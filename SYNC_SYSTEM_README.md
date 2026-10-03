# 🔄 Système de Synchronisation Offline-First

## Vue d'ensemble

Le système de synchronisation permet à l'application de fonctionner en **mode offline** avec SQLite comme base de données primaire, tout en synchronisant les données avec un serveur MySQL central pour le partage entre plusieurs appareils.

## 🎯 Caractéristiques Principales

### ✅ Architecture Offline-First
- **SQLite** comme base de données **primaire** (locale, rapide, toujours disponible)
- **MySQL** comme base de données de **synchronisation** (centrale, partagée)
- Fonctionne **100% offline** sans connexion MySQL
- Synchronisation en **arrière-plan** sans bloquer l'interface

### ✅ Synchronisation Bidirectionnelle
- **PULL**: Télécharge les changements depuis MySQL vers SQLite
- **PUSH**: Envoie les changements locaux vers MySQL
- Détection automatique des modifications
- Soft delete pour propagation correcte des suppressions

### ✅ Détection et Résolution de Conflits
- **Détection automatique** avec algorithme three-way merge
- Utilise timestamps, versions et hash SHA-256
- **5 stratégies de résolution**:
  - `LAST_WRITE_WINS` (par défaut, recommandé)
  - `LOCAL_WINS`
  - `REMOTE_WINS`
  - `MANUAL` (résolution manuelle via dialogue)
  - `HIGHER_VERSION_WINS`

### ✅ Suivi Multi-Appareils
- Enregistrement automatique des appareils (ID unique basé sur hostname + MAC)
- Tracking du dernier sync par appareil
- Métadonnées complètes: qui, quand, quelle version

### ✅ Interface Utilisateur
- **Bouton Sync** dans MainController avec statut en temps réel
- Indicateurs visuels: 🔄 Syncing, ✅ Success, ❌ Failed, 📴 Offline
- Dialogue de résolution de conflits (comparaison côte à côte)
- Statistiques de synchronisation détaillées

## 📋 Architecture Technique

### Phase 1: Infrastructure Database
```
┌─────────────────────────────────────────────────────────┐
│ Tables Existantes (modifiées)                          │
├─────────────────────────────────────────────────────────┤
│ • groups, members, events, projects                    │
│ • expenses, contributions, payment_groups              │
│                                                         │
│ Colonnes ajoutées:                                     │
│ • created_at, updated_at, deleted_at                   │
│ • last_modified_by, sync_status, sync_version         │
│ • last_sync_at                                         │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ Nouvelles Tables de Sync                               │
├─────────────────────────────────────────────────────────┤
│ • sync_metadata: État de sync par enregistrement      │
│ • sync_log: Journal d'audit des opérations            │
│ • sync_devices: Registre des appareils                │
└─────────────────────────────────────────────────────────┘
```

### Phase 2: Logique Core
```
┌─────────────────────────────────────────────────────────┐
│ Classes de Sync                                        │
├─────────────────────────────────────────────────────────┤
│ • SyncableEntity: Classe de base pour tous les models │
│ • DataHashCalculator: Calcul SHA-256 pour détection   │
│ • ConflictDetector: Three-way merge                   │
│ • ConflictResolver: Résolution automatique            │
│ • SyncManager: Orchestrateur PULL/PUSH               │
│ • SyncMetadataDAO, SyncLogDAO: Accès données         │
└─────────────────────────────────────────────────────────┘
```

### Phase 3: Services & UI
```
┌─────────────────────────────────────────────────────────┐
│ Services                                               │
├─────────────────────────────────────────────────────────┤
│ • SyncService: API haut niveau (async)                │
│ • DeviceRegistrationService: Gestion appareils        │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ Interface Utilisateur                                  │
├─────────────────────────────────────────────────────────┤
│ • MainController: Bouton Sync + statut                │
│ • ConflictResolutionDialog: Résolution manuelle       │
└─────────────────────────────────────────────────────────┘
```

## 🚀 Utilisation

### Configuration Initiale

1. **Copier le fichier de configuration**:
   ```bash
   cp config.properties.example config.properties
   ```

2. **Configurer MySQL** (optionnel pour offline):
   ```properties
   db.type=sqlite
   db.mysql.host=your-mysql-server.com
   db.mysql.database=nasroul
   db.mysql.username=your_username
   db.mysql.password=your_password
   ```

3. **Lancer l'application**:
   - L'appareil s'enregistre automatiquement
   - SQLite est créé si non existant
   - Fonctionne immédiatement en mode offline

### Synchronisation

#### Manuelle (Recommandé)
1. Cliquer sur le **bouton Sync** dans l'interface
2. Attendre la fin de la synchronisation
3. Voir le résultat: records téléchargés/envoyés, conflits

#### Automatique (Optionnel)
```properties
sync.auto.enabled=true
sync.auto.interval=30  # Minutes
sync.on.startup=true
```

### Gestion des Conflits

