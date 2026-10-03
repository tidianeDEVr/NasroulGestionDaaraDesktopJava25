<?php
/**
 * Copier ce fichier en api.config.php à côté de api.php, et le compléter.
 * NE JAMAIS publier api.config.php dans le dépôt (il contient des secrets).
 *
 * La clé API doit être identique à sync.api.key dans le config.properties de
 * chaque poste. Générez-la par exemple avec : openssl rand -hex 32
 */
return [
    'api_key'   => 'REMPLACEZ-MOI-PAR-UNE-CLE-LONGUE-ET-ALEATOIRE',
    'db_driver' => 'mysql',
    'db_host'   => 'localhost',   // vu depuis le serveur web : presque toujours localhost
    'db_port'   => '3306',
    'db_name'   => 'xassaidc_nasroul',
    'db_user'   => 'xassaidc_nasroul_user',
    'db_pass'   => 'mot-de-passe-mysql',
];
