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

## Etape 14 : API JSON avec l'annotation `@WebApi`

**Fichiers** : `framework/src/java/com/lovapinto/WebApi.java` (nouveau), `framework/src/java/com/lovapinto/JsonSerializer.java` (nouveau), `framework/src/java/com/lovapinto/FrontServlet.java` (modifie)

**But** : quand une methode de controleur est annotee `@WebApi`, la valeur qu'elle retourne est serialisee en JSON et ecrite directement dans la reponse, au lieu d'etre traitee comme un nom de vue JSP. Sans cette annotation, le comportement du sprint 5 (ModelAndView + JSP) ne change pas.

### 1. L'annotation `@WebApi`

```java
package com.lovapinto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface WebApi {
}
```

**Explication** : `@Target` accepte `METHOD` et `TYPE`.

- Sur une **methode** : seule cette route renvoie du JSON.
- Sur une **classe** : toutes les methodes `@UrlMapping` du controleur renvoient du JSON (equivalent d'un `@RestController`).

**Utilisation** :

```java
@MyController
@WebApi                       // toutes les routes du controleur -> JSON
public class ApiController {
    @UrlMapping(path = "/api/ping")
    public String ping() {
        return "pong";
    }
}
```

### 2. Le serialiseur `JsonSerializer`

Aucune librairie externe : le framework n'utilise ni Maven ni Gradle, la compilation se fait a la main avec `javac`. Le serialiseur reflectif est donc ecrit dans le framework.

Point d'entree public :

```java
public static String toJson(Object value)
```

Types traites :

| Valeur | JSON produit |
|--------|--------------|
| `null` | `null` |
| `String`, `char` | `"..."` avec echappement |
| `Boolean` | `true` / `false` |
| `Number` | litteral ; `NaN` et `Infinity` donnent `null` (valeurs interdites en JSON) |
| `Enum` | `"NOM"` |
| `Collection`, `Iterable` | `[...]` |
| `Map` | `{"clef":valeur}` |
| Tableaux, y compris primitifs (`int[]`, `double[]`...) | `[...]` |
| `Date`, `java.time.*`, tout autre type `java.*` | `"..."` via `toString()` |
| Objet quelconque | `{...}` |

**Champs d'un objet** : le serialiseur cherche d'abord un **getter** (`getXxx()` sans parametre et non `void`, puis `isXxx()` pour un booleen) ; s'il n'en trouve pas, il lit le **champ prive** par reflection. Il ignore les membres `static`, `transient` et synthetiques, ainsi que `getClass()`. L'ordre des cles JSON suit l'ordre de declaration des champs, donc la reponse est stable d'un appel a l'autre.

**Echappement** : `"`, `\`, `\b`, `\f`, `\n`, `\r`, `\t`, plus tout caractere de controle inferieur a `0x20` ecrit en `\u00XX`. Le `/` n'est pas echappe.

**Garde-fous** : les accesseurs sont mis en cache par classe (`ClassValue`), donc la reflexion n'est pas refaite a chaque requete ; la profondeur est limitee a 32 niveaux et les objets deja vus sur le chemin courant sont ecrits `null`, ce qui empeche un `StackOverflowError` sur un graphe circulaire.

### 3. La branche JSON dans `FrontServlet`

Dans `handleRequest`, la verification se place **apres** `method.invoke(...)` et **avant** le test `instanceof ModelAndView` :

```java
Object result = method.invoke(controller, buildArguments(method, req, res));
if (isWebApi(method)) {
    renderJson(res, result);
    return;
}

if (result instanceof ModelAndView) {
    renderModelAndView(req, res, (ModelAndView) result);
    return;
}
```

```java
private boolean isWebApi(Method method) {
    return method.isAnnotationPresent(WebApi.class)
            || method.getDeclaringClass().isAnnotationPresent(WebApi.class);
}

private void renderJson(HttpServletResponse res, Object result) throws IOException {
    Object value = result instanceof ModelAndView modelAndView ? modelAndView.getModel() : result;

    res.setContentType("application/json; charset=UTF-8");
    res.setCharacterEncoding("UTF-8");
    res.getWriter().write(JsonSerializer.toJson(value));
}
```

**Explication** : l'ordre compte. La branche JSON est evaluee avant le test sur `String`, donc une methode `@WebApi` qui renvoie un `String` produit bien `"pong"` en JSON et pas un forward vers une vue. Si une methode `@WebApi` renvoie un `ModelAndView`, c'est son `model` qui est serialise (`{"users":[...]}`) et non l'objet complet. `buildArguments` n'est pas modifie : `HttpServletRequest` et `HttpServletResponse` restent injectables dans une methode `@WebApi`.

**Utilisation** :

```java
@MyController
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @UrlMapping(path = "/user")
    public ModelAndView list(Model model) {          // toujours une JSP
        model.setAttribute("users", userRepository.findAll());
        return new ModelAndView("users/list").setModelContainer(model);
    }

    @WebApi
    @UrlMapping(path = "/api/users")
    public List<String> listJson() {                  // ["Alice","Bob","Charlie"]
        return userRepository.findAll();
    }

    @WebApi
    @UrlMapping(path = "/api/info")
    public ApiInfo info() {                           // {"framework":"...","version":"sprint6",...}
        return new ApiInfo("lovapinto-spring-like", "sprint6",
                List.of("/api/users", "/api/info", "/api/ping"));
    }
}
```

Le package `Dto` est volontairement hors du parametre `controllers-package` du `web.xml` (`Controller,Repository`), sinon `ApiInfo` serait instancie comme un bean par le conteneur IoC alors que c'est un simple objet de transport.

### 4. Tester

```batch
:: 1. Compiler le framework (genere framework\lib\sprint1.jar, puis compile l'app)
cd framework
compile.bat

:: 2. Reconstruire et deployer le WAR de testmonjar
cd ..\testmonjar
deploy.bat

:: 3. Redemarrer Tomcat
```

```bash
curl -i http://localhost:8080/s5/controller/api/info
curl -i http://localhost:8080/s5/controller/api/users
curl -i http://localhost:8080/s5/controller/api/ping
curl -i http://localhost:8080/s5/controller/user
```

Attendu : les trois premieres reponses en `application/json`, la derniere reste du HTML. L'ordre des etapes 1 puis 2 est obligatoire, `deploy.bat` compile l'application contre `framework\lib\sprint1.jar` et utiliserait un jar perime sinon.

---

## Etape 15 : Binding des parametres de requete (`@MyRequestParam`)

**Fichiers** : `framework/src/java/com/lovapinto/MyRequestParam.java` (nouveau), `framework/src/java/com/lovapinto/FrontServlet.java` (modifie)

**But** : Permettre d'ecrire une methode de controleur qui recoit directement la valeur d'un `<input>` de formulaire, sans passer par `req.getParameter()` dans le corps de la methode.

### 1. L'annotation `@MyRequestParam`

```java
package com.lovapinto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface MyRequestParam {

    String value();
}
```

**Explication** :
- `@Target(ElementType.PARAMETER)` : c'est la difference essentielle avec `@Autowired` (`FIELD`) et `@WebApi` (`METHOD` ou `TYPE`). Elle ne peut poser que sur un parametre de methode, jamais sur un champ ni sur la methode elle-meme.
- `String value()` est **obligatoire** (pas de `default ""`), donc le nom du parametre HTTP est toujours explicite.
- L'annotation ne fait rien toute seule : elle est une simple marque utilisee sur le parametre, que `FrontServlet` va lire par reflexion. C'est ce qui se passe a l'etape 2.

### 2. `buildArguments` : le point d'entree

C'est la fonction appelee par `handleRequest` pour fabriquer le tableau d'arguments de `method.invoke(...)`. Elle existe depuis l'Etape 6, mais ses trois premieres lignes changent.

**Avant** :

```java
private Object[] buildArguments(Method method, HttpServletRequest req, HttpServletResponse res) {
    Class<?>[] parameterTypes = method.getParameterTypes();
    Object[] arguments = new Object[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
        Class<?> parameterType = parameterTypes[i];
        if (HttpServletRequest.class.isAssignableFrom(parameterType)) {
            arguments[i] = req;
        } else if (HttpServletResponse.class.isAssignableFrom(parameterType)) {
            arguments[i] = res;
        } else if (Model.class.isAssignableFrom(parameterType)) {
            arguments[i] = new Model();
        } else if (Map.class.isAssignableFrom(parameterType)) {
            arguments[i] = new Model();
        } else {
            arguments[i] = null;
        }
    }
    return arguments;
}
```

**Apres** :

```java
private Object[] buildArguments(Method method, HttpServletRequest req, HttpServletResponse res)
        throws ServletException {
    Parameter[] parameters = method.getParameters();
    Object[] arguments = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
        arguments[i] = resolveArgument(parameters[i], req, res);
    }
    return arguments;
}
```

**Explication** :
- le tableau renvoye a la meme taille qu'avant, c'est `Method.invoke` qui l'exige.
- le changement decisif est `method.getParameterTypes()` -> `method.getParameters()`. Les deux methodes de reflexion marchent de la meme facon, mais elles ne renvoient pas le meme objet :
  - `getParameterTypes()` renvoie des `Class<?>`. Une `Class` ne sait **rien** des annotations posees sur le parametre.
  - `getParameters()` renvoie des `Parameter`. Un `Parameter` connait son `getType()` **et** ses `getAnnotation(...)`.
- tout le reste de la logique est deplace dans `resolveArgument` : `buildArguments` devient une simple boucle, ce qui evite d'avoir une methode de 30 lignes avec 6 niveaux de `if`.

### 3. `resolveArgument` : le routeur

```java
private Object resolveArgument(Parameter parameter, HttpServletRequest req, HttpServletResponse res)
        throws ServletException {
    MyRequestParam requestParam = parameter.getAnnotation(MyRequestParam.class);
    if (requestParam != null) {
        return resolveRequestParam(parameter, requestParam.value(), req);
    }

    Class<?> parameterType = parameter.getType();
    if (HttpServletRequest.class.isAssignableFrom(parameterType)) {
        return req;
    }
    if (HttpServletResponse.class.isAssignableFrom(parameterType)) {
        return res;
    }
    if (Model.class.isAssignableFrom(parameterType)) {
        return new Model();
    }
    if (Map.class.isAssignableFrom(parameterType)) {
        return new LinkedHashMap<String, Object>();
    }
    return null;
}
```

**Explication** :
- c'est le **routeur** du binding. Deux strategies, dans un ordre de priorite voulu : **annotation d'abord**, type ensuite.
- l'annotation gagne, donc `@MyRequestParam("q") String q` est traite comme un parametre HTTP meme si on le declare `String` : c'est le type qui determine la conversion, pas la strategie choisie.
- le bloc "sinon" conserve exactement le comportement de l'Etape 6 : `HttpServletRequest`, `HttpServletResponse`, `Model` et `Map` restent injectables sans annotation, ce qui garde les controleurs existants compatibles.
- un parametre sans annotation et d'un type non reconnu recoit toujours `null`, comme avant.
- **Correction apportee** : la branche `Map` renvoyait `new Model()`, qui n'implemente pas `java.util.Map`. Un controleur qui declarait `Map<String, Object> model` plantait avec un `ClassCastException` au moment du `invoke`. Elle renvoie maintenant une vraie `LinkedHashMap`.

### 4. `resolveRequestParam` : le point de bifurcation

```java
private Object resolveRequestParam(Parameter parameter, String name, HttpServletRequest req)
        throws ServletException {
    Class<?> targetType = parameter.getType();

    if (isSimpleType(targetType)) {
        String value = req.getParameter(name);
        if (value == null) {
            throw new ServletException("Paramètre manquant : " + name + describe(parameter));
        }
        return convertParamValue(value, targetType, name);
    }

    return bindObject(targetType, new LinkedHashSet<>(List.of(name)), req, parameter, 0);
}
```

**Explication** :
- c'est ici que se decide si le parametre est un **type simple** (section 6) ou un **objet** (section 10). Le code de cette section ne montre que le premier cas ; la branche objet est decrite plus loin.
- un seul `req.getParameter(name)` dans la branche simple couvre les deux sources de donnees d'un formulaire. Tomcat a deja fusionne la query string et le body `application/x-www-form-urlencoded` avant d'appeler la servlet, donc cette ligne marche aussi bien pour `GET /x?name=Alice` que pour un `<form method="post">` envoye par le navigateur. Pas besoin de distinguer les deux cas.
- `null` et `""` sont deux choses differentes : `null` signifie "le parametre n'a pas ete envoye du tout", `""` signifie "l'utilisateur a soumis un champ vide". Seul `null` declenche une erreur, pour ne pas casser un formulaire qui contient un champ laisse vide.
- le nom lu dans l'annotation (`name`) sert aussi de prefixe et de libelle dans tous les messages d'erreur.

### 5. `describe` : des messages d'erreur exploitables

```java
private String describe(Parameter parameter) {
    return " (paramètre " + parameter.getName()
            + " de " + parameter.getDeclaringExecutable().getDeclaringClass().getName()
            + "." + parameter.getDeclaringExecutable().getName() + ")";
}
```

**Explication** :
- cette fonction n'est utilisee que pour construire le message d'erreur de l'etape 4, rien d'autre.
- sans elle, `handleRequest` ne remonterait que `Erreur invocation de submitForm`, ce qui ne dit pas **quel** parametre pose probleme ni dans **quel** controleur.
- `getDeclaringExecutable()` remonte le `Method` ou le `Constructor` qui porte le parametre, et permet de nommer la classe et la methode sans avoir a passer le `Method` en parametre supplementaire.

### 6. `convertParamValue` : la conversion `String` -> Java

C'est le coeur du binding : une requete HTTP ne transporte que du texte, alors que Java ne fait **aucune** conversion implicite `"42"` -> `42`.

```java
@SuppressWarnings({ "unchecked", "rawtypes" })
private Object convertParamValue(String value, Class<?> targetType, String name) throws ServletException {
    String raw = value.trim();

    try {
        if (targetType == String.class || targetType == CharSequence.class || targetType == Object.class) {
            return value;
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return parseBoolean(raw);
        }
        if (targetType == char.class || targetType == Character.class) {
            if (raw.length() != 1) {
                throw new ServletException("Paramètre " + name + " : un seul caractère est attendu, reçu \""
                        + value + "\"");
            }
            return raw.charAt(0);
        }
        if (targetType == int.class || targetType == Integer.class) {
            return (int) checkRange(Long.parseLong(raw), Integer.MIN_VALUE, Integer.MAX_VALUE, name, value);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(raw);
        }
        if (targetType == short.class || targetType == Short.class) {
            return (short) checkRange(Long.parseLong(raw), Short.MIN_VALUE, Short.MAX_VALUE, name, value);
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return (byte) checkRange(Long.parseLong(raw), Byte.MIN_VALUE, Byte.MAX_VALUE, name, value);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(raw);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(raw);
        }
        if (targetType == BigInteger.class) {
            return new BigInteger(raw);
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(raw);
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, raw.toUpperCase());
        }
    } catch (ServletException e) {
        throw e;
    } catch (IllegalArgumentException e) {
        throw new ServletException("Paramètre " + name + " : impossible de convertir \"" + value
                + "\" en " + targetType.getSimpleName(), e);
    }

