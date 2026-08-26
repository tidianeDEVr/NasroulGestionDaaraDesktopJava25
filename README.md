# 🕌 Nasroul Mouminina - Système de Gestion d'Association

<div align="center">

![JavaFX](https://img.shields.io/badge/JavaFX-21.0.1-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Maven](https://img.shields.io/badge/Maven-3.x-red.svg)
![License](https://img.shields.io/badge/License-Proprietary-yellow.svg)

**Application de bureau moderne pour la gestion complète d'une Daara (association islamique)**

[Fonctionnalités](#-fonctionnalités) • [Installation](#-installation) • [Utilisation](#-utilisation) • [Architecture](#-architecture) • [Documentation](#-documentation)

</div>

---

## 📋 Table des matières

- [Vue d'ensemble](#-vue-densemble)
- [Fonctionnalités](#-fonctionnalités)
- [Technologies](#-technologies)
- [Prérequis](#-prérequis)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Utilisation](#-utilisation)
- [Architecture](#-architecture)
- [Système de synchronisation](#-système-de-synchronisation)
- [Structure du projet](#-structure-du-projet)
- [Développement](#-développement)
- [Contributions](#-contributions)
- [Licence](#-licence)

---

## 🎯 Vue d'ensemble

**Nasroul Mouminina** est une application de bureau complète développée en JavaFX pour la gestion d'associations islamiques (Daara). Elle offre une solution moderne et intuitive pour gérer les membres, les événements, les contributions financières, les projets et bien plus encore.

### 🌟 Points forts

- 🎨 **Interface moderne** : Interface utilisateur élégante et intuitive avec JavaFX
- 📴 **Mode Offline-First** : Fonctionne sans connexion internet avec SQLite
- 🔄 **Synchronisation intelligente** : Sync bidirectionnelle avec MySQL pour le partage multi-appareils
- 📊 **Tableaux de bord riches** : Statistiques et visualisations en temps réel
- 📤 **Export Excel** : Exportation des données vers Excel avec Apache POI
- 💬 **Campagnes SMS** : Envoi de SMS groupés aux membres
- 🖼️ **Gestion d'images** : Support des photos pour les membres
- 🔍 **Résolution de conflits** : Détection et résolution automatique/manuelle des conflits de synchronisation

---

## ✨ Fonctionnalités

### 👥 Gestion des Membres
- Ajout, modification et suppression de membres
- Informations détaillées : nom, prénom, téléphone, email, adresse, photo
- Attribution aux groupes
- Historique des contributions par membre
- Export Excel de la liste des membres

### 📅 Gestion des Événements
- Création et planification d'événements
- Suivi des participants
- Gestion des dates et descriptions
- Historique complet des événements

### 💰 Gestion Financière
- **Contributions** : Suivi des cotisations des membres
- **Dépenses** : Enregistrement et catégorisation des dépenses
- **Groupes de paiement** : Organisation des paiements collectifs
- **Projets** : Gestion budgétaire des projets avec objectifs financiers

### 📊 Tableau de Bord
- Vue d'ensemble des statistiques clés
- Nombre total de membres, groupes, événements
- Résumé financier : contributions totales, dépenses, solde
- Graphiques et indicateurs visuels

### 🔄 Synchronisation Multi-Appareils
- Mode offline-first avec SQLite comme base primaire
- Synchronisation bidirectionnelle avec MySQL (PULL/PUSH)
- Détection automatique des conflits avec résolution intelligente
- Historique détaillé de synchronisation
- Support multi-appareils avec identification unique

### 💬 Campagnes SMS
- Envoi de SMS groupés aux membres
- Sélection des destinataires par groupe ou individuellement
- Interface intuitive de composition de messages

### 📤 Export de Données
- Export Excel des membres, contributions, dépenses
- Format personnalisable
- Compatible avec Microsoft Excel et LibreOffice

---

## 🛠️ Technologies

### Langages et Frameworks
- **Java 17** - Langage de programmation
- **JavaFX 21.0.1** - Framework d'interface utilisateur moderne
- **Maven** - Gestion des dépendances et build

### Bases de Données
- **SQLite 3.44.1** - Base de données locale (mode offline)
- **MySQL 8.3.0** - Base de données centrale (synchronisation)

### Bibliothèques Principales
- **Apache POI 5.2.5** - Export Excel
- **Unirest Java 3.14.5** - Requêtes HTTP pour la synchronisation
- **Commons Codec 1.16.0** - Hashing SHA-256 pour la détection de conflits

### Architecture
- **MVC (Model-View-Controller)** - Pattern architectural
- **DAO (Data Access Object)** - Couche d'accès aux données
- **Service Layer** - Logique métier

---

## 📦 Prérequis

### Logiciels requis

```bash
# Java Development Kit 17+
java -version  # Doit afficher Java 17 ou supérieur

# Apache Maven 3.6+
mvn -version

# MySQL Server (optionnel, uniquement pour la synchronisation)
mysql --version
```

### Systèmes d'exploitation supportés
- ✅ Windows 10/11
- ✅ macOS (Intel et Apple Silicon)
- ✅ Linux (Ubuntu, Debian, Fedora, etc.)

---

## 🚀 Installation

### 1. Cloner le dépôt

```bash
git clone https://github.com/tidianeDEVr/NasroulGestionDaaraDesktopJava25.git
cd NasroulGestionDaaraDesktopJava25
```

### 2. Configuration de la base de données

L'application fonctionne en mode **offline-first**, aucune configuration n'est nécessaire pour commencer. SQLite sera automatiquement initialisé au premier lancement.

Pour activer la synchronisation avec MySQL (optionnel) :

```bash
# Copier le fichier de configuration
cp src/main/resources/config.properties.example src/main/resources/config.properties

# Éditer le fichier avec vos paramètres MySQL
nano src/main/resources/config.properties
```

**Contenu du fichier `config.properties`** :

```properties
# Type de base de données (sqlite ou mysql)
db.type=mysql

# Configuration MySQL (pour la synchronisation)
mysql.host=localhost
mysql.port=3306
mysql.database=nasroul_db
mysql.user=root
mysql.password=your_password

# Configuration SQLite (par défaut)
sqlite.path=nasroul.db
```

### 3. Compiler le projet

#### Windows
```bash
mvn clean package -P windows
```

#### macOS (Intel)
```bash
mvn clean package -P mac
```

#### macOS (Apple Silicon)
```bash
mvn clean package -P mac
# Puis modifier pom.xml: <javafx.platform>mac-aarch64</javafx.platform>
```

#### Linux
```bash
mvn clean package -P linux
```

### 4. Lancer l'application

```bash
mvn javafx:run
```

Ou exécuter le JAR généré :

```bash
java -jar target/AssociationManager-2.0.0.jar
```

---

## ⚙️ Configuration

### Configuration de base

Le fichier `config.properties` dans `src/main/resources/` permet de configurer :

```properties
# Mode de fonctionnement
db.type=sqlite                    # sqlite (offline) ou mysql (avec sync)

# MySQL (synchronisation)
mysql.host=localhost
mysql.port=3306
mysql.database=nasroul_db
mysql.user=root
mysql.password=

# SQLite (base locale)
sqlite.path=nasroul.db

# Paramètres de synchronisation
sync.enabled=true                 # Activer/désactiver la sync
sync.conflict.strategy=LAST_WRITE_WINS  # Stratégie de résolution de conflits
```

### Stratégies de résolution de conflits

- `LAST_WRITE_WINS` ⭐ (recommandé) : Le dernier modifié gagne
- `LOCAL_WINS` : Les modifications locales sont prioritaires
- `REMOTE_WINS` : Les modifications distantes sont prioritaires
- `MANUAL` : Résolution manuelle via interface graphique
- `HIGHER_VERSION_WINS` : La version la plus élevée gagne

---

## 📖 Utilisation

### Premier lancement

1. **Écran de démarrage** : Un splash screen s'affiche pendant le chargement
2. **Initialisation automatique** : La base de données SQLite est créée automatiquement
3. **Interface principale** : Vous accédez au tableau de bord

### Navigation

L'application est organisée en onglets :

- 📊 **Tableau de bord** : Vue d'ensemble et statistiques
- 👥 **Membres** : Gestion des membres
- 📅 **Groupes** : Organisation en groupes
- 🎉 **Événements** : Planification d'événements
- 💰 **Contributions** : Suivi des cotisations
- 📉 **Dépenses** : Gestion des dépenses
- 🎯 **Projets** : Gestion de projets
- 💳 **Groupes de paiement** : Paiements collectifs
- 🔄 **Historique Sync** : Journal de synchronisation

### Synchronisation

1. Cliquer sur le bouton **"🔄 Synchroniser"** dans la barre d'outils
2. L'application effectue :
   - **PULL** : Récupération des données du serveur
   - **PUSH** : Envoi des modifications locales
3. Les conflits sont détectés et résolus automatiquement ou manuellement selon la configuration

### Export Excel

1. Naviguer vers la section souhaitée (Membres, Contributions, etc.)
2. Cliquer sur **"📤 Exporter vers Excel"**
3. Choisir l'emplacement du fichier
4. Le fichier Excel est généré avec toutes les données

---

## 🏗️ Architecture

### Vue d'ensemble

```
┌─────────────────────────────────────────────────────┐
│                  Présentation Layer                 │
│  (JavaFX FXML Views + Controllers)                  │
└─────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────┐
│                   Service Layer                     │
│  (Business Logic: MemberService, EventService...)   │
└─────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────┐
│                    DAO Layer                        │
│  (Data Access: MemberDAO, EventDAO...)              │
└─────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────┬──────────────────────────────┐
│   SQLite (Primary)   │    MySQL (Sync Server)       │
│   Offline-First      │    Multi-device Sharing      │
└──────────────────────┴──────────────────────────────┘
```

### Couches de l'application

#### 1. **Presentation Layer** (Vue + Contrôleurs)
- Fichiers FXML pour la définition des interfaces
- Contrôleurs JavaFX pour la logique de présentation
- Gestion des événements utilisateur

#### 2. **Service Layer** (Logique Métier)
- Services métier : `MemberService`, `EventService`, `ContributionService`, etc.
- Validation des données
- Orchestration des opérations complexes

#### 3. **DAO Layer** (Accès aux Données)
- Pattern DAO pour chaque entité
- Abstraction de la source de données (SQLite/MySQL)
- Requêtes SQL optimisées

#### 4. **Model Layer** (Entités)
- POJOs représentant les entités métier
- Implémentation de `SyncableEntity` pour la synchronisation

### Composants Principaux

#### Synchronisation
```
SyncManager
    ↓
┌─────────────┬───────────────┬──────────────┐
│ SyncService │ ConflictDetec │ ConflictRes  │
│             │ tor           │ olver        │
└─────────────┴───────────────┴──────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ SyncMetadataDAO + SyncLogDAO                │
└─────────────────────────────────────────────┘
```

---

## 🔄 Système de synchronisation

### Architecture Offline-First

L'application utilise une architecture **offline-first** où :

1. **SQLite est la base primaire** : Toutes les opérations CRUD se font sur SQLite
2. **MySQL est optionnel** : Utilisé uniquement pour la synchronisation multi-appareils
3. **L'app fonctionne 100% offline** : Aucune dépendance à une connexion réseau

### Flux de synchronisation

#### PULL (Téléchargement)
```
1. Récupérer les enregistrements modifiés sur MySQL depuis le dernier sync
2. Comparer avec les données locales SQLite
3. Détecter les conflits (three-way merge)
4. Résoudre les conflits selon la stratégie configurée
5. Mettre à jour SQLite avec les données réconciliées
```

#### PUSH (Envoi)
```
1. Identifier les enregistrements modifiés localement depuis le dernier sync
2. Envoyer vers MySQL avec métadonnées (version, timestamp, device_id)
3. Mettre à jour les statuts de synchronisation locaux
```

### Détection de conflits

Utilise un algorithme **three-way merge** :

- **Base** : Dernière version synchronisée (hash stocké dans `sync_metadata`)
- **Local** : Version actuelle dans SQLite
- **Remote** : Version actuelle dans MySQL

Un conflit survient quand :
- Base ≠ Local ET Base ≠ Remote ET Local ≠ Remote
- C'est-à-dire : les deux côtés ont modifié depuis la dernière sync

### Documentation détaillée

Pour plus de détails sur le système de synchronisation, consultez :
- [SYNC_SYSTEM_README.md](./SYNC_SYSTEM_README.md) - Documentation complète
- [SYNC_IMPLEMENTATION_PLAN.md](./SYNC_IMPLEMENTATION_PLAN.md) - Plan d'implémentation

---

## 📁 Structure du projet

```
NasroulGestionDaaraDesktopJava25/
│
├── src/
│   ├── main/
│   │   ├── java/com/nasroul/
│   │   │   ├── AssociationApp.java          # Point d'entrée de l'application
│   │   │   │
│   │   │   ├── controller/                  # Contrôleurs JavaFX
│   │   │   │   ├── MainController.java
│   │   │   │   ├── DashboardController.java
│   │   │   │   ├── MemberController.java
│   │   │   │   ├── EventController.java
│   │   │   │   ├── ContributionController.java
│   │   │   │   ├── ExpenseController.java
│   │   │   │   ├── ProjectController.java
│   │   │   │   ├── SyncHistoryController.java
│   │   │   │   └── ...
│   │   │   │
│   │   │   ├── model/                       # Modèles de données
│   │   │   │   ├── Member.java
│   │   │   │   ├── Event.java
│   │   │   │   ├── Group.java
│   │   │   │   ├── Contribution.java
│   │   │   │   ├── Expense.java
│   │   │   │   ├── Project.java
│   │   │   │   ├── PaymentGroup.java
│   │   │   │   └── SyncableEntity.java      # Interface pour la sync
│   │   │   │
│   │   │   ├── dao/                         # Data Access Objects
│   │   │   │   ├── DatabaseManager.java     # Gestionnaire de connexions
│   │   │   │   ├── MemberDAO.java
│   │   │   │   ├── EventDAO.java
│   │   │   │   ├── GroupDAO.java
│   │   │   │   ├── SyncMetadataDAO.java
│   │   │   │   ├── SyncLogDAO.java
│   │   │   │   └── ...
│   │   │   │
│   │   │   ├── service/                     # Services métier
│   │   │   │   ├── MemberService.java
│   │   │   │   ├── EventService.java
│   │   │   │   ├── ContributionService.java
│   │   │   │   ├── SyncService.java
│   │   │   │   ├── SMSService.java
│   │   │   │   └── ...
│   │   │   │
│   │   │   ├── sync/                        # Système de synchronisation
│   │   │   │   ├── SyncManager.java         # Orchestrateur principal
│   │   │   │   ├── ConflictDetector.java    # Détection de conflits
│   │   │   │   ├── ConflictResolver.java    # Résolution de conflits
│   │   │   │   ├── DataHashCalculator.java  # Calcul SHA-256
│   │   │   │   └── GenericSyncableEntity.java
│   │   │   │
│   │   │   ├── util/                        # Utilitaires
│   │   │   │   ├── ConfigManager.java       # Gestion de la config
│   │   │   │   ├── ExcelUtil.java           # Export Excel
│   │   │   │   ├── ImageUtil.java           # Gestion d'images
│   │   │   │   └── DeviceIdGenerator.java   # ID d'appareil unique
│   │   │   │
│   │   │   └── view/                        # Vues personnalisées
│   │   │       └── ConflictResolutionDialog.java
│   │   │
│   │   └── resources/
│   │       ├── fxml/                        # Fichiers FXML
│   │       │   ├── MainView.fxml
│   │       │   ├── DashboardView.fxml
│   │       │   ├── MemberView.fxml
│   │       │   ├── SyncHistoryView.fxml
│   │       │   └── ...
│   │       │
│   │       ├── css/                         # Feuilles de style
│   │       │   └── styles.css
│   │       │
│   │       ├── images/                      # Images et icônes
│   │       │
│   │       └── config.properties            # Configuration
│   │
│   └── test/                                # Tests unitaires (à venir)
│
├── target/                                  # Fichiers compilés (généré)
│
├── pom.xml                                  # Configuration Maven
├── README.md                                # Ce fichier
├── SYNC_SYSTEM_README.md                    # Doc du système de sync
├── SYNC_IMPLEMENTATION_PLAN.md              # Plan d'implémentation
└── .gitignore
```

---

## 👨‍💻 Développement

### Lancer en mode développement

```bash
# Compilation et exécution en une commande
mvn clean javafx:run

# Ou en deux étapes
mvn clean compile
mvn javafx:run
```

### Compilation avec profiles

```bash
# Pour Windows
mvn clean package -P windows

# Pour macOS
mvn clean package -P mac

# Pour Linux
mvn clean package -P linux
```

### Debugging

Pour déboguer avec un IDE :

**IntelliJ IDEA** :
1. Ouvrir le projet
2. Configurer le SDK Java 17
3. Run → Edit Configurations → Add New → Maven
4. Command line : `javafx:run`

**Eclipse** :
1. Importer comme projet Maven existant
2. Configurer Java 17
3. Run As → Maven Build → Goals : `javafx:run`

### Structure de développement recommandée

1. **Modèle (Model)** : Créer l'entité dans `com.nasroul.model`
2. **DAO** : Créer le DAO dans `com.nasroul.dao`
3. **Service** : Créer le service dans `com.nasroul.service`
4. **Vue FXML** : Créer le fichier FXML dans `resources/fxml`
5. **Contrôleur** : Créer le contrôleur dans `com.nasroul.controller`

### Conventions de code

- **Nommage** : CamelCase pour les classes, camelCase pour les méthodes/variables
- **Packages** : Un package par couche (model, dao, service, controller)
- **Commentaires** : Javadoc pour les classes et méthodes publiques
- **Indentation** : 4 espaces (pas de tabulations)

---

## 🤝 Contributions

Les contributions sont les bienvenues ! Pour contribuer :

1. **Fork** le projet
2. Créer une **branche feature** : `git checkout -b feature/AmazingFeature`
3. **Commit** vos changements : `git commit -m 'Add some AmazingFeature'`
4. **Push** vers la branche : `git push origin feature/AmazingFeature`
5. Ouvrir une **Pull Request**

### Guidelines

- Respecter les conventions de code
- Ajouter des tests pour les nouvelles fonctionnalités
- Mettre à jour la documentation si nécessaire
- Décrire clairement les changements dans la PR

---

## 🐛 Signalement de bugs

Pour signaler un bug, veuillez ouvrir une **issue** sur GitHub avec :

- Description claire du problème
- Étapes pour reproduire
- Comportement attendu vs comportement actuel
- Captures d'écran si applicable
- Environnement (OS, version Java, etc.)

---

## 📝 Changelog

### Version 1.0-SNAPSHOT (En cours)

#### Fonctionnalités
- ✅ Gestion complète des membres
- ✅ Gestion des événements et groupes
- ✅ Système de contributions et dépenses
- ✅ Gestion de projets et groupes de paiement
- ✅ Tableau de bord avec statistiques
- ✅ Export Excel
- ✅ Campagnes SMS
- ✅ Système de synchronisation offline-first
- ✅ Détection et résolution de conflits
- ✅ Interface utilisateur moderne

#### Corrections récentes
- 🐛 Fix import `java.lang.String` dans `SyncHistoryView.fxml`
- 🐛 Amélioration des messages d'erreur pour utilisateurs finaux
- 🐛 Corrections diverses de l'interface

---

## 📄 Licence

Ce projet est sous licence propriétaire. Tous droits réservés.

© 2025 Nasroul Mouminina

---

## 👤 Auteur

**Tidiane DEVr**
- GitHub: [@tidianeDEVr](https://github.com/tidianeDEVr)

---

## 🙏 Remerciements

- Communauté JavaFX pour l'excellent framework
- Apache Foundation pour les bibliothèques POI
- Tous les contributeurs du projet

---

## 📞 Support

Pour toute question ou assistance :

- 📧 Email : cheikhtiindiaye@gmail.com
- 🐛 Issues : [GitHub Issues](https://github.com/tidianeDEVr/NasroulGestionDaaraDesktopJava25/issues)
- 📖 Wiki : [GitHub Wiki](https://github.com/tidianeDEVr/NasroulGestionDaaraDesktopJava25/wiki)

---

<div align="center">

**Fait avec ❤️ par la communauté Mouride**

⭐ N'oubliez pas de mettre une étoile si ce projet vous plaît !

</div>
