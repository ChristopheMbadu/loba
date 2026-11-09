-- Durcissement : aucun accès pour le rôle anon aux tables de paiement (inutilisées pour l'instant).
revoke all on public.payments, public.subscriptions from anon;