throw new ServletException("Paramètre " + name + " : type non supporté " + targetType.getName()
                + ". Utilisez @MyRequestParam uniquement avec un type simple ou une énumération.");
}
```

**Explication** :
- chaque condition compare **deux** classes : `targetType == int.class` (type primitif) et `targetType == Integer.class` (type enveloppe). Les deux sont traitees ensemble pour qu'un `@MyRequestParam("age") int age` et un `@MyRequestParam("age") Integer age` se comportent pareil.
- le type `String` est le seul a renvoyer `value` et non `raw` : `trim()` sert a nettoyer les espaces des nombres, mais on ne doit jamais perdre les espaces d'un nom ou d'un message.
- `targetType.isEnum()` couvre toutes les enumerations en une ligne : `Enum.valueOf` est case-sensitive, d'ou le `toUpperCase()` pour accepter `actif` comme `ACTIF`.
- le `catch (ServletException e) { throw e; }` doit rester **avant** le `catch (IllegalArgumentException e)`, sinon les messages deja formats par `char` et `checkRange` seraient engloutis et remplaces par un message generique.
- **`NumberFormatException` herite de `IllegalArgumentException`**, ils ne peuvent donc pas etre annonces ensemble dans un multi-catch (erreur de compilation `Alternatives in a multi-catch statement cannot be related by subclassing`).
- le dernier `throw`, **hors du bloc `try`**, ne sert plus que de filet de securite : avec la section 10, un type non simple est traite par `bindObject` et n'arrive plus ici. Si on l'atteint quand meme, c'est que `isSimpleType` et `convertParamValue` se contredisent, et il vaut mieux une exception explicite qu'un `null` silencieux qui provoquerait un `NullPointerException` incomprehensible plus loin dans le controleur.

### 7. `parseBoolean` : le cas des cases a cocher

```java
private boolean parseBoolean(String raw) {
    if ("true".equalsIgnoreCase(raw) || "on".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw)
            || "1".equals(raw)) {
        return true;
    }
    return false;
}
```

**Explication** :
- un `<input type="checkbox">` HTML n'envoie **jamais** `"true"` : le navigateur envoie `name=on` quand la case est cochee, et n'envoie rien du tout quand elle ne l'est pas. Ce helper accepte donc `true`, `on`, `yes` et `1` comme vrais, insensiblement a la casse.
- tout le reste vaut `false`, ce qui evite le `Boolean.parseBoolean` qui ne comprendrait que `"true"` (et renverrait `false` pour `"on"`).
- le champ n'etant envoye que si la case est cochee, un parametre `boolean` absent declenche deja une erreur a l'etape 4 : c'est le comportement voulu.

### 8. `checkRange` : eviter l'overflow silencieux

```java
private long checkRange(long parsed, long min, long max, String name, String value) throws ServletException {
    if (parsed < min || parsed > max) {
        throw new ServletException("Paramètre " + name + " : la valeur \"" + value + "\" est hors limites ("
                + min + " a " + max + ")");
    }
    return parsed;
}
```

**Explication** :
- `Byte.parseByte("200")` ne leve **pas** d'exception : Java tronque silencieusement et renvoie `-56`. `Integer.parseInt("3000000000")` fait pareil. Un formulaire_non valide deviendrait donc une donnee corrompue, invisible.
- le remede : on parse d'abord en `long` (64 bits, plus large que `int`), puis on verifie que la valeur tient dans l'intervalle du type vise avant de caste.
- utilise par `int`, `short` et `byte` a l'etape 6. `long` et les flottants n'en ont pas besoin : ils sont deja assez larges, et `Long.parseLong("99999999999999999999")` leve bien une `NumberFormatException`.

### 9. Utilisation (types simples)

Une JSP qui affiche le formulaire, et une methode de controleur qui recoit les valeurs :

```jsp
<%-- testmonjar/src/main/webapp/WEB-INF/views/users/form.jsp --%>
<form action="" method="post">
    <label>Nom : <input type="text" name="name" value="${name}"></label><br>
    <label>Version : <input type="text" name="version" value="${version}"></label><br>
    <button type="submit">Envoyer</button>
