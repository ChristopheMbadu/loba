# Publier Loba sur Google Play et l'App Store

Loba est une **PWA** (application web installable). On l'emballe pour les stores sans réécrire le code.

## ⚠️ Règle importante : les dons
Google Play et Apple **interdisent aux particuliers** de mettre des liens de dons externes (PayPal, Orange Money) dans une application. La version store est donc lancée avec `?app=1` : la page de dons est **masquée** automatiquement (mode `STORE_BUILD`). Les dons restent disponibles sur le site web.

## 1. Google Play (recommandé en premier)
**Coût : 25 $ une seule fois.**

1. Crée un compte développeur : https://play.google.com/console (pièce d'identité + vérification ; un compte personnel doit faire tester l'app par **12 testeurs pendant 14 jours** avant la mise en production).
2. Va sur https://www.pwabuilder.com, entre `https://christophembadu.github.io/loba/`, puis **Package for stores → Android**.
   - Package ID : `io.github.christophembadu.loba`
   - Start URL : `/loba/?app=1`
   - Télécharge le zip : il contient le fichier `.aab`, la clé de signature (**à garder précieusement**) et `assetlinks.json`.
3. **Lien de confiance (Digital Asset Links)** : `assetlinks.json` doit être accessible à `https://christophembadu.github.io/.well-known/assetlinks.json`. Il faut donc créer un dépôt GitHub nommé **`ChristopheMbadu.github.io`** et y mettre ce fichier dans `.well-known/` (plus un fichier `.nojekyll`). Sans ça, une barre d'adresse s'affiche en haut de l'app.
4. Dans la Play Console : crée l'application, téléverse le `.aab`, remplis la fiche :
   - Politique de confidentialité : `https://christophembadu.github.io/loba/privacy.html`
   - Catégorie : Éducation ; public : 13 ans et plus ; pas de publicité.
   - Sécurité des données : identifiant utilisateur anonyme + e-mail facultatif, chiffrés en transit, suppression sur demande.
   - Captures d'écran (au moins 2) + icône 512 px (`icons/icon-512.png`).

### Micro (exercices oraux)
L'application demande le micro seulement quand on appuie sur 🎤. Dans la fiche Play Console, déclare l'accès au micro (« Audio : enregistrement vocal, non collecté, non partagé ») et laisse la mention « Micro » de la politique de confidentialité.

## 2. App Store (iPhone)
**Coût : 99 $ par an. Il faut un Mac (ou un service de build cloud).**

1. Compte : https://developer.apple.com/programs
2. PWABuilder → **iOS** génère un projet Xcode ; ou bien utiliser **Capacitor** (`npx cap add ios`).
3. ⚠️ Apple refuse souvent les apps qui ne sont « qu'un site web » (règle 4.2). Pour maximiser les chances : rappels quotidiens par notifications, fonctionnement hors ligne, et une vraie expérience d'app. À tenter **après** Google Play.

## 3. Sans store, dès aujourd'hui
Sur Android comme sur iPhone, ouvrir le site puis **« Ajouter à l'écran d'accueil »** installe déjà Loba comme une vraie application, gratuitement.
