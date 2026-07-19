# Etapes de developpement du Framework Spring-like

---

## Etape 1 : Creer l'annotation `@Autowired`

**Fichier** : `framework/src/java/com/lovapinto/Autowired.java`

**But** : Permettre au conteneur IoC d'injecter automatiquement les dependances dans les champs d'un bean.

```java
package com.lovapinto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)   // disponible a l'execution (pour la reflexion)
@Target(ElementType.FIELD)            // s'applique uniquement sur les attributs
public @interface Autowired {
}
```

**Explication** :
- `@Retention(RetentionPolicy.RUNTIME)` : l'annotation est visible pendant l'execution, ce qui permet au conteneur de la detecter via la reflexion Java.
- `@Target(ElementType.FIELD)` : on ne peut l'utiliser que sur des attributs de classe, pas sur des methodes ou des constructeurs.
- L'annotation est vide (pas d'attributs), c'est juste un marqueur.

**Utilisation** :
```java
@Autowired
private UserRepository userRepository;   // le conteneur injecte l'instance automatiquement
```

---

## Etape 2 : Creer l'annotation `@RepositoryAnnotation`

**Fichier** : `framework/src/java/com/lovapinto/RepositoryAnnotation.java`

**But** : Marquer les classes d'acces aux donnees (Repository) pour que le conteneur les detecte et leur injecte automatiquement une connexion `java.sql.Connection`.

```java
package com.lovapinto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RepositoryAnnotation {
}
```

**Explication** :
- `@Target(ElementType.TYPE)` : s'applique sur la classe entiere, pas sur un champ.
- Le conteneur `ApplicationContext` detecte cette annotation pendant le scan et injecte la connexion JDBC dans les champs de type `Connection`.

**Utilisation** :
```java
@RepositoryAnnotation
public class UserRepository {
    private Connection connection;   // injecte automatiquement par le conteneur
}
```

---

## Etape 3 : Creer `DatabaseConfig` (gestion de la connexion JDBC)

**Fichier** : `framework/src/java/com/lovapinto/DatabaseConfig.java`

**But** : Gerer la connexion a MySQL via JDBC. Fournit une connexion unique (singleton) partagee par tous les Repository.

```java
package com.lovapinto;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private final String url;        // ex: jdbc:mysql://localhost:3306/db_framework
    private final String user;       // ex: root
    private final String password;   // ex: root
    private Connection connection;   // connexion unique (singleton)

    public DatabaseConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    // Fournit la connexion. Si elle n'existe pas ou est fermee, on la cree.
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                // Charge le driver MySQL
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("Driver MySQL introuvable", e);
            }
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    public String getUrl() { return url; }
    public String getUser() { return user; }

    // Ferme la connexion quand l'application s'arrete
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
```

**Explication** :
- Le constructeur recoit les 3 parametres de connexion (lu depuis `web.xml`).
- `getConnection()` : cree la connexion au premier appel, la reutilise aux appels suivants (pattern singleton).
- `Class.forName("com.mysql.cj.jdbc.Driver")` : charge le driver MySQL (necessaire pour JDBC).
- `close()` : ferme la connexion proprement. Appele par `FrontServletListener.contextDestroyed()`.

**Parametres dans `web.xml`** :
```xml
<context-param>
    <param-name>db-url</param-name>
    <param-value>jdbc:mysql://localhost:3306/db_framework</param-value>
</context-param>
<context-param>
    <param-name>db-user</param-name>
    <param-value>root</param-value>
</context-param>
<context-param>
    <param-name>db-password</param-name>
    <param-value></param-value>
</context-param>
```

---

## Etape 4 : Creer `ApplicationContext` (conteneur IoC)

**Fichier** : `framework/src/java/com/lovapinto/ApplicationContext.java`

**But** : C'est le coeur du framework. Il remplace le `new UserRepository()` manuel. Il :
1. Scanne les packages pour trouver les classes avec `@MyController`, `@FWController`, `@RepositoryAnnotation`
2. Instancie chaque classe en **singleton** (une seule instance)
3. Injecte `@Autowired` (dependances entre beans)
4. Injecte `Connection` dans les `@RepositoryAnnotation`