</form>
```

```java
// testmonjar/src/main/java/Controller/UserController.java
@MyController
public class UserController {

    @UrlMapping(path = "/user/form")
    public ModelAndView form(Model model) {                 // GET  --> la JSP
        model.setAttribute("name", "");
        model.setAttribute("version", "");

        return new ModelAndView("users/form")
                .setModelContainer(model);
    }

    @WebApi
    @UrlMapping(path = "/user/form", method = "POST")
    public Map<String, Object> submitForm(                   // POST --> JSON
            @MyRequestParam("name") String name,
            @MyRequestParam("version") String version) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("version", version);

        return result;
    }
}
```

**Explication** :
- les deux methodes partagent le meme `path`. Ce n'est pas un doublon : `UrlMapping` est converti en cle `UrlMethod`, et `UrlMethod` contient l'URL **et** la methode HTTP. Le registre de `rebuildRegistry` accepte donc `GET /user/form` et `POST /user/form` comme deux entrees distinctes.
- `form(Model model)` est une methode `@UrlMapping` classique, elle ne touche pas au binding : elle prepare juste les valeurs initiales du formulaire.
- `submitForm` ne contient aucun `req.getParameter` : la declaration des parametres **est** le binding. `name` et `version` sont des `String` locaux, comme n'importe quel parametre de methode Java.
- `Map<String, Object>` en valeur de retour suffit : `@WebApi` passe le resultat a `JsonSerializer`, qui sait serialiser une `Map`. Il n'est pas necessaire de creer une classe `Dto` pour une reponse de test.
- `LinkedHashMap` et non `HashMap` pour que les cles sortent dans l'ordre dans le JSON.

### 10. Lier un objet entier (`@MyRequestParam` sur un POJO)

Quand le type du parametre n'est pas un type simple, le framework construit l'objet et remplit ses champs.

**La decision simple / objet** est prise par `isSimpleType`, appele dans `resolveRequestParam` :

```java
private boolean isSimpleType(Class<?> type) {
    return type == String.class
            || type == CharSequence.class
            || type == Object.class
            || type.isPrimitive()
            || type.isEnum()
            || type == Boolean.class
            || type == Character.class
            || Number.class.isAssignableFrom(type);
}
```

Tout ce qui n'est pas dans cette liste est considere comme un objet metier : `resolveRequestParam` appelle alors `bindObject` au lieu de `convertParamValue`.

```java
private Object resolveRequestParam(Parameter parameter, String name, HttpServletRequest req)
        throws ServletException {
    Class<?> targetType = parameter.getType();

    if (isSimpleType(targetType)) {
        String value = req.getParameter(name);
        if (value == null) {
            throw new ServletException("Paramètre manquant : " + name + describe(parameter));
        }
        return convertParamValue(value, targetType, name);
    }

    return bindObject(targetType, new LinkedHashSet<>(List.of(name)), req, parameter, 0);
}
```

**Explication** : c'est cette bifurcation qui remplace l'ancien `throw` "type non supporte" de la section 6. Un parametre `String` absent reste une erreur, mais un parametre `User` absent produit un objet vide : c'est logique, car un formulaire n'envoie pas un objet, il envoie des champs, et un champ non envoye doit rester `null`.

**`bindObject` : deux conventions de nommage** :

```java
private Object bindObject(Class<?> targetType, Set<String> prefixes, HttpServletRequest req, Parameter parameter,
        int depth) throws ServletException {
    if (depth > MAX_BINDING_DEPTH) {
        throw new ServletException("Paramètre " + prefixes.iterator().next()
                + " : imbrication d'objets trop profonde (max " + MAX_BINDING_DEPTH + ")" + describe(parameter));
    }

    Object instance = instantiate(targetType, prefixes.iterator().next(), parameter);

    for (Field field : targetType.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic()) {
            continue;
        }

        String fieldName = field.getName();
        Set<String> candidates = new LinkedHashSet<>();
        for (String prefix : prefixes) {
            candidates.add(prefix + "." + fieldName);
        }
        candidates.add(fieldName);

        String value = null;
        String label = candidates.iterator().next();
        for (String candidate : candidates) {
            value = req.getParameter(candidate);
            if (value != null) {
                label = candidate;
                break;
            }
        }

        if (value != null) {
            setField(field, instance, convertFieldValue(value, field.getType(), label), label);
        } else if (!isSimpleType(field.getType()) && hasDefaultConstructor(field.getType())) {
            setField(field, instance, bindObject(field.getType(), trim(candidates), req, parameter, depth + 1),
                    label);
        }
    }
    return instance;
}
```

**Explication** :
- `prefixes` est un ensemble **ordonne** de noms possibles pour l'objet courant. Au premier niveau il contient le seul nom de l'annotation. Pour chaque champ, on construit la liste des candidats : `prefixe.champ` pour chaque prefixe, puis le nom nu du champ en dernier recours.
- `@MyRequestParam("user") User user` accepte donc `<input name="user.name">` **et** `<input name="name">`, et le prefixe est toujours prioritaire. C'est utile quand deux objets du meme formulaire ont tous les deux un champ `name` : `<input name="user.name">` et `<input name="client.name">` ne se melangent jamais.
- la liste de candidats est **transmise a l'objet imbrique** via `trim(candidates)`, donc l'imbrication continue de fonctionner dans les deux styles : `user.address.city`, `address.city` ou `city`.
- `MAX_BINDING_PREFIXES = 4` borne la taille de la liste transmise. Sans cela, le nombre de candidats croiserait a chaque niveau de profondeur.
- les champs `static`, `final` et synthetiques sont ignores. Un `final` ne peut pas etre modifie par reflexion, il faudrait le mettre a jour depuis le constructeur.
- si un champ n'a aucune valeur et que son type est lui-meme un objet **avec** un constructeur vide, `bindObject` est rappele : l'objet imbrique est cree meme si rien n'a ete envoye. Il reste alors avec tous ses champs a `null` / `0`.
- si le type du champ n'a **pas** de constructeur vide, le champ est laisse a `null` au lieu de leve une erreur : un objet imbrique optionnel ne doit pas casser la requete.

**Les troisfonctions de support** :

```java
private Object instantiate(Class<?> targetType, String name, Parameter parameter) throws ServletException {
    try {
        Constructor<?> constructor = targetType.getDeclaredConstructor();
        if (!Modifier.isPublic(constructor.getModifiers())) {
            constructor.setAccessible(true);
        }
        return constructor.newInstance();
    } catch (NoSuchMethodException e) {
        throw new ServletException("Paramètre " + name + " : la classe " + targetType.getName()
                + " doit avoir un constructeur vide pour etre liée" + describe(parameter), e);
    } catch (Exception e) {
        throw new ServletException("Paramètre " + name + " : impossible d'instancier " + targetType.getName()
                + describe(parameter), e);
    }
}

