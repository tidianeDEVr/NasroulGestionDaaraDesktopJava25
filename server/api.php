<?php
/**
 * Nasroul Gestion Daara — passerelle de synchronisation.
 *
 * Déposer ce fichier ET api.config.php (clé + identifiants MySQL) dans un
 * dossier de l'hébergement, par exemple public_html/nasroul-sync/, puis
 * renseigner sur chaque poste :
 *   sync.api.url=https://votre-domaine/nasroul-sync/api.php
 *   sync.api.key=<la même clé que dans api.config.php>
 *
 * Protocole : POST JSON {"action": ..., ...}, en-tête X-Api-Key.
 * Réponse : {"ok": true, ...} ou {"ok": false, "error": "..."} (HTTP 4xx/5xx).
 * Les BLOB voyagent en base64 : {"__blob": "..."}.
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

const SYNC_TABLES = ['groups', 'members', 'events', 'projects', 'expenses', 'contributions', 'payment_groups'];
const ALL_TABLES  = ['groups', 'members', 'events', 'projects', 'expenses', 'contributions', 'payment_groups',
                     'member_groups', 'sync_log', 'sync_devices', 'sync_metadata'];
const MAX_BODY    = 64 * 1024 * 1024;

$config = [
    'api_key'   => '',
    'db_driver' => 'mysql',   // mysql | sqlite (sqlite : tests uniquement)
    'db_host'   => 'localhost',
    'db_port'   => '3306',
    'db_name'   => '',
    'db_user'   => '',
    'db_pass'   => '',
    'db_path'   => '',        // sqlite uniquement
];
$configFile = __DIR__ . '/api.config.php';
if (is_file($configFile)) {
    $loaded = require $configFile;
    if (is_array($loaded)) {
        $config = array_merge($config, $loaded);
    }
}
// Variables d'environnement (tests, conteneurs) : NASROUL_API_KEY, NASROUL_DB_DRIVER, ...
foreach (['API_KEY' => 'api_key', 'DB_DRIVER' => 'db_driver', 'DB_HOST' => 'db_host', 'DB_PORT' => 'db_port',
          'DB_NAME' => 'db_name', 'DB_USER' => 'db_user', 'DB_PASS' => 'db_pass', 'DB_PATH' => 'db_path'] as $env => $key) {
    $value = getenv('NASROUL_' . $env);
    if ($value !== false && $value !== '') {
        $config[$key] = $value;
    }
}

function fail(int $status, string $message): never
{
    http_response_code($status);
    echo json_encode(['ok' => false, 'error' => $message], JSON_UNESCAPED_UNICODE);
    exit;
}

function ok(array $data = []): never
{
    echo json_encode(['ok' => true] + $data, JSON_UNESCAPED_UNICODE | JSON_PRESERVE_ZERO_FRACTION);
    exit;
}

// ------------------------------------------------------------ authentification
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    fail(405, 'Méthode non autorisée : POST attendu');
}
if (strlen((string) $config['api_key']) < 16) {
    fail(500, 'Clé API non configurée côté serveur (api.config.php)');
}
$provided = $_SERVER['HTTP_X_API_KEY'] ?? '';
if (!is_string($provided) || !hash_equals((string) $config['api_key'], $provided)) {
    fail(401, 'Clé API invalide');
}

$raw = file_get_contents('php://input', false, null, 0, MAX_BODY);
$req = json_decode((string) $raw, true);
if (!is_array($req) || !isset($req['action']) || !is_string($req['action'])) {
    fail(400, 'Requête JSON invalide');
}

// ------------------------------------------------------------ base de données
$driver = strtolower((string) $config['db_driver']) === 'sqlite' ? 'sqlite' : 'mysql';
try {
    if ($driver === 'sqlite') {
        $pdo = new PDO('sqlite:' . $config['db_path']);
        $pdo->exec('PRAGMA busy_timeout = 10000');
    } else {
        $dsn = sprintf('mysql:host=%s;port=%s;dbname=%s;charset=utf8mb4',
            $config['db_host'], $config['db_port'], $config['db_name']);
        $pdo = new PDO($dsn, (string) $config['db_user'], (string) $config['db_pass'], [
            PDO::ATTR_EMULATE_PREPARES => false,
            PDO::MYSQL_ATTR_USE_BUFFERED_QUERY => true,
        ]);
        $pdo->exec("SET time_zone = '+00:00'");
    }
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
    if (defined('PDO::ATTR_STRINGIFY_FETCHES')) {
        $pdo->setAttribute(PDO::ATTR_STRINGIFY_FETCHES, false);
    }
} catch (Throwable $e) {
    fail(500, 'Connexion à la base impossible : ' . $e->getMessage());
}

function q(string $identifier): string
{
    return '`' . str_replace('`', '', $identifier) . '`';
}

function table_or_fail(array $req, array $allowed = SYNC_TABLES): string
{
    $table = $req['table'] ?? null;
    if (!is_string($table) || !in_array($table, $allowed, true)) {
        fail(400, 'Table non autorisée');
    }
    return $table;
}

/** Colonnes d'une table : nom (minuscule) → type déclaré (minuscule). */
function columns(PDO $pdo, string $driver, string $table): array
{
    static $cache = [];
    if (isset($cache[$table])) {
        return $cache[$table];
    }
    $cols = [];
    if ($driver === 'sqlite') {
        foreach ($pdo->query('PRAGMA table_info(' . q($table) . ')') as $row) {
            $cols[strtolower((string) $row['name'])] = strtolower((string) $row['type']);
        }
    } else {
        foreach ($pdo->query('SHOW COLUMNS FROM ' . q($table)) as $row) {
            $cols[strtolower((string) $row['Field'])] = strtolower((string) $row['Type']);
        }
    }
    return $cache[$table] = $cols;
}

