# Cahier des charges - Version mobile Nasroul Mouminina

## 1. Contexte et objectifs
L application desktop actuelle couvre la gestion complete d une association (Daara). L objectif est de proposer une version mobile iOS/Android, basee sur Flutter et Firebase, qui reprend les fonctionnalites existantes, avec une interface claire, intuitive et tres simple a utiliser. L application sera payante sur Google Play et App Store, et les utilisateurs pourront acheter des points pour l envoi de SMS.

## 2. Perimetre
### 2.1 Inclus (MVP)
- Reprise des fonctionnalites core: membres, groupes, evenements, contributions, depenses, projets, groupes de paiement, dashboard, campagnes SMS.
- Synchronisation multi appareils et mode hors ligne.
- Personnalisation de l identite visuelle (logo + couleurs principales).
- Monetisation: app payante + points SMS via achats integres.

### 2.2 Hors perimetre (ou phase 2, a confirmer)
- Import automatique des donnees depuis la version desktop.
- Gestion multi langues avancee.
- Integrations comptables externes.

## 3. Utilisateurs et roles (a valider)
- Administrateur: acces complet, parametres, gestion des points SMS et personnalisation.
- Gestionnaire: CRUD sur les entites, campagnes SMS.
- Lecteur: consultation uniquement.

## 4. Fonctionnalites detaillees

### 4.1 Tableau de bord
- Statistiques globales: total membres, groupes, evenements, projets.
- Resume financier: contributions, depenses, solde.
- Graphiques simples (par periode) et indicateurs de suivi.

### 4.2 Gestion des membres
- CRUD membre avec champs: prenom, nom, telephone, email, adresse, date de naissance, date d adhesion, role, statut actif.
- Photo/avatar (upload).
- Attribution multi groupes.
- Historique des contributions par membre.

### 4.3 Gestion des groupes
- CRUD groupe: nom, description, statut actif.
- Liaison membres <-> groupes.

### 4.4 Gestion des evenements
- CRUD: nom, description, lieu, dates debut/fin, statut, organisateur, capacite max.
- Objectif de contribution par evenement.
- Liste des participants (via contributions/paiements).

### 4.5 Gestion des projets
- CRUD: nom, description, dates, statut, budget, budget cible, responsable.
- Objectif de contribution par projet.

### 4.6 Contributions
- Saisie des contributions liees a un membre et a un evenement/projet.
- Montant, date, statut (PENDING/PAID/OVERDUE), methode de paiement, notes.
- Filtres par membre, projet, evenement, periode.

### 4.7 Depenses
- Enregistrement des depenses: description, montant, date, categorie.
- Lien eventuel a un projet/evenement ou membre.

### 4.8 Groupes de paiement
- Association d un groupe a un projet/evenement.
- Montant par membre.
- Suivi de l avancement des paiements du groupe.

### 4.9 Campagnes SMS
- Envoi de SMS groupes par evenement, projet ou groupe de membres.
- Modele de message avec variables: {prenom}, {nom}, {montant_restant}, {montant_total}, {entite}.
- Affichage du solde de points SMS.
- Historique des campagnes (date, destinataires, resultat).

### 4.10 Export / partage
- Export partageable (CSV ou PDF) pour membres, contributions, depenses.
- Partage via applications du telephone.

### 4.11 Synchronisation et mode hors ligne
- Donnees accessibles hors ligne (cache local).
- Sync automatique et manuelle, resolution de conflits simple (last write wins).
- Journal de synchronisation (historique des operations).

### 4.12 Parametrage et personnalisation
- Logo de l association (upload).
- Couleurs principales (primaire/secondaire/accent).
- Preview instantanee des changements.

## 5. Monetisation
### 5.1 Application payante
- Tarif fixe configure dans Google Play et App Store.
- Gestion des licences via stores.

### 5.2 Points SMS (achats integres)
- Packs de points (ex: 100, 500, 1000) a definir.
- Debit automatique lors de l envoi (1 SMS = 1 point, a confirmer).
- Validation serveur des achats (anti fraude).
- Solde et historique des achats visibles dans l app.

## 6. Exigences UX/UI
- Interface claire, contraste suffisant, textes lisibles.
- Parcours courts pour les actions frequentes (ajout membre, contribution, envoi SMS).
- Formulaires avec validation immediate et messages simples.
- Navigation mobile standard (tabs + ecrans detail).

## 7. Donnees et modele fonctionnel (resume)
- Membre: prenom, nom, email, telephone, adresse, date naissance, date adhesion, role, actif, groupes, avatar.
- Groupe: nom, description, actif.
- Evenement: nom, description, dates, lieu, statut, organisateur, capacite, objectif contribution.
- Projet: nom, description, dates, statut, budget, budget cible, responsable, objectif contribution.
- Contribution: membre, entite (evenement/projet), montant, date, statut, methode paiement, notes.
- Depense: description, montant, date, categorie, entite/membre.
- GroupePaiement: groupe, entite, montant par membre.
- CampagneSMS: entite, message, destinataires, cout points, statut, date.
- ParametresApp: logo, couleurs, infos association.

## 8. Architecture technique (Flutter + Firebase)
### 8.1 Services Firebase
- Auth (si comptes requis): authentification email/telephone.
- Firestore: stockage des donnees principales.
- Storage: stockage des photos et logo.
- Cloud Functions: validation achats integres, debit de points, envoi SMS via fournisseur.
- Crashlytics/Analytics: suivi stabilite et usage (optionnel).

### 8.2 Local cache
- Persistence offline Firestore ou stockage local (Hive/SQLite).
- Strategie de reprise et gestion des conflits basique.

### 8.3 Integration SMS
- Envoi SMS via fournisseur (existant LAM SMS ou equivalent).
- Appel depuis Cloud Functions pour securiser les credentials.

## 9. Securite et conformite
- Regles Firestore strictes par role/organisation.
- Validation cote serveur des achats integres et du solde points.
- Chiffrement en transit (HTTPS) et protection des secrets.
- Sauvegardes automatiques des donnees Firebase.

## 10. Tests et qualite
- Tests unitaires pour logique metier.
- Tests d integration (Firebase emulators).
- Tests E2E sur parcours critiques (ajout membre, contribution, achat points, envoi SMS).

## 11. Livrables
- Application Flutter (code source).
- Configuration Firebase (projet, regles, fonctions).
- Assets stores (icones, screenshots).
- Documentation d exploitation (admin, SMS, personnalisation).

## 12. Points a clarifier
- Roles exacts et droits par profil.
- Langues supportees et format des devises.
- Pays cibles et regles SMS (prefixes, operateurs).
- Fournisseur SMS definitif et cout par SMS.
- Besoin d import/migration depuis la base actuelle.