private boolean hasDefaultConstructor(Class<?> type) {
    try {
        type.getDeclaredConstructor();
        return true;
    } catch (NoSuchMethodException e) {
        return false;
    }
}

private Object convertFieldValue(String value, Class<?> fieldType, String label) throws ServletException {
    if (!isSimpleType(fieldType)) {
        throw new ServletException("Paramètre " + label + " : impossible de convertir \"" + value + "\" en "
                + fieldType.getName() + ". Pour un objet, utilisez un préfixe (ex. " + label + ".*)");
    }
    return convertParamValue(value, fieldType, label);
}
```

**Explication** :
- **`instantiate`** exige un constructeur vide. C'est la seule contrainte imposee aux classes liees. `getDeclaredConstructor()` + `setAccessible(true)` permet a une classe avec un constructeur prive d'etre liee quand meme, comme le fait deja `ApplicationContext.instantiateBeans` pour les beans.
- **`hasDefaultConstructor`** est le test qui evite l'erreur ci-dessus pour les objets imbriques optionnels.
- **`convertFieldValue`** fait une **seule** chose de plus que `convertParamValue` : interdire le cas `envoyer "Tana" pour un champ de type Address`. Le framework ne peut pas deviner comment construire un objet depuis une seule chaine, il le dit clairement au lieu de passer `null`.
- **`setField`** encapsule le `field.set(...)` qui leve `IllegalAccessException`, exception declaree, donc non gerable par le `catch` de la section 6.

**Le garde-fou anti-boucle** : `MAX_BINDING_DEPTH = 32`. Une classe qui se contient elle-meme (`class Node { Node next; }`) ferait une recursion infinie puisque `Node` a bien un constructeur vide. La profondeur est donc comptee, et le depassement leve une exception nommant le chemin complet plutot que de provoquer un `StackOverflowError` illisible.

### 11. Utilisation

Les objets lies ne sont pas des controleurs : ils sont dans le package `Dto`, hors du parametre `controllers-package`, pour que le conteneur IoC ne les instancie pas tout seul.

```java
// testmonjar/src/main/java/Dto/User.java
package Dto;