function is_blob_type(string $type): bool
{
    return str_contains($type, 'blob') || str_contains($type, 'binary');
}

/** Ligne PDO → JSON (BLOB en base64). */
function encode_row(array $row, array $cols): array
{
    $out = [];
    foreach ($row as $name => $value) {
        $name = strtolower((string) $name);
        if ($value !== null && is_blob_type($cols[$name] ?? '')) {
            $out[$name] = ['__blob' => base64_encode(is_resource($value) ? (string) stream_get_contents($value) : (string) $value)];
        } else {
            $out[$name] = $value;
        }
    }
    return $out;
}

/** Champs JSON → valeurs à lier, validés contre les colonnes réelles (sans id). */
function decode_fields(mixed $fields, array $cols): array
{
    if (!is_array($fields)) {
        fail(400, 'Champs invalides');
    }
    $out = [];
    foreach ($fields as $name => $value) {
        $name = strtolower((string) $name);
        if ($name === 'id' || !isset($cols[$name])) {
            if ($name === 'id') {
                continue;
            }
            fail(400, "Colonne inconnue : $name");
        }
        if (is_array($value)) {
            if (array_key_exists('__blob', $value)) {
                $value = $value['__blob'] === null ? null : base64_decode((string) $value['__blob'], true);
                if ($value === false) {
                    fail(400, "BLOB invalide pour $name");
                }
                $out[$name] = ['blob', $value];
                continue;
            }
            fail(400, "Valeur invalide pour $name");
        }
        $out[$name] = ['value', $value];
    }
    return $out;
}

function bind_all(PDOStatement $stmt, array $decoded, int &$index): void
{
    foreach ($decoded as [$kind, $value]) {
        if ($value === null) {
            $stmt->bindValue($index++, null, PDO::PARAM_NULL);
        } elseif ($kind === 'blob') {
            $stmt->bindValue($index++, $value, PDO::PARAM_LOB);
        } elseif (is_bool($value)) {
            $stmt->bindValue($index++, $value ? 1 : 0, PDO::PARAM_INT);
        } elseif (is_int($value)) {
            $stmt->bindValue($index++, $value, PDO::PARAM_INT);
        } else {
            $stmt->bindValue($index++, is_float($value) ? (string) $value : (string) $value, PDO::PARAM_STR);
        }
    }
}

function member_groups_for(PDO $pdo, ?int $memberId = null): array
{
    $sql = 'SELECT member_id, group_id FROM member_groups' . ($memberId !== null ? ' WHERE member_id = ?' : '')
         . ' ORDER BY member_id, group_id';
    $stmt = $pdo->prepare($sql);
    $stmt->execute($memberId !== null ? [$memberId] : []);
    $out = [];
    foreach ($stmt as $row) {
        $out[(string) (int) $row['member_id']][] = (int) $row['group_id'];
    }
    return $out;
}

