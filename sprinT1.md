init fonction appelena indray 

# Sprint 1
## framework:
    manabotra anotation

## test:
     creer un controller d annotena

          :mande au demarage(mapiasa listner)  
code kely: listener
            :premier appel 

mila fantatra ny controller rhtra

## FrontServlet:
    asina list controller

methode hijerena            

modifier les 

3bis invoker les methode


modelAndView
invoker any @ framework
de return modelAndView si map si url 

Sprint 4 special
Containt listner (changer la fonction init en listner )
 dans Listner il y a  filter class

Sprint 5
Page de liste
Mise en place de la base de données
Création de la classe d’accès aux données (repository)
Pas de service, mais le repository est directement appelé par le controller

En fait, la liste est d’abord codée en dur dans le controller.
Ensuite, les données sont récupérées et envoyées vers les vues.
Autrement dit, le controller identifie les vues.

String list(Model m)
m.setAttribute("clé", valeur)

Model : paramètre de type Map
Préfixe et suffixe : utilisés pour configurer les vues

Utilisation de ModelAndView
Dans le sprint 5, on utilise ModelAndView.
La fonction retourne un objet ModelAndView.
On instancie ModelAndView.
Si ModelAndView est traité, sinon on invoque simplement.
Dans la vue, on appelle request.getAttribute et on boucle sur les données.

En résumé, ce sprint consiste à :

- Mettre en place la structure repository -> controller -> view
- Utiliser ModelAndView pour gérer le passage des données aux vues
- Configurer correctement les préfixes et suffixes pour le rendu

demarrer container

methode utilitaire qui donne le stx
aplication 

comment avoir le instence container spring au demarage
invoker dans 

mis instancie bean au @ Mycontroller

# sprint -6
creer  un API
=>dans le framework en creer un annotation WebApi lorsque on utilise cet annotation dans la methode annoter retour  (json)
annotation toJson transformer un object on json
qui ne fonction dan view mais return un json

dans frontservlet test cet annotation si il existe , il doit retourn u json ;
sinon 

# sprint 7
invok parametr d un fonction 
get les valeur d un input d un formulaire 
    si le type de fonction est parametre est object donc return erreur exeption
    sinon le afficher les valeur de   


## suite
upload
session
role(authorisation)