public class User {

    private String name;
    private int age;
    private boolean active;
    private Role role;
    private Address address;

    public User() {                 // constructeur vide obligatoire
    }

    // getters et setters ...
}
```

```java
// testmonjar/src/main/java/Dto/Address.java
package Dto;

public class Address {

    private String city;
    private int cp;

    public Address() {
    }

    // getters et setters ...
}
```

```java
// testmonjar/src/main/java/Controller/UserController.java
@WebApi
@UrlMapping(path = "/user/object", method = "POST")
public User setUserRepository(@MyRequestParam("user") User user) {
    return user;
}
```

L'annotation `@MyRequestParam` reste **obligatoire** sur un parametre objet. Un POJO sans annotation continue de recevoir `null` : la liaison reste explicite, et une faute de frappe dans la declaration d'un controleur ne peut pas creer silencieusement un objet vide.

### 12. Tester

```batch
:: 1. Compiler le framework (genere framework\lib\sprint1.jar, puis compile l'app)
cd framework
compile.bat

:: 2. Reconstruire et deployer le WAR de testmonjar
cd ..\testmonjar
deploy.bat

:: 3. Redemarrer Tomcat
```

```bash
:: afficher le formulaire
curl -i http://localhost:8080/s5/controller/user/form

:: envoyer le formulaire et lire le JSON
curl -i -X POST http://localhost:8080/s5/controller/user/form -d "name=lovapinto&version=sprint7"

:: le binding accepte l'URL aussi, pas seulement le body du formulaire
curl -i "http://localhost:8080/s5/controller/user/form?name=lovapinto&version=sprint7"
```

Attendu :

```
HTTP/1.1 200
Content-Type: application/json; charset=UTF-8

{"name":"lovapinto","version":"sprint7"}
```

Lier un objet entier :

```bash
:: prefixe sur chaque champ
curl -i -X POST http://localhost:8080/s5/controller/user/object ^
  -d "user.name=Alice&user.age=30&user.active=on&user.role=admin&user.address.city=Tana&user.address.cp=101"

:: nom nu, accepte aussi
curl -i -X POST http://localhost:8080/s5/controller/user/object -d "name=Bob&age=7"

:: prefixe melange : imbrication par prefixe et par nom nu
curl -i -X POST http://localhost:8080/s5/controller/user/object ^
  -d "user.name=Carol&address.city=Fianarantsoa&address.cp=301"
```

Attendu pour le premier appel :

```json
{"name":"Alice","age":30,"active":true,"role":"ADMIN",
 "address":{"city":"Tana","cp":101}}
```

Les cas d'erreur :

```bash
:: parametre absent -> HTTP 500, "Paramètre manquant : version"
curl -i -X POST http://localhost:8080/s5/controller/user/form -d "name=x"

:: valeur non numerique sur un int -> "impossible de convertir \"abc\" en int"
curl -i -X POST http://localhost:8080/s5/controller/api/test -d "name=x&age=abc"

:: depassement de capacite -> "hors limites"
curl -i -X POST http://localhost:8080/s5/controller/api/test -d "name=x&age=3000000000"

:: chaine envoyee pour un champ objet -> "Pour un objet, utilisez un préfixe"
curl -i -X POST http://localhost:8080/s5/controller/user/object -d "user.address=Tana"

:: classe sans constructeur vide -> "doit avoir un constructeur vide"
curl -i -X POST http://localhost:8080/s5/controller/user/object -d "user.role=ADMIN"
```

Aucune de ces erreurs ne renvoie de JSON : elles remontent en `ServletException` et Tomcat affiche une page d'erreur avec le message.

### 13. Pieges rencontres

**Le bouton de formulaire.** Une ecriture comme `button type="submit">Envoyer</button>` (chevrons d'ouverture oublies) n'est pas reconnue comme un bouton par le navigateur : le formulaire ne part jamais, et il n'y a aucune erreur visible dans la console du serveur.

**Le nom du `<input>`.** Le nom du champ HTML et le nom mis dans `@MyRequestParam` sont independants. Une faute de frappe ne provoque pas d'erreur de compilation, seulement un `Paramètre manquant` a l'execution.

**Les navigateurs envoient aussi des champs qu'on n'a pas demandes.** Certains navigateurs ajoutent des champs internes au formulaire. Comme seuls les parametres annotes sont lus, ces champs supplementaires sont simplement ignores : c'est un avantage du binding par annotation sur un `req.getParameterMap()` complet.

**Un DTO sans constructeur vide.** Les classes liees doivent avoir un constructeur vide, explicite ou implicite. `class User { User(String n) {} }` suffit a faire echouer toute la requete, alors que le programme compile tres bien.

**Un champ `final`.** Il est ignore silencieusement (`Modifier.isFinal`), donc une valeur envoyee pour ce champ disparait sans erreur. Les DTO ne doivent pas avoir de champ `final`.

**Les sous-objets sont toujours crees.** `User` a un champ `Address` : meme sans aucune donnee `address.*`, `user.getAddress()` renvoie un `Address` vide, pas `null`. Il faut tester le contenu, pas l'objet.

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
| `FrontServlet.java` | MODIFIE | Utilise le conteneur + branche JSON @WebApi + binding @MyRequestParam |
| `WebApi.java` | **NOUVEAU** | Annotation @WebApi (methode ou classe) |
| `JsonSerializer.java` | **NOUVEAU** | Serialisation reflective objet -> JSON |
| `MyRequestParam.java` | **NOUVEAU** | Annotation @MyRequestParam (parametre de methode) |
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
| `UserController.java` | MODIFIE | Utilise @Autowired + 2 routes @WebApi + binding @MyRequestParam |
| `UserRepository.java` | MODIFIE | Lit depuis MySQL |
| `ClassAController.java` | INCHANGE | Controller vide |
| `ApiController.java` | **NOUVEAU** | Controleur @WebApi au niveau classe |
| `ApiInfo.java` | **NOUVEAU** | Objet de transport serialise en JSON |
| `User.java` | **NOUVEAU** | DTO lie par binding (constructeur vide + champs) |
| `Address.java` | **NOUVEAU** | Sous-objet imbrique de User |
| `Role.java` | **NOUVEAU** | Enumeration liee par binding |
| `web.xml` | MODIFIE | Parametres DB + packages |
| `deploy.bat` | MODIFIE | Ajout driver MySQL |
| `users/list.jsp` | INCHANGE | Affiche la liste |
| `users/form.jsp` | **NOUVEAU** | Formulaire name/version, envoye en POST |
| `compile.bat` | MODIFIE | Ajout `pushd "%~dp0"` + suppression du `rmdir` de testmonjar |
