# Sécurité de Loba

Revue réalisée le 9 novembre 2026.

## Ce qui est en place

### Application (navigateur)
- **Content-Security-Policy** stricte (balise meta) : scripts limités à l'app et au CDN jsDelivr (version de supabase-js figée), connexions limitées à Supabase, `object-src 'none'`, `base-uri 'none'`, `form-action 'none'`.
- **Aucun `eval`** ni `new Function`.
- **Échappement HTML** (`esc()`) de toute donnée venant de l'utilisateur ou du stockage avant affichage.
- **Assainissement de l'état** (`sanitize()`) : la progression lue depuis le `localStorage` ou le cloud est validée champ par champ (types, bornes, longueurs). Un état falsifié ne peut ni casser l'app ni injecter de code.
- Tous les liens externes ont `rel="noopener noreferrer"`.
- Aucune publicité, aucun traqueur, aucun cookie tiers.

### Serveur (Supabase, région UE)
- **Row Level Security** activée sur toutes les tables publiques.
- Table `progress` : chaque utilisateur ne peut lire et écrire que **sa propre ligne** ; suppression interdite ; taille limitée à 200 Ko ; données obligatoirement de type objet JSON.
- Table `push_subs` (rappels) : chaque utilisateur ne gère que ses propres abonnements ; seules les adresses des vrais services de notification (Google, Apple, Mozilla, Microsoft) sont acceptées ; la date d'envoi n'est modifiable que par le serveur.
- Fonction `daily-reminder` protégée par un secret partagé avec la tâche planifiée ; clé privée VAPID stockée en secret Supabase.
- Ligues et amis : tables `public_profiles`, `league_entries`, `follows` non lisibles directement ; tout passe par des fonctions qui ne renvoient que pseudo, avatar, série et XP. Valeurs bornées côté serveur.
- Rôle `anon` sans aucun droit sur les tables.
- Anciennes fonctions de paiement (CinetPay) **supprimées** du serveur ; tables de paiement fermées au public.
- Seule la clé publique `anon` est dans l'app (c'est prévu pour) ; la clé `service_role` n'est jamais exposée.

### Tests réalisés en production
| Test | Résultat |
|---|---|
| Écrire / lire sa propre progression | ✅ autorisé |
| Écrire la progression d'un autre utilisateur | ⛔ 403 |
| Supprimer une progression | ⛔ 403 |
| Accès sans session | ⛔ 401 |
| Envoyer des données trop volumineuses | ⛔ 400 |
| Appeler les anciennes fonctions de paiement | ⛔ 404 |

## Recommandations restantes
1. **Régénérer l'ancienne clé `service_role`** dans Supabase (Settings → API) par précaution, puisqu'elle a servi pendant le développement.
2. **Activer un captcha** (hCaptcha ou Cloudflare Turnstile) sur l'authentification anonyme si des abus apparaissent (création massive de comptes).
3. Garder les limites d'envoi d'e-mails de Supabase (activées par défaut).

## Signaler une faille
Écris à **mbaduchristophe@gmail.com** sans publier les détails dans une *issue*. Réponse sous 72 h.
