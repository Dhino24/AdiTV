# Lecture, mesures et Turbo
## Deux moteurs intégrés
LibVLC est intégré derrière notre UI, sans lancer l’application VLC externe. Contrat commun : prepare/play/pause/seek/selectAudio/selectSubtitle/release, capacités et événements. Media3 par défaut provisoire ; mode Auto avec politique testée, choix manuel mémorisable par contenu/chaîne. Bascule sur erreur de compatibilité identifiée, pas sur tout ralentissement ; une tentative bornée, pas de ping-pong. Libérer le moteur précédent avant nouvelle connexion ; conserver position VOD, sélection de pistes lorsque compatible. Un timeout n’est pas une preuve d’incompatibilité codec.

## Statistiques
| Mesure | Définition | Précaution |
|---|---|---|
| Réception lissée | 8 × octets reçus / durée de fenêtre / 1 000 000 | Fenêtre explicite, ex. 5 s ; inclure périodes sans trafic |
| Transfert actif | Octets / temps de transfert actif | Valeur différente, éventuellement supérieure au débit vidéo |
| Moyenne session | Octets réseau depuis début / durée écoulée | Définir inclusion du démarrage |
| Bande passante estimée | Estimation moteur basée sur transferts | Libellé estimée, pas speedtest Internet |
| Débit média | Valeur déclarée ou estimation documentée | Les pics VBR varient ; ne pas confondre Mb/s et Mo/s |
| Réserve | Position chargée moins position lue si fiable | N/D si le moteur/flux ne l’expose pas |
| Première image | Temps monotone entre demande Lire et première frame | Distinguer préparation et lecture |
| Rebuffering | Entrée buffering après premier démarrage | Exclure pause utilisateur et seek explicitement |
| Vidéo | Résolution décodée, codec, FPS si connu, images perdues | Ne pas utiliser la résolution de l’écran à la place |

Implémenter une couche par moteur ; ne pas recopier une mesure Media3 dans LibVLC sans équivalent. Timestamps monotones, métriques par session. États : mesuré/estimé/indisponible. Affichage 1 fois/s maximum ; collecte légère et stockage local d’agrégats. Masquer les URLs/tokens dans les exports. Une faible réception en HLS peut correspondre à un buffer plein ou à l’attente de segments ; pas forcément à une panne.

## Turbo, priorité à la stabilité
Modes Normal et Turbo. Politique différente direct/VOD, paramètres bornés et ajustés aux mesures. Augmenter la réserve cible seulement si source et mémoire le permettent. Exemples de départ à expérimenter : VOD 10–20 s normal, 30–60 s Turbo ; direct 2–5 s normal, 6–12 s Turbo. Ce sont des cibles exploratoires, pas une configuration de production ; conserver aussi un plafond d’octets, réduire en cas de pression mémoire et évaluer le retard direct. Tous les moteurs ne proposent pas le même contrôle de tampon : adapter et signaler les limites.

Suspendre enrichissement, images hors écran, import EPG et tâches non essentielles pendant difficultés de lecture. Réutiliser les connexions et adapter timeouts/reprises selon transport. Backoff borné avec jitter, annulation à la sortie et plafond de tentatives. Ne jamais répéter une erreur d’auth comme une erreur réseau. Adapter la qualité uniquement si renditions disponibles ; pas de fausse conversion 4K vers HD côté client.

## Limites physiques
Un lecteur ne force pas le débit d’un serveur, ne crée pas de segments live futurs et n’améliore pas un débit durablement inférieur au besoin média en augmentant indéfiniment le buffer. HTTP/2/3 ne servent que si l’ensemble client/serveur est compatible. Fire OS ne doit pas dépendre du Cronet Google Play Services ; vérifier une option embarquée ou utiliser une pile compatible simple. Multiconnexion, range parallèle et double ouverture hors MVP ; l’éventuelle expérimentation future exige support serveur et limites fournisseur vérifiées, avec mesures comparatives.

## Diagnostic lisible
Présenter des observations et une hypothèse prudente : réserve vide + réception faible ; décodage qui perd des images malgré réserve ; attente serveur au démarrage ; erreur d’accès. Fournir action réversible pertinente et bouton retour aux réglages normaux. Ne pas prétendre distinguer à coup sûr Wi-Fi et serveur sans mesures distinctes. Pas de speedtest lourd pendant une lecture.
