# Rôle de chaque fonction du binding

Ce code est un mini-framework MVC (type Spring) : il transforme une requête HTTP en arguments pour une méthode de contrôleur.

## Vue d'ensemble

```
buildArguments
   └─ resolveArgument (pour chaque paramètre)
        ├─ resolveRequestParam (si @MyRequestParam)
        │     ├─ type simple  → convertParamValue
        │     └─ type objet   → bindObject
        │                          ├─ instantiate
        │                          ├─ setField / convertFieldValue
        │                          └─ trim
        └─ req / res / Model / Map (injection directe)
```

## Niveau 1 : point d'entrée

### `buildArguments`
Prépare le tableau d'arguments à passer à la méthode du contrôleur. Il parcourt tous les paramètres de la méthode et appelle `resolveArgument` pour chacun.

### `resolveArgument`
Décide quoi injecter pour **un** paramètre :
- annoté `@MyRequestParam` → délègue à `resolveRequestParam`
- type `HttpServletRequest` / `HttpServletResponse` → injecte `req` / `res`
- type `Model` → crée un nouveau `Model`
- type `Map` → crée une `LinkedHashMap` vide
- sinon → `null`

## Niveau 2 : paramètres annotés

### `resolveRequestParam`
Gère un paramètre `@MyRequestParam("nom")`.
- Type simple (String, int...) : lit `req.getParameter(name)`, lance une erreur s'il manque, puis convertit.
- Type objet (ex. `User`) : appelle `bindObject` pour construire l'objet à partir de plusieurs paramètres.

### `isSimpleType`
Dit si un type est « simple » : String, primitifs, enum, Boolean, Character, Number. Sert à choisir entre conversion directe et binding d'objet.

## Niveau 3 : binding d'objets

### `bindObject`
Fonction centrale du binding d'objets. Elle :
1. crée l'instance (`instantiate`) ;
2. parcourt chaque champ non static/final ;
3. cherche la valeur dans la requête avec les candidats `prefixe.champ` (ex. `user.name`) puis `champ` seul (ex. `name`) ;
4. si trouvée → convertit et affecte le champ ;
5. sinon, si le champ est un objet avec constructeur vide → **récursion** (objets imbriqués, ex. `user.address.city`), limitée par `MAX_BINDING_DEPTH`.

### `trim`
Limite le nombre de préfixes candidats à `MAX_BINDING_PREFIXES` pour éviter une explosion combinatoire lors de la récursion.

### `setField`
Affecte une valeur à un champ par réflexion (`setAccessible(true)` permet d'écrire même dans un champ `private`). Transforme l'erreur d'accès en `ServletException`.

### `instantiate`
Crée une instance de la classe cible via son constructeur sans argument (même privé). Erreur claire si le constructeur vide n'existe pas.

### `hasDefaultConstructor`
Vérifie qu'une classe a un constructeur vide. Utilisé dans `bindObject` pour savoir si on peut tenter de lier un champ objet imbriqué.

## Niveau 4 : conversion de valeurs

### `convertFieldValue`
Convertit la valeur d'un **champ** d'objet. Refuse les types non simples (avec un message qui suggère d'utiliser un préfixe), sinon délègue à `convertParamValue`.

### `convertParamValue`
Convertisseur principal `String → type cible` : String, boolean, char, int, long, short, byte, double, float, BigInteger, BigDecimal, enum. Transforme les erreurs de format (`NumberFormatException`, etc.) en `ServletException` lisible.

### `parseBoolean`
Interprète `true`, `on`, `yes`, `1` comme `true`, tout le reste comme `false` (pratique pour les cases à cocher HTML).

### `checkRange`
Vérifie qu'un nombre rentre dans les limites du type (ex. `byte` : -128 à 127) pour éviter un dépassement silencieux.

## Utilitaire

### `describe`
Construit un texte du type `(paramètre x de MaClasse.maMethode)` ajouté aux messages d'erreur, pour savoir exactement où le problème se situe.

---

## Résumé

`buildArguments` → `resolveArgument` → (`resolveRequestParam` → `bindObject` / `convertParamValue`) ; les autres fonctions sont des outils au service de ces quatre-là.