/** Schéma MySQL : miroir de DatabaseManager.createTablesMySQL / createSyncTablesMySQL / migrations. */
function ensure_schema_mysql(PDO $pdo): void
{
    $ddl = [
        "CREATE TABLE IF NOT EXISTS `groups` (`id` INT PRIMARY KEY AUTO_INCREMENT, `name` VARCHAR(255) NOT NULL UNIQUE,
            `description` TEXT, `active` INT DEFAULT 1, `contribution_target` DOUBLE DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS `members` (`id` INT PRIMARY KEY AUTO_INCREMENT, `first_name` VARCHAR(255) NOT NULL,
            `last_name` VARCHAR(255) NOT NULL, `email` VARCHAR(255) UNIQUE, `phone` VARCHAR(255), `birth_date` VARCHAR(255),
            `address` TEXT, `join_date` VARCHAR(255) NOT NULL, `role` VARCHAR(255), `avatar` LONGBLOB, `active` INT DEFAULT 1,
            `group_id` INT, FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`))",
        "CREATE TABLE IF NOT EXISTS `events` (`id` INT PRIMARY KEY AUTO_INCREMENT, `name` VARCHAR(255) NOT NULL,
            `description` TEXT, `start_date` VARCHAR(255) NOT NULL, `end_date` VARCHAR(255), `location` VARCHAR(255),
            `status` VARCHAR(255) DEFAULT 'PLANNED', `organizer_id` INT, `max_capacity` INT, `active` INT DEFAULT 1,
            `contribution_target` DOUBLE DEFAULT 0, FOREIGN KEY (`organizer_id`) REFERENCES `members`(`id`))",
        "CREATE TABLE IF NOT EXISTS `projects` (`id` INT PRIMARY KEY AUTO_INCREMENT, `name` VARCHAR(255) NOT NULL,
            `description` TEXT, `start_date` VARCHAR(255), `end_date` VARCHAR(255), `status` VARCHAR(255) DEFAULT 'PLANNING',
            `budget` DOUBLE DEFAULT 0, `manager_id` INT, `contribution_target` DOUBLE DEFAULT 0,
            FOREIGN KEY (`manager_id`) REFERENCES `members`(`id`))",
        "CREATE TABLE IF NOT EXISTS `expenses` (`id` INT PRIMARY KEY AUTO_INCREMENT, `description` VARCHAR(255) NOT NULL,
            `amount` DOUBLE NOT NULL, `date` VARCHAR(255) NOT NULL, `category` VARCHAR(255), `entity_type` VARCHAR(255) NOT NULL,
            `entity_id` INT NOT NULL, `member_id` INT, FOREIGN KEY (`member_id`) REFERENCES `members`(`id`))",
        "CREATE TABLE IF NOT EXISTS `contributions` (`id` INT PRIMARY KEY AUTO_INCREMENT, `member_id` INT NOT NULL,
            `entity_type` VARCHAR(255) NOT NULL, `entity_id` INT NOT NULL, `amount` DOUBLE NOT NULL, `date` VARCHAR(255) NOT NULL,
            `status` VARCHAR(255) DEFAULT 'PENDING', `payment_method` VARCHAR(255), `notes` TEXT, `group_id` INT,
            FOREIGN KEY (`member_id`) REFERENCES `members`(`id`))",
        "CREATE TABLE IF NOT EXISTS `payment_groups` (`id` INT PRIMARY KEY AUTO_INCREMENT, `group_id` INT NOT NULL,
            `entity_type` VARCHAR(255) NOT NULL, `entity_id` INT NOT NULL, `amount` DOUBLE NOT NULL,
            FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`))",
        "CREATE TABLE IF NOT EXISTS `member_groups` (`member_id` INT NOT NULL, `group_id` INT NOT NULL,
            PRIMARY KEY (`member_id`, `group_id`), FOREIGN KEY (`member_id`) REFERENCES `members`(`id`) ON DELETE CASCADE,
            FOREIGN KEY (`group_id`) REFERENCES `groups`(`id`) ON DELETE CASCADE)",
        "CREATE TABLE IF NOT EXISTS `sync_metadata` (`table_name` VARCHAR(255) NOT NULL, `record_id` INT NOT NULL, `remote_id` INT,
            `sync_version` INT DEFAULT 1, `local_hash` VARCHAR(255), `remote_hash` VARCHAR(255), `last_sync_at` DATETIME,
            `sync_status` VARCHAR(50) DEFAULT 'PENDING', `conflict_resolution` TEXT, PRIMARY KEY (`table_name`, `record_id`))",
        "CREATE TABLE IF NOT EXISTS `sync_log` (`id` INT PRIMARY KEY AUTO_INCREMENT, `sync_session_id` VARCHAR(255) NOT NULL,
            `table_name` VARCHAR(255) NOT NULL, `record_id` INT NOT NULL, `operation` VARCHAR(50) NOT NULL,
            `sync_direction` VARCHAR(50) NOT NULL, `status` VARCHAR(50) NOT NULL, `error_message` TEXT, `synced_at` DATETIME NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `sync_devices` (`device_id` VARCHAR(255) PRIMARY KEY, `device_name` VARCHAR(255) NOT NULL,
            `user_name` VARCHAR(255), `last_sync_at` DATETIME, `is_active` INT DEFAULT 1)",
    ];
    foreach ($ddl as $sql) {
        $pdo->exec($sql);
    }
    $syncColumns = ['created_at' => 'DATETIME', 'updated_at' => 'DATETIME', 'deleted_at' => 'DATETIME',
        'last_modified_by' => 'VARCHAR(255)', 'sync_status' => "VARCHAR(50) DEFAULT 'PENDING'",
        'sync_version' => 'INT DEFAULT 1', 'last_sync_at' => 'DATETIME'];
    $extra = [['sync_metadata', 'remote_id', 'INT'], ['contributions', 'group_id', 'INT'],
              ['projects', 'contribution_target', 'DOUBLE DEFAULT 0']];
    foreach (SYNC_TABLES as $table) {
        foreach ($syncColumns as $col => $type) {
            $extra[] = [$table, $col, $type];
        }
    }
    foreach ($extra as [$table, $col, $type]) {
        try {
            $pdo->exec('ALTER TABLE ' . q($table) . ' ADD COLUMN ' . q($col) . ' ' . $type);
        } catch (PDOException $e) {
            // colonne déjà présente
        }
    }
}