```java
package com.lovapinto;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationContext {

    private final Map<Class<?>, Object> beans = new HashMap<>();          // stockage des instances singleton
    private final Map<String, Class<?>> beanNames = new HashMap<>();      // index par nom de classe
    private final List<Class<?>> beanClasses = new ArrayList<>();         // toutes les classes detectees
    private final DatabaseConfig databaseConfig;                          // config de la base de donnees

    // --- CONSTRUCTEUR ---
    // Recoit la liste des packages (separes par virgule) et la config DB
    public ApplicationContext(String packagesList, DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
        try {
            // Scanne chaque package
            String[] packages = packagesList.split(",");
            List<Class<?>> allClasses = new ArrayList<>();
            for (String pkg : packages) {
                String trimmed = pkg.trim();
                if (!trimmed.isEmpty()) {
                    allClasses.addAll(scanPackage(trimmed));
                }
            }
            // Instancie les beans
            instantiateBeans(allClasses);
            // Injecte les dependances @Autowired
            injectDependencies();
        } catch (Exception e) {
            throw new RuntimeException("Erreur initialisation ApplicationContext", e);
        }
    }

    // --- SCAN DU PACKAGE ---
    // Parcourt le repertoire du package et cherche les classes avec les bonnes annotations
    private List<Class<?>> scanPackage(String packageName) throws Exception {
        List<Class<?>> result = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);
        if (resource == null) return result;

        File directory = new File(resource.toURI());
        File[] files = directory.listFiles();
        if (files == null) return result;

        for (File file : files) {
            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                Class<?> clazz = Class.forName(className);
                // Filtre : uniquement les classes avec @MyController, @FWController ou @RepositoryAnnotation
                if (clazz.isAnnotationPresent(MyController.class)
                        || clazz.isAnnotationPresent(FWController.class)
                        || clazz.isAnnotationPresent(RepositoryAnnotation.class)) {
                    result.add(clazz);
                }
            }
        }
        return result;
    }

    // --- INSTANCIATION DES BEANS ---
    // Cree une instance de chaque classe via le constructeur sans argument
    private void instantiateBeans(List<Class<?>> classes) throws Exception {
        for (Class<?> clazz : classes) {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                constructor.setAccessible(true);    // force l'access si le constructeur est private
            }
            Object instance = constructor.newInstance();

            // Stocke l'instance en singleton
            beans.put(clazz, instance);
            beanNames.put(clazz.getName(), clazz);
            beanClasses.add(clazz);

            // Si c'est un Repository, injecte la connexion JDBC
            if (databaseConfig != null && clazz.isAnnotationPresent(RepositoryAnnotation.class)) {
                injectConnection(instance);
            }
        }
    }

    // --- INJECTION DE LA CONNEXION ---
    // Cherche un champ de type java.sql.Connection et y met la connexion
    private void injectConnection(Object instance) {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getType().getName().equals("java.sql.Connection")) {
                try {
                    field.setAccessible(true);
                    field.set(instance, databaseConfig.getConnection());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // --- INJECTION DES DEPENDANCES @Autowired ---
    // Pour chaque bean, cherche les champs avec @Autowired et injecte le bean correspondant
    private void injectDependencies() {
        for (Object bean : beans.values()) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    // Cherche un bean du meme type que le champ
                    Object dependency = findBeanByType(field.getType());
                    if (dependency != null) {
                        try {
                            field.setAccessible(true);
                            field.set(bean, dependency);    // injecte l'instance
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    // --- RECHERCHE DE BEAN PAR TYPE ---
    // Parcourt tous les beans et retourne celui qui est du type demande
    private Object findBeanByType(Class<?> type) {
        for (Object bean : beans.values()) {
            if (type.isInstance(bean)) {
                return bean;
            }
        }
        return null;
    }

    // --- GET BEAN ---
    // Recupere une instance par sa classe
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        Object bean = beans.get(clazz);
        if (bean != null) return (T) bean;
        for (Object instance : beans.values()) {
            if (clazz.isInstance(instance)) return (T) instance;
        }
        return null;
    }

    // Recupere une instance par son nom de classe (String)
    public Object getBean(String className) {
        Class<?> clazz = beanNames.get(className);
        if (clazz != null) return beans.get(clazz);
        return null;
    }

    public List<Class<?>> getBeanClasses() { return beanClasses; }
    public Collection<Object> getAllBeans() { return beans.values(); }
    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
}
```

**Explication detaillee** :

1. **Scan** : `scanPackage()` lit les fichiers `.class` du repertoire, charge chaque classe avec `Class.forName()`, et verifie si elle a les annotations `@MyController`, `@FWController` ou `@RepositoryAnnotation`.

