# Plan d'Implémentation - Synchronisation Robuste avec Mapping ID

## Problèmes Critiques Identifiés

### Cas 1: Soft Delete Non Propagé
- Manager A supprime un record → `deleted_at` set
- Manager B ne voit PAS la suppression lors du PULL
- **Solution**: Propager `deleted_at` lors du PULL

### Cas 2: Conflit d'ID Auto-Increment
- Manager B crée record → SQLite génère ID=5
- Manager C crée record → SQLite génère ID=5
- MySQL génère ID=47 pour B, ID=48 pour C
- **Problème**: Aucun mapping entre ID local (5) et ID remote (47/48)
- **Impact**: Foreign keys cassées (member.group_id=5 invalide)

## Solution: Colonne remote_id dans sync_metadata

### Étape 1: ✅ Migration Base de Données
- [x] Ajouter `remote_id INTEGER` à sync_metadata (SQLite)
- [x] Ajouter `remote_id INT` à sync_metadata (MySQL)
- [x] Créer méthodes de migration automatique

### Étape 2: ✅ TERMINÉ - Modifier SyncMetadataDAO
- [x] Ajouter méthodes pour sauvegarder/récupérer remote_id
- [x] Méthode `setRemoteId(tableName, recordId, remoteId)`
- [x] Méthode `getRemoteId(tableName, recordId)`
- [x] Méthode `getLocalIdByRemoteId(tableName, remoteId)`

### Étape 3: ✅ TERMINÉ - Modifier SyncManager.insertRemoteEntity()
- [x] Capturer l'ID généré par MySQL après INSERT
- [x] Sauvegarder mapping: local_id → remote_id
- [x] Conversion automatique des FK avant INSERT

### Étape 4: ✅ TERMINÉ - Modifier SyncManager.updateRemoteEntity()
- [x] Utiliser remote_id au lieu de record_id pour UPDATE MySQL
- [x] Requête: `UPDATE table SET ... WHERE id = ?` avec remote_id
- [x] Conversion automatique des FK avant UPDATE

### Étape 5: ✅ TERMINÉ - Modifier SyncManager.pullTableFromRemote()
- [x] Propager soft deletes (deleted_at IS NOT NULL)
- [x] Mapper remote_id → local_id lors de l'update
- [x] Créer nouveau record si aucun mapping trouvé
- [x] Conversion automatique des FK après PULL

### Étape 6: ✅ TERMINÉ - Gestion Foreign Keys
- [x] Identifier toutes les FK: group_id, member_id, entity_id, paid_by, project_id, organizer_id
- [x] PUSH: Convertir FK local → FK remote avant INSERT/UPDATE (méthode `convertForeignKeysForPush`)
- [x] PULL: Convertir FK remote → FK local après SELECT (méthode `convertForeignKeysForPull`)
- [x] Support FK polymorphique (contributions.entity_id basé sur entity_type)
- [x] Logging détaillé des conversions FK
- [x] Gestion des FK manquantes (set NULL si mapping introuvable)

### Étape 7: ⏳ À TESTER - Tests de Validation
- [ ] Test soft delete: A supprime → B sync → record disparaît chez B
- [ ] Test conflit ID: B crée ID=5 → C crée ID=5 → sync → pas de conflit
- [ ] Test FK: B crée membre avec group_id=5 → sync → MySQL a FK correcte
- [ ] Test FK polymorphique: Contribution vers Event → sync → mapping correct
- [ ] Test multi-device: 3 devices créent des records → sync → toutes les relations préservées

## Fichiers Modifiés
1. ✅ DatabaseManager.java - Migration remote_id (SQLite + MySQL)
2. ✅ SyncMetadataDAO.java - Méthodes remote_id complètes
3. ✅ SyncManager.java - PULL/PUSH avec mapping ID + conversion FK
4. ✅ SYNC_IMPLEMENTATION_PLAN.md - Documentation complète
