# 🇨🇩 Loba — Apprends le lingala et le swahili

## Loba Studio — tableau de bord éditorial

Un tableau de bord avec connexion, gestion des leçons, brouillons et publication est disponible dans ce dépôt. Backend Node.js/PostgreSQL, déploiement Railway via Docker et API de contenu pour une future application SwiftUI. Voir **[STUDIO.md](STUDIO.md)** pour l'installation, le déploiement et les limites actuelles. L'application historique ci-dessous conserve ses leçons intégrées et son backend Supabase.

**Loba** (« parle ! » en lingala) est une application façon Duolingo pour apprendre le **lingala** de Kinshasa et le **swahili**, pensée pour la diaspora congolaise : celles et ceux qui comprennent la langue de la famille… mais n'osent pas la parler.

👉 **Démo : https://christophembadu.github.io/loba/** (installable sur téléphone : « Ajouter à l'écran d'accueil »)

<p align="center"><img src="screenshot.png" width="320" alt="Capture d'écran de Loba"></p>

## Fonctionnalités

- **Deux langues à apprendre** : lingala et swahili, avec test de placement.
- **Interface en français ou en anglais** (🇫🇷 / 🇬🇧), pour les francophones comme pour les anglophones (Kenya, Tanzanie, diaspora anglo-saxonne).
- **Exercices variés** : QCM, paires, phrases à reconstruire, situations réelles façon WhatsApp (maman, marché, aéroport de N'djili…).
- **Apprentissage vocal** : écoute la prononciation de chaque mot (🔊, 🐢 au ralenti), exercices « Qu'as-tu entendu ? » et exercices où tu parles au micro, avec vérification automatique de ta prononciation.
- **Machine à phrases** : pronom + verbe + temps + négation, pour comprendre la mécanique de la langue.
- **Gamification** : série 🔥, XP, cœurs ❤️, gemmes 💎, quêtes, Défi éclair, badges.
- **Profil personnalisé** : prénom ou pseudo, avatar, couleur, objectif quotidien.
- **Comptes** : e-mail, numéro de téléphone ou Apple, pour retrouver sa progression partout.
- **12 unités par langue** et des **histoires** avec personnages récurrents (à écouter, avec questions de compréhension).
- **Ligues hebdomadaires** (Cuivre → Diamant) et **amis** avec code ami et défis.
- **Série protégée** : gel de série, réparation, étapes 7/30/100 jours, bilan de la semaine.
- **Boutique** (gel de série, double XP, recharge de cœurs, avatars spéciaux) et **entraînement** par révision espacée.
- **Rappel quotidien** : une notification à l'heure choisie, seulement si on n'a pas encore pratiqué.
- **Sauvegarde automatique** : la progression est enregistrée en continu, sur l'appareil et dans le cloud (compte anonyme). On peut lier un e-mail pour la retrouver sur un autre téléphone.
- **100 % gratuit, sans publicité, sans traqueur.** Le projet vit grâce aux **dons** (PayPal pour la diaspora, Orange Money pour le Congo).
- **Application installable (PWA)** et fonctionne hors connexion.

## Stack

- Un seul `index.html` (HTML, CSS, JavaScript vanilla) généré par `build/build.py`.
- `sw.js` + `manifest.webmanifest` : application installable et hors ligne.
- **Supabase** (région UE) : authentification anonyme + table `progress` protégée par RLS (`supabase/migrations`).
- Sons générés avec la Web Audio API.

Voir [SECURITY.md](SECURITY.md) pour la revue de sécurité et [STORES.md](STORES.md) pour la publication sur Google Play et l'App Store. Politique de confidentialité : [privacy.html](privacy.html).

## Feuille de route

- [ ] Audio enregistré par des locuteurs kinois
- [ ] Publication sur Google Play puis l'App Store
- [ ] Classement entre amis
- [ ] Nouvelles unités : transport, santé, musique (rumba, ndombolo)

## Contribuer

Tu parles lingala ou swahili ? Les corrections et propositions de phrases sont les bienvenues : ouvre une *issue* ou une *pull request*.

---

Fait à Kinshasa par [Christophe Mbadu](https://www.linkedin.com/in/christophe-mbadu-b61179289/).