2. **Instanciation** : `instantiateBeans()` utilise la reflexion (`Constructor.newInstance()`) pour creer chaque bean. Le constructeur doit etre accessible (public ou force avec `setAccessible(true)`).

3. **Injection Connection** : `injectConnection()` cherche un champ dont le type est `java.sql.Connection` et y injecte la connexion fournie par `DatabaseConfig`.

4. **Injection @Autowired** : `injectDependencies()` parcourt tous les beans, cherche les champs avec `@Autowired`, puis appelle `findBeanByType()` pour trouver le bean du bon type et l'injecter.

5. **Singleton** : chaque bean n'est instancie qu'une seule fois et stocke dans la Map `beans`.

**Flux d'execution** :
```
new ApplicationContext("Controller,Repository", databaseConfig)
    |
    +-- scanPackage("Controller")   --> [UserController.class, ClassAController.class]
    +-- scanPackage("Repository")   --> [UserRepository.class]
    |
    +-- instantiateBeans()          --> {UserController => instance, UserRepository => instance, ...}
    +-- injectConnection()          --> UserRepository.connection = databaseConfig.getConnection()
    +-- injectDependencies()        --> UserController.userRepository = UserRepository instance
```

---

## Etape 5 : Modifier `FrontServletListener` (demarrage du conteneur)

**Fichier** : `framework/src/java/com/lovapinto/FrontServletListener.java`

**But** : Au demarrage de Tomcat, creer le `ApplicationContext` et le stocker dans le `ServletContext`.

```java
package com.lovapinto;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class FrontServletListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ServletContext context = sce.getServletContext();

            // 1. Lit les packages a scanner depuis web.xml (ex: "Controller,Repository")
            String packageName = context.getInitParameter("controllers-package");
            if (packageName == null || packageName.isBlank()) {
                packageName = "Controller";
            }

            // 2. Lit les parametres de connexion a la base de donnees
            String dbUrl = context.getInitParameter("db-url");
            String dbUser = context.getInitParameter("db-user");
            String dbPassword = context.getInitParameter("db-password");

            // 3. Cree DatabaseConfig si les parametres existent
            DatabaseConfig databaseConfig = null;
            if (dbUrl != null && !dbUrl.isBlank()) {
                databaseConfig = new DatabaseConfig(dbUrl, dbUser, dbPassword);
            }

            // 4. Cree le conteneur IoC (scanne + instancie + injecte)
            ApplicationContext applicationContext = new ApplicationContext(packageName, databaseConfig);

            // 5. Stocke le conteneur dans le ServletContext (accessible par FrontServlet)
            context.setAttribute("applicationContext", applicationContext);
        } catch (Exception e) {
            throw new RuntimeException("Erreur initialisation ApplicationContext", e);
        }
    }

    // Quand Tomcat s'arrete, ferme la connexion a la base
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        Object obj = context.getAttribute("applicationContext");
        if (obj instanceof ApplicationContext) {
            ApplicationContext appCtx = (ApplicationContext) obj;
            if (appCtx.getDatabaseConfig() != null) {
                appCtx.getDatabaseConfig().close();
            }
        }
    }
}
```

**Explication** :
- `contextInitialized()` : appele une seule fois au demarrage de Tomcat. C'est ici que tout commence.
- Le `DatabaseConfig` est cree avec les parametres `db-url`, `db-user`, `db-password` lus depuis `web.xml`.
- L'`ApplicationContext` est cree avec les packages et la config DB. Il fait tout le travail de scan, instanciation et injection.
- Le resultat est stocke dans `ServletContext` sous la cle `"applicationContext"`.
- `contextDestroyed()` : appele a l'arret de Tomcat, ferme proprement la connexion a MySQL.

---

## Etape 6 : Modifier `FrontServlet` (utilisation du conteneur)

**Fichier** : `framework/src/java/com/lovapinto/FrontServlet.java`

**But** : Au lieu de gerer les instances de controllers directement, `FrontServlet` utilise maintenant `ApplicationContext`.

**Changements principaux** :