#### Automatique (Par défaut)
```properties
sync.conflict.strategy=LAST_WRITE_WINS
```
La version la plus récente gagne automatiquement.

#### Manuelle
```properties
sync.conflict.strategy=MANUAL
```
Un dialogue s'affiche pour choisir: Local, Remote, ou Skip.

## 🌐 Deux transports vers le serveur

- **Passerelle HTTP `server/api.php` (recommandé)** : les postes n'ont que
  `sync.api.url` et `sync.api.key` ; les identifiants MySQL restent sur
  l'hébergement et aucune autorisation d'adresse IP n'est nécessaire.
  Installation : voir `server/README.md`.
- **MySQL direct** (`db.mysql.*`) : utilisé seulement si `sync.api.url` est
  vide. Chaque poste doit alors être autorisé dans cPanel « MySQL distant ».

Le moteur (`SyncManager`) est identique dans les deux cas : il parle à un
`RemoteStore` (`HttpRemoteStore` ou `JdbcRemoteStore`). Le test
`ApiSyncTest` exécute la vraie passerelle PHP sur une base SQLite jetable.

## 🔧 Mécanique de synchronisation (révision octobre 2026)

Le moteur (`sync/SyncManager`) a été réécrit pour corriger des doublons et des
conflits mal arbitrés. Règles en vigueur :

- **Mapping d'identifiants** : les ids auto-incrémentés diffèrent d'un poste à
  l'autre. Chaque ligne locale est reliée à sa ligne serveur par
  `sync_metadata.remote_id` (upsert, jamais perdu). Toutes les clés étrangères
  sont converties dans les deux sens (`members.group_id`, `events.organizer_id`,
  `projects.manager_id`, `contributions.member_id/group_id/entity_id`,
  `expenses.member_id/entity_id`, `payment_groups.group_id/entity_id`). Une FK
  dont la cible n'a pas encore d'id distant déclenche l'envoi immédiat de la
  cible (récursivement) ; une cible inexistante fait échouer **cette ligne
  seulement**, qui reste PENDING, est comptée dans le résultat et repart à la
  sync suivante.
- **Auto-réparation** : avant le PUSH, une ligne marquée SYNCED sans id distant
  (ancien moteur, mapping perdu) repasse en PENDING et est envoyée.
- **Hash de contenu** : calculé sur les colonnes métier **communes aux deux
  schémas** (sans `id` ni métadonnées de sync ; une colonne héritée d'un seul
  côté comme `projects.target_budget` est ignorée), dans l'espace d'ids local,
  avec un rendu canonique des valeurs (5000 = 5000.0, BLOB par contenu, dates
  normalisées).
