# 🇨🇩 Loba — Apprends le lingala de Kinshasa

**Loba** (« parle ! » en lingala) est une application web façon Duolingo pour apprendre le lingala, pensée pour la diaspora congolaise : celles et ceux qui comprennent la langue de la famille… mais n'osent pas la parler.

👉 **Essaie-la directement dans ton navigateur : https://christophembadu.github.io/loba/**

## Pourquoi Loba ?

Des millions de jeunes Congolais à Paris, Bruxelles ou Montréal entendent le lingala à la maison sans jamais le parler. Les applis de langues classiques ne le proposent pas, ou l'enseignent comme une langue de manuel. Loba part de situations réelles.

## Fonctionnalités

- **Onboarding et test de placement** : selon ta motivation (famille, belle-famille, voyage à Kin…) et ton niveau, Loba débloque directement les unités que tu maîtrises.
- **6 unités** : salutations, kit de survie (« Loba malembe » = parle doucement), famille, nourriture, nombres, verbes.
- **Exercices variés** : QCM dans les deux sens, paires à associer, phrases à reconstruire.
- **Situations réelles** façon WhatsApp : appel avec maman, marché (zando), arrivée à N'djili, rencontre avec la belle-famille.
- **Message du jour** à envoyer à sa famille sur WhatsApp, en un clic.
- **Machine à phrases** : on assemble pronom + verbe + temps + négation et on voit la mécanique du lingala (*na* + *zali* + *kolia* = « Nazali kolia »).
- **Révision intelligente** : les mots ratés reviennent au bon moment.
- **Gamification** : série de jours 🔥, XP, cœurs ❤️, gemmes 💎, coffres, quêtes du jour, Défi éclair de 60 secondes, badges.

## Stack

Un seul fichier `index.html` : HTML, CSS et JavaScript vanilla, sans dépendance ni build. La progression est sauvegardée dans le `localStorage` du navigateur. Les sons sont générés avec la Web Audio API.

```bash
git clone https://github.com/ChristopheMbadu/loba.git
cd loba && open index.html
```

## Feuille de route

- [ ] Audio enregistré par des locuteurs kinois
- [ ] Application mobile (React Native / Expo) avec notifications de rappel
- [ ] Comptes et classement entre amis
- [ ] Nouvelles unités : transport, santé, musique (rumba, ndombolo)

## Contribuer

Tu parles lingala ? Les corrections de vocabulaire et les propositions de phrases sont les bienvenues : ouvre une *issue* ou une *pull request*.

---

Fait à Kinshasa par [Christophe Mbadu](https://www.linkedin.com/in/christophe-mbadu-b61179289/).