### Avant (sans conteneur) :
```java
public class FrontServlet extends HttpServlet {
    private Map<UrlMethod, Method> urlMappings = new HashMap<>();
    private Map<String, Object> controllerInstances = new HashMap<>();  // instances manuelles

    private void rebuildRegistry() {
        List<Class<?>> controllers = resolveControllers();   // scan manuel
        for (Class<?> controllerClass : controllers) {
            controllerInstances.put(controllerClass.getName(),
                instantiateController(controllerClass));      // new via reflexion
            // ...
        }
    }

    protected Object getControllerInstance(String className, ...) {
        return controllerInstances.get(className);   // cherche dans la Map locale
    }

    private Object instantiateController(Class<?> controllerClass) {
        Constructor<?> constructor = controllerClass.getDeclaredConstructor();
        return constructor.newInstance();   // cree l'instance ici
    }
}
```

### Apres (avec conteneur) :
```java
public class FrontServlet extends HttpServlet {
    private Map<UrlMethod, Method> urlMappings = new HashMap<>();
    private ApplicationContext applicationContext;   // le conteneur IoC

    public void init() throws ServletException {
        super.init();
        // Recupere le conteneur depuis ServletContext (mis par FrontServletListener)
        applicationContext = (ApplicationContext) getServletContext()
            .getAttribute("applicationContext");
        rebuildRegistry();
    }

    private void rebuildRegistry() {
        // Plus de scan manuel, utilise les classes detectees par le conteneur
        for (Class<?> controllerClass : applicationContext.getBeanClasses()) {
            if (controllerClass.isAnnotationPresent(RepositoryAnnotation.class)) {
                continue;   // ignore les Repository (pas de routes)
            }
            // Construit les mappings URL -> Method
            Map<UrlMethod, Method> classMappings = getUrlMappings(controllerClass);
            // ...
        }
    }

    protected Object getControllerInstance(String className) {
        // Demande l'instance au conteneur (singleton)
        return applicationContext.getBean(className);
    }

    // instantiateController() SUPPRIME : le conteneur gere l'instanciation
}
```

**Resume des changements** :
| Avant | Apres |
|-------|-------|
| `Map<String, Object> controllerInstances` | **Supprime** — utilise `ApplicationContext` |
| `resolveControllers()` + `scanControllers()` | **Supprime** — le conteneur fait le scan |
| `instantiateController()` | **Supprime** — le conteneur instancie les beans |
| `getControllerInstance()` cherche dans Map locale | `getControllerInstance()` appelle `applicationContext.getBean()` |
| `rebuildRegistry()` scanne + instancie | `rebuildRegistry()` lit juste `applicationContext.getBeanClasses()` |

---

## Etape 7 : Modifier `UserRepository` (JDBC au lieu de donnees en dur)

**Fichier** : `testmonjar/src/main/java/Repository/UserRepository.java`

**But** : Lire les donnees depuis MySQL au lieu du `List.of("Alice", "Bob", "Charlie")`.

### Avant :
```java
package Repository;

import java.util.List;

public class UserRepository {

    public List<String> findAll() {
        return List.of("Alice", "Bob", "Charlie");   // donnees en dur
    }
}
```

### Apres :
```java
package Repository;

import com.lovapinto.RepositoryAnnotation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@RepositoryAnnotation          // marque cette classe comme Repository
public class UserRepository {

    private Connection connection;   // injecte par le conteneur via @RepositoryAnnotation

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public List<String> findAll() {
        List<String> users = new ArrayList<>();
        if (connection == null) return users;
        try {
            String sql = "SELECT name FROM users";
            PreparedStatement stmt = connection.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("name"));   // lit chaque ligne de la table
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return users;
    }
}
```

**Explication** :
- `@RepositoryAnnotation` : indique au conteneur que c'est un Repository. Le conteneur lui injecte automatiquement la `Connection`.
- `connection` : le champ `Connection` est rempli par `ApplicationContext.injectConnection()` lors de l'instanciation.
- `findAll()` : execute `SELECT name FROM users` et retourne la liste des noms.

---

## Etape 8 : Modifier `UserController` (`@Autowired`)

**Fichier** : `testmonjar/src/main/java/Controller/UserController.java`

**But** : Utiliser `@Autowired` au lieu de `new UserRepository()` pour que le conteneur injecte automatiquement le repository.

### Avant :
```java
@MyController
public class UserController {

    private final UserRepository userRepository = new UserRepository();   // instanciation manuelle

    @UrlMapping(path = "/user")
    public ModelAndView list(Model model) {
        List<String> users = userRepository.findAll();
        model.setAttribute("users", users);
        return new ModelAndView("users/list").setModelContainer(model);
    }
}
```

