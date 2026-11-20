# Prototype de design ADITV (données fictives)

Interface TV 1920 × 1080 navigable au clavier comme une télécommande. Aucune connexion, aucun flux, aucun service tiers : uniquement le design et les interactions de démonstration.

Ouvrir `index.html` via un petit serveur local (`python3 -m http.server` dans ce dossier), puis :
- flèches = croix directionnelle, Entrée = OK, Échap ou ⌫ = Retour ;
- D = états de démonstration (erreur catalogue, liste vide, lecture en échec, animations réduites, son de l'ouverture) ;
- M = animations réduites (respecte aussi le réglage du système) ;
- `?screen=home|films|series|live|search|mylist|film|serie` pour ouvrir directement un écran.

Écrans : ouverture animée, accueil immersif, films, séries, fiche film (distribution, recommandations), fiche série (saisons, épisodes), direct + guide (rappels), lecteur (commandes, audio/sous-titres, versions, Turbo, statistiques mesuré/estimé/déclaré/N/D, zapping), recherche, Ma liste ; chargements, états vides et erreurs.

Visuels : recadrés depuis les maquettes fournies par Adama (`docs/dossier/05_Assets`) ; titres, chaînes, personnes et matchs sont inventés.