- **Fusion à trois voies** : version locale, version serveur et hash de la
  dernière sync (`sync_metadata.local_hash`). Un seul côté a changé → il gagne
  sans conflit. Les deux ont changé → conflit arbitré par la stratégie
  `sync.conflict.strategy` (défaut LAST_WRITE_WINS sur `updated_at`, départage
  déterministe en cas d'égalité : version puis hash). Suppression vs
  modification : la plus récente gagne.
- **Pas de doublon** : une ligne serveur sans mapping est d'abord rapprochée des
  lignes locales sans mapping par contenu identique, puis par clé naturelle
  (groupe/projet : nom ; membre : email, sinon prénom+nom+téléphone ;
  événement : nom+date ; objectif : groupe+entité). Une ligne reliée aux
  contenus différents passe par la fusion à trois voies.
- **Suppressions logiques** : une ligne supprimée (`deleted_at`) circule comme
  les autres, dans les deux sens, même si elle n'avait jamais été synchronisée :
  d'autres lignes peuvent la référencer (cotisations d'un membre parti,
  organisateur d'un événement). Elle reste invisible dans l'application.
- **État local** : `sync_status` et `last_sync_at` ne sont jamais recopiés
  d'une base à l'autre (une ligne tirée arrive SYNCED).
- **member_groups** : les appartenances voyagent avec la ligne membre (incluses
  dans son hash, converties en ids) ; les effectifs par groupe convergent.
- **Transactions** : une connexion locale et une distante par session ; chaque
  ligne (données + mapping + métadonnées + journal) est validée séparément.
- **Stratégie MANUAL** : la ligne reste en `CONFLICT` (rien n'est écrasé) et est
  réévaluée à chaque sync ; `SyncManager.resolveConflictManually(table, id,
  TAKE_LOCAL|TAKE_REMOTE)` tranche.

### Tests

`mvn test` exécute `com.nasroul.sync.*Test` : deux postes et un serveur
simulés par des bases SQLite jetables (aucun MySQL requis), couvrant PUSH,
PULL, aller-retour multi-postes, conflits (LWW, suppression/modification,
MANUAL), liaison sans doublon, collisions d'objectifs, valeurs legacy.

### Avant déploiement

- Le format de hash a changé : à la **première** sync après mise à jour, chaque
  ligne déjà présente des deux côtés est réévaluée (LWW si elle diffère).
- Les doublons créés sur le serveur par l'ancien moteur ne sont pas fusionnés
  automatiquement entre eux : nettoyer la base MySQL (ou repartir d'une base
  serveur vide alimentée par un seul poste) avant de resynchroniser les autres.

## 📊 Flux de Synchronisation

```
┌──────────────┐
│  CLIC SYNC   │
└──────┬───────┘
       │
       ▼
┌─────────────────────┐
│ 1. CHECK MYSQL      │ ─── Si indisponible ───> Mode Offline
└──────┬──────────────┘
       │ Disponible
       ▼
┌─────────────────────┐
│ 2. PULL (MySQL→SQLite) │
│ • Récupérer changements MySQL     │
│ • Détecter conflits                │
│ • Résoudre automatiquement         │
│ • Mettre à jour SQLite            │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────┐
│ 3. PUSH (SQLite→MySQL) │
│ • Trouver PENDING records          │
│ • Vérifier conflits               │
│ • Envoyer à MySQL                 │
│ • Marquer SYNCED                  │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────┐
│ 4. RÉSULTAT         │
│ • Records pulled    │
│ • Records pushed    │
│ • Conflits          │
│ • Erreurs           │
└─────────────────────┘
```

## 🔧 API Pour Développeurs

### Déclencher une Synchronisation
```java
SyncService syncService = SyncService.getInstance();

// Asynchrone (recommandé)
Task<SyncManager.SyncResult> task = syncService.synchronizeAsync();
task.setOnSucceeded(event -> {
    SyncManager.SyncResult result = task.getValue();
    System.out.println("Pulled: " + result.getRecordsPulled());
    System.out.println("Pushed: " + result.getRecordsPushed());
});

// Synchrone (bloquant)
SyncManager.SyncResult result = syncService.synchronize();
```

### Écouter les Changements de Statut
```java
syncService.setStatusListener(status -> {
    switch (status) {
        case SYNCING:
            // Afficher spinner
            break;
        case SUCCESS:
            // Afficher succès
            break;
        case FAILED:
            // Afficher erreur
            break;
    }
});
```

### Obtenir les Informations de l'Appareil
```java
DeviceRegistrationService deviceService = DeviceRegistrationService.getInstance();
String deviceId = deviceService.getCurrentDeviceId();
DeviceInfo info = deviceService.getCurrentDeviceInfo();
```

## 📝 Modèle DAO pour Support Sync

Pour que les autres DAOs supportent la synchronisation comme GroupDAO:

```java
public void create(Entity entity) throws SQLException {
    String sql = """
        INSERT INTO table_name (field1, field2,
            created_at, updated_at, last_modified_by, sync_status, sync_version)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
    
    LocalDateTime now = LocalDateTime.now();
    String deviceId = DeviceIdGenerator.getDeviceId();
    
    // Set parameters + sync metadata
    pstmt.setObject(3, now); // created_at
    pstmt.setObject(4, now); // updated_at
    pstmt.setString(5, deviceId); // last_modified_by
    pstmt.setString(6, "PENDING"); // sync_status
    pstmt.setInt(7, 1); // sync_version
}

public void update(Entity entity) throws SQLException {
    String sql = """
        UPDATE table_name
        SET field1 = ?, field2 = ?,
            updated_at = ?, last_modified_by = ?, 
            sync_status = ?, sync_version = sync_version + 1
        WHERE id = ?
        """;
    // Marquer comme PENDING pour sync
}

public void delete(int id) throws SQLException {
    // SOFT DELETE au lieu de suppression physique
    String sql = """
        UPDATE table_name
        SET deleted_at = ?, updated_at = ?, 
            last_modified_by = ?, sync_status = ?
        WHERE id = ?
        """;
}
```

## ⚠️ Points Importants

1. **Ne jamais utiliser MySQL directement** - Toujours passer par SQLite
2. **Soft delete obligatoire** - Pour propagation correcte
3. **Toujours incrémenter sync_version** lors de modifications
4. **Marquer PENDING** après chaque changement
5. **Exclure deleted_at IS NULL** dans les SELECT

## 🔜 Tâches Restantes

- [ ] Modifier MemberDAO avec support sync
- [ ] Modifier EventDAO avec support sync
- [ ] Modifier ProjectDAO avec support sync
- [ ] Modifier ExpenseDAO avec support sync
- [ ] Modifier ContributionDAO avec support sync
- [ ] Modifier PaymentGroupDAO avec support sync

Modèle: Voir `GroupDAO.java` (complètement implémenté)

## 📞 Support

Pour toute question ou problème, consulter:
- Code source dans `src/main/java/com/nasroul/sync/`
- Services dans `src/main/java/com/nasroul/service/`
- Configuration dans `config.properties.example`

---

**Système développé avec ❤️ pour l'architecture offline-first**