### Apres :
```java
@MyController
public class UserController {

    @Autowired                                            // le conteneur injecte l'instance
    private UserRepository userRepository;                 // plus de "new UserRepository()"

    @UrlMapping(path = "/user")
    public ModelAndView list(Model model) {
        List<String> users = userRepository.findAll();
        model.setAttribute("users", users);
        return new ModelAndView("users/list").setModelContainer(model);
    }
}
```

**Explication** :
- `@Autowired` dit au conteneur : "cherche un bean de type `UserRepository` et mets-le dans ce champ".
- Le conteneur trouve `UserRepository` (qui a `@RepositoryAnnotation`), et injecte l'instance singleton.
- Le `new UserRepository()` est supprime — le conteneur gere tout.

---

## Etape 9 : Modifier `web.xml` (parametres DB + packages)

**Fichier** : `testmonjar/src/main/webapp/WEB-INF/web.xml`

**But** : Ajouter les parametres de connexion a la base de donnees et les packages a scanner.

```xml
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
         https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd"
         version="5.0">
    <display-name>TD-Connexion et cookies</display-name>

    <!-- Configuration des vues JSP -->
    <context-param>
        <param-name>viewPrefix</param-name>
        <param-value>/WEB-INF/views/</param-value>
    </context-param>
    <context-param>
        <param-name>viewSuffix</param-name>
        <param-value>.jsp</param-value>
    </context-param>

    <!-- Configuration de la base de donnees MySQL -->
    <context-param>
        <param-name>db-url</param-name>
        <param-value>jdbc:mysql://localhost:3306/db_framework</param-value>
    </context-param>
    <context-param>
        <param-name>db-user</param-name>
        <param-value>root</param-value>
    </context-param>
    <context-param>
        <param-name>db-password</param-name>
        <param-value></param-value>
    </context-param>

    <!-- Packages a scanner pour les controllers et repositories -->
    <context-param>
        <param-name>controllers-package</param-name>
        <param-value>Controller,Repository</param-value>
    </context-param>

    <listener>
        <listener-class>com.lovapinto.FrontServletListener</listener-class>
    </listener>

    <servlet>
        <servlet-name>FrontServlet</servlet-name>
        <servlet-class>com.lovapinto.FrontServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>FrontServlet</servlet-name>
        <url-pattern>/controller/*</url-pattern>
    </servlet-mapping>
</web-app>
```

**Nouveaux parametres** :
| Parametre | Valeur | Role |
|-----------|--------|------|
| `db-url` | `jdbc:mysql://localhost:3306/db_framework` | URL de connexion MySQL |
| `db-user` | `root` | Utilisateur MySQL |
| `db-password` | *(vide)* | Mot de passe MySQL |
| `controllers-package` | `Controller,Repository` | Packages a scanner (separes par virgule) |

---

## Etape 10 : Modifier `deploy.bat` (ajout driver MySQL)

**Fichier** : `testmonjar/deploy.bat`

**But** : Ajouter le driver MySQL au classpath de compilation et dans le WAR.

**Changements** :

```batch
:: Ajout du chemin vers le driver MySQL
set MYSQL_JAR=..\framework\lib\mysql-connector-j-9.5.0.jar
if not exist "%MYSQL_JAR%" (
    set MYSQL_JAR=lib\mysql-connector-j-9.5.0.jar
)

:: Le classpath inclut maintenant le driver MySQL
javac -cp "%SERVLET_API_JAR%;%FRAMEWORK_JAR%;%MYSQL_JAR%" -d %BUILD_DIR%\WEB-INF\classes @sources.txt

:: Copie le driver MySQL dans WEB-INF/lib du WAR
copy /Y "%MYSQL_JAR%" "%BUILD_DIR%\WEB-INF\lib\mysql-connector-j-9.5.0.jar" >nul
```

---

## Etape 11 : Script SQL pour creer la base de donnees

```sql
-- Creer la base de donnees
CREATE DATABASE IF NOT EXISTS db_framework;

-- Selectionner la base
USE db_framework;

-- Creer la table users
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

-- Inserer des donnees de test
INSERT INTO users (name) VALUES ('Alice'), ('Bob'), ('Charlie');
```

---

## Etape 12 : Compiler le framework et creer le JAR

