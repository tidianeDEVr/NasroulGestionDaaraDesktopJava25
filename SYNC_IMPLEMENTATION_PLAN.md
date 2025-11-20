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

### Étape 2: 🔄 EN COURS - Modifier SyncMetadataDAO
- [ ] Ajouter méthodes pour sauvegarder/récupérer remote_id
- [ ] Méthode `setRemoteId(tableName, recordId, remoteId)`
- [ ] Méthode `getRemoteId(tableName, recordId)`
- [ ] Méthode `getLocalIdByRemoteId(tableName, remoteId)`

### Étape 3: Modifier SyncManager.insertRemoteEntity()
- [ ] Capturer l'ID généré par MySQL après INSERT
- [ ] Sauvegarder mapping: local_id → remote_id
- [ ] Code:
```java
pstmt.executeUpdate();
ResultSet rs = pstmt.getGeneratedKeys();
if (rs.next()) {
    int remoteId = rs.getInt(1);
    syncMetadataDAO.setRemoteId(tableName, localId, remoteId);
}
```

### Étape 4: Modifier SyncManager.updateRemoteEntity()
- [ ] Utiliser remote_id au lieu de record_id pour UPDATE MySQL
- [ ] Requête: `UPDATE table SET ... WHERE id = ?` avec remote_id

### Étape 5: Modifier SyncManager.pullTableFromRemote()
- [ ] Propager soft deletes (deleted_at IS NOT NULL)
- [ ] Mapper remote_id → local_id lors de l'update
- [ ] Créer nouveau record si aucun mapping trouvé

### Étape 6: Gestion Foreign Keys
- [ ] Identifier toutes les FK: group_id, member_id, entity_id, etc.
- [ ] PUSH: Convertir FK local → FK remote avant INSERT
- [ ] PULL: Convertir FK remote → FK local après SELECT

### Étape 7: Tests
- [ ] Test soft delete: A supprime → B sync → record disparaît chez B
- [ ] Test conflit ID: B crée ID=5 → C crée ID=5 → sync → pas de conflit
- [ ] Test FK: B crée membre avec group_id=5 → sync → MySQL a FK correcte

## Fichiers à Modifier
1. ✅ DatabaseManager.java - Migration remote_id
2. 🔄 SyncMetadataDAO.java - Méthodes remote_id
3. SyncManager.java - PULL/PUSH avec mapping
4. Potentiellement: DAOs spécifiques pour FK complexes