// ------------------------------------------------------------ actions
try {
    switch ($req['action']) {
        case 'ping':
            $version = $driver === 'sqlite'
                ? 'SQLite ' . $pdo->query('SELECT sqlite_version()')->fetchColumn()
                : (string) $pdo->query('SELECT VERSION()')->fetchColumn();
            ok(['driver' => $driver, 'version' => $version, 'database' => (string) ($config['db_name'] ?: $config['db_path'])]);

        case 'ensure_schema':
            if ($driver === 'mysql') {
                ensure_schema_mysql($pdo);
            }
            ok();

        case 'register_device':
            $deviceId = (string) ($req['device_id'] ?? '');
            if ($deviceId === '') {
                fail(400, 'device_id manquant');
            }
            $now = gmdate('Y-m-d H:i:s');
            $sql = $driver === 'sqlite'
                ? 'INSERT INTO sync_devices (device_id, device_name, user_name, last_sync_at, is_active) VALUES (?, ?, ?, ?, 1)
                   ON CONFLICT(device_id) DO UPDATE SET device_name = excluded.device_name, user_name = excluded.user_name,
                   last_sync_at = excluded.last_sync_at, is_active = 1'
                : 'INSERT INTO sync_devices (device_id, device_name, user_name, last_sync_at, is_active) VALUES (?, ?, ?, ?, 1)
                   ON DUPLICATE KEY UPDATE device_name = VALUES(device_name), user_name = VALUES(user_name),
                   last_sync_at = VALUES(last_sync_at), is_active = 1';
            $pdo->prepare($sql)->execute([$deviceId, (string) ($req['device_name'] ?? ''), (string) ($req['user_name'] ?? ''), $now]);
            ok();

        case 'columns':
            $table = table_or_fail($req, ALL_TABLES);
            $list = [];
            foreach (columns($pdo, $driver, $table) as $name => $type) {
                $list[] = ['name' => $name, 'type' => $type];
            }
            ok(['columns' => $list]);

        case 'fetch_all':
            $table = table_or_fail($req);
            $cols = columns($pdo, $driver, $table);
            $rows = [];
            foreach ($pdo->query('SELECT * FROM ' . q($table) . ' ORDER BY id') as $row) {
                $rows[] = encode_row($row, $cols);
            }
            $data = ['rows' => $rows];
            if ($table === 'members') {
                $data['member_groups'] = (object) member_groups_for($pdo);
            }
            ok($data);

        case 'get':
            $table = table_or_fail($req);
            $id = (int) ($req['id'] ?? 0);
            $cols = columns($pdo, $driver, $table);
            $stmt = $pdo->prepare('SELECT * FROM ' . q($table) . ' WHERE id = ?');
            $stmt->execute([$id]);
            $row = $stmt->fetch();
            $data = ['row' => $row ? encode_row($row, $cols) : null];
            if ($row && $table === 'members') {
                $data['group_ids'] = member_groups_for($pdo, $id)[(string) $id] ?? [];
            }
            ok($data);

        case 'insert':
            $table = table_or_fail($req);
            $cols = columns($pdo, $driver, $table);
            $decoded = decode_fields($req['fields'] ?? null, $cols);
            if ($decoded === []) {
                fail(400, 'Aucun champ à insérer');
            }
            $names = array_map('q', array_keys($decoded));
            $sql = 'INSERT INTO ' . q($table) . ' (' . implode(', ', $names) . ') VALUES ('
                 . implode(', ', array_fill(0, count($decoded), '?')) . ')';
            $stmt = $pdo->prepare($sql);
            $i = 1;
            bind_all($stmt, $decoded, $i);
            $stmt->execute();
            ok(['id' => (int) $pdo->lastInsertId()]);

        case 'update':
            $table = table_or_fail($req);
            $id = (int) ($req['id'] ?? 0);
            $cols = columns($pdo, $driver, $table);
            $decoded = decode_fields($req['fields'] ?? null, $cols);
            if ($decoded === []) {
                ok(['affected' => 0]);
            }
            $sets = [];
            foreach (array_keys($decoded) as $name) {
                $sets[] = q($name) . ' = ?';
            }
            $stmt = $pdo->prepare('UPDATE ' . q($table) . ' SET ' . implode(', ', $sets) . ' WHERE id = ?');
            $i = 1;
            bind_all($stmt, $decoded, $i);
            $stmt->bindValue($i, $id, PDO::PARAM_INT);
            $stmt->execute();
            ok(['affected' => $stmt->rowCount()]);

        case 'set_member_groups':
            $memberId = (int) ($req['member_id'] ?? 0);
            $groupIds = $req['group_ids'] ?? [];
            if ($memberId <= 0 || !is_array($groupIds)) {
                fail(400, 'member_id / group_ids invalides');
            }
            $pdo->beginTransaction();
            $pdo->prepare('DELETE FROM member_groups WHERE member_id = ?')->execute([$memberId]);
            $ins = $pdo->prepare('INSERT INTO member_groups (member_id, group_id) VALUES (?, ?)');
            foreach (array_unique(array_map('intval', $groupIds)) as $gid) {
                $ins->execute([$memberId, $gid]);
            }
            $pdo->commit();
            ok();

        case 'log_batch':
            $entries = $req['entries'] ?? [];
            if (!is_array($entries)) {
                fail(400, 'entries invalide');
            }
            $stmt = $pdo->prepare('INSERT INTO sync_log (sync_session_id, table_name, record_id, operation, sync_direction,
                status, error_message, synced_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
            $pdo->beginTransaction();
            $n = 0;
            foreach ($entries as $e) {
                if (!is_array($e)) {
                    continue;
                }
                $message = $e['error_message'] ?? null;
                $stmt->execute([
                    (string) ($e['sync_session_id'] ?? ''), (string) ($e['table_name'] ?? ''), (int) ($e['record_id'] ?? 0),
                    (string) ($e['operation'] ?? ''), (string) ($e['sync_direction'] ?? ''), (string) ($e['status'] ?? ''),
                    $message === null ? null : mb_substr((string) $message, 0, 2000),
                    (string) ($e['synced_at'] ?? gmdate('Y-m-d H:i:s')),
                ]);
                $n++;
            }
            $pdo->commit();
            ok(['inserted' => $n]);

        default:
            fail(400, 'Action inconnue : ' . $req['action']);
    }
} catch (PDOException $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    fail(500, 'Erreur base de données : ' . $e->getMessage());
} catch (Throwable $e) {
    fail(500, 'Erreur interne : ' . $e->getMessage());
}
