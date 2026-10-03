# Passerelle de synchronisation (api.php)

Permet aux postes de synchroniser **sans connexion MySQL directe** : plus
besoin d'autoriser l'adresse IP de chaque poste dans cPanel, et les identifiants
MySQL ne quittent jamais le serveur.

## Installation (une fois, sur l'hébergement)

1. Copier `api.config.example.php` en `api.config.php`, renseigner la clé API
   (longue et aléatoire, ex. `openssl rand -hex 32`) et les identifiants MySQL
   (`db_host` est en général `localhost` vu depuis le serveur web).
2. Déposer `api.php` et `api.config.php` dans un dossier du site, par exemple
   `public_html/nasroul-sync/`. Le dossier doit être servi en **HTTPS**.
3. Vérifier : `curl -X POST -H "X-Api-Key: <clé>" -d '{"action":"ping"}' https://votre-domaine/nasroul-sync/api.php`
   doit répondre `{"ok":true,...}`.

## Sur chaque poste (config.properties)

```
sync.api.url=https://votre-domaine/nasroul-sync/api.php
sync.api.key=<la même clé que dans api.config.php>
```

Dès que `sync.api.url` est renseignée, l'application n'utilise plus les
paramètres `db.mysql.*`. Les tables sont créées/migrées par la passerelle
(`ensure_schema`) à la première synchronisation.

## Protocole (pour information)

POST JSON `{"action": ...}` avec l'en-tête `X-Api-Key`. Actions : `ping`,
`ensure_schema`, `register_device`, `columns`, `fetch_all`, `get`, `insert`,
`update`, `set_member_groups`, `log_batch`. Les BLOB sont encodés
`{"__blob": "<base64>"}`. Tables et colonnes sont validées côté serveur,
toutes les valeurs sont liées en requêtes préparées.