```bash
# Depuis le dossier framework/
# 1. Compiler toutes les classes Java
javac -cp "C:\apache-tomcat-10.1.55-windows-x64\apache-tomcat-10.1.55\lib\servlet-api.jar;lib\mysql-connector-j-9.5.0.jar" -d build\classes src\java\com\lovapinto\*.java

# 2. Creer le JAR (depuis build/classes/ pour avoir le bon chemin)
cd build\classes
jar cvf sprint1.jar com\lovapinto\*.class
cd ..\..\..

# 3. Copier le JAR dans lib/ du framework
copy /Y build\classes\sprint1.jar lib\sprint1.jar
```

---

## Etape 13 : Deploiement

```bash
# Depuis le dossier testmonjar/
.\deploy.bat
```

Puis redemarrer Tomcat et acceder a :
```
http://localhost:8080/s5/controller/user
```

---

## Schema recapitulatif : Flux d'execution

```
TOMCAT DEMARRE
    |
    v
FrontServletListener.contextInitialized()
    |
    +-- Lit web.xml : db-url, db-user, db-password, controllers-package
    |
    +-- Cree DatabaseConfig(url, user, password)
    |
    +-- Cree ApplicationContext("Controller,Repository", databaseConfig)
    |       |
    |       +-- scanPackage("Controller")   --> UserController.class, ClassAController.class
    |       +-- scanPackage("Repository")   --> UserRepository.class
    |       |
    |       +-- instantiateBeans()
    |       |       UserController    => instance 1
    |       |       ClassAController  => instance 2
    |       |       UserRepository    => instance 3
    |       |
    |       +-- injectConnection()
    |       |       UserRepository.connection = DatabaseConfig.getConnection() --> MySQL
    |       |
    |       +-- injectDependencies()
    |               UserController.userRepository = instance 3 (UserRepository)
    |
    +-- context.setAttribute("applicationContext", applicationContext)
    |
    v
FrontServlet.init()
    |
    +-- applicationContext = getServletContext().getAttribute("applicationContext")
    +-- rebuildRegistry() --> construit urlMappings depuis applicationContext.getBeanClasses()
    |
    v
REQUETE HTTP : GET /controller/user
    |
    v
FrontServlet.handleRequest()
    |
    +-- Cherche la methode pour /user + GET --> UserController.list()
    +-- applicationContext.getBean("Controller.UserController") --> instance singleton
    +-- UserController.list(model)
    |       |
    |       +-- userRepository.findAll()
    |       |       |
    |       |       +-- SELECT name FROM users --> MySQL
    |       |       +-- retourne ["Alice", "Bob", "Charlie"]
    |       |
    |       +-- model.setAttribute("users", users)
    |       +-- return new ModelAndView("users/list").setModelContainer(model)
    |
    +-- renderModelAndView() --> forward vers users/list.jsp
    |
    v
JSP affiche : Alice, Bob, Charlie (depuis MySQL)
```

---

## Liste de tous les fichiers du framework

| Fichier | Type | Role |
|---------|------|------|
| `ApplicationContext.java` | **NOUVEAU** | Conteneur IoC - scan, instancie, injecte |
| `Autowired.java` | **NOUVEAU** | Annotation @Autowired |
| `RepositoryAnnotation.java` | **NOUVEAU** | Annotation @Repository |
| `DatabaseConfig.java` | **NOUVEAU** | Gestion connexion JDBC |
| `FrontServletListener.java` | MODIFIE | Cree le conteneur au demarrage |
| `FrontServlet.java` | MODIFIE | Utilise le conteneur |
| `FWController.java` | INCHANGE | Annotation existante |
| `MyController.java` | INCHANGE | Annotation existante |
| `MyEntity.java` | INCHANGE | Annotation existante |
| `UrlMapping.java` | INCHANGE | Annotation existante |
| `UrlMethod.java` | INCHANGE | Cle composite URL+Method |
| `Model.java` | INCHANGE | Conteneur de donnees |
| `ModelAndView.java` | INCHANGE | Paire View + Model |
| `SampleServlet.java` | INCHANGE | Servlet utilitaire |

## Liste de tous les fichiers de testmonjar

| Fichier | Type | Role |
|---------|------|------|
| `UserController.java` | MODIFIE | Utilise @Autowired |
| `UserRepository.java` | MODIFIE | Lit depuis MySQL |
| `ClassAController.java` | INCHANGE | Controller vide |
| `web.xml` | MODIFIE | Parametres DB + packages |
| `deploy.bat` | MODIFIE | Ajout driver MySQL |
| `users/list.jsp` | INCHANGE | Affiche la liste |
