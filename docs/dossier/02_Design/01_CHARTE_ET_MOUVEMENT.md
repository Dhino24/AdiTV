# Charte et mouvement
## Identité
Logo de référence : `../05_Assets/ADITV_logo_valide.png`. Le fichier sur fond anthracite est validé ; aucun logo transparent défectueux n’est inclus. Pour l’app, reconstruire plus tard un tracé propre et ses exports en conservant l’identité, sans traiter le PNG comme un SVG final.

Base couleur : fond #090D12, surfaces #111922 et #18232E, texte #F5F7FA, secondaire #ADB8C5, accent #00D7D7. Valeurs proposées, ajustables après contrôle de contraste. Typographie système ou police explicitement licenciée, sans dépendance distante. Grille 8 dp, rayons 12–20 dp. Tailles de départ à tester au canapé : texte 18–22 sp, titres 28–36 sp ; hiérarchie et distance priment sur la densité.

## Composition
Menu gauche compact, déploiement sur focus ; hero supérieur avec dégradé discret garantissant le contraste ; boutons Lire/Reprendre et détails ; rangées horizontales peu nombreuses ; visuels dimensionnés. Aucun panneau statistique permanent sur l’accueil. Turbo apparaît dans le lecteur. Pas de flou plein écran animé sur chaque mouvement ni multiples vidéos d’arrière-plan.

## Focus
Un seul focus ; bordure visible même sans couleur ; agrandissement 1,03–1,05 sans déplacer la grille ; texte jamais rogné. Défilement conservant le prochain élément visible. Restaurer item, rangée et position. Le menu retient sa sélection indépendamment du contenu. Toutes les commandes ont un libellé accessible.

## Mouvement
Cibles proposées : réponse visuelle au focus <100 ms ; transition de focus 120–180 ms ; ouverture d’une fiche 200–300 ms ; intro 2,4 s. Transformer opacité/échelle/position, éviter les recalculs lourds. Animation annulable sans file d’attente quand l’utilisateur maintient une flèche. Respecter réduction des animations ; pas d’animation obligatoire au lancement.

## Fluidité et UHD
Viser 60 Hz quand disponible, temps de frame proche de 16,7 ms sur scénario défini. Mesurer les percentiles et les frames lentes sur l’appareil ; ne pas annoncer 60 FPS à partir d’un navigateur. Les sources graphiques doivent être adaptées aux dimensions visibles, pas toutes téléchargées en taille originale. Résolution vidéo 4K, HDMI UHD et canvas UI sont distincts : contrôler le rendu effectif et la mémoire avant toute exigence de rendu natif 3840 × 2160.

La première maquette jointe est une référence d’ambiance, pas une capture de l’app. Les futures maquettes doivent améliorer le menu compact et présenter des contenus variés, pas limiter ADITV au cinéma sénégalais.
