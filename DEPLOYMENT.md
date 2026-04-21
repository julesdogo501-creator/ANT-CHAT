# Guide d'Hébergement ANT CHAT 🚀

Ce document explique comment mettre en ligne votre application **ANT CHAT**.

## 1. Hébergement du Backend (Serveur Spring Boot)

Le backend a besoin d'un environnement Java 21 et d'une base de données MySQL.

### Option A : Render (Gratuit/Partagé)
1. Créez un compte sur [Render.com](https://render.com).
2. Créez un nouveau **Web Service**.
3. Liez votre dépôt GitHub.
4. **Runtime** : Docker (recommandé) ou Java.
5. **Build Command** : `./mvnw clean package -DskipTests`
6. **Start Command** : `java -jar target/server-0.0.1-SNAPSHOT.jar`
7. **Variables d'environnement** :
   - `SPRING_DATASOURCE_URL`: Votre URL MySQL (ex: Clever Cloud).
   - `SPRING_DATASOURCE_USERNAME`: Votre utilisateur DB.
   - `SPRING_DATASOURCE_PASSWORD`: Votre mot de passe DB.

### Option B : Clever Cloud (Déjà utilisé pour MySQL)
1. Créez une nouvelle application Java sur Clever Cloud.
2. Liez votre Git.
3. Configurez les variables d'environnement identiques à ci-dessus.

---

## 2. Hébergement de la Base de Données

Vous utilisez déjà **Clever Cloud**, ce qui est parfait pour MySQL. Assurez-vous que l'URL dans `application.properties` est correcte ou utilisez des variables d'environnement.

---

## 3. Hébergement du Frontend (Web)

Puisque le frontend est statique (HTML/CSS/JS), il peut être hébergé gratuitement n'importe où.

### GitHub Pages
1. Poussez votre projet sur GitHub.
2. Allez dans **Settings > Pages**.
3. Choisissez la branche `main` et le dossier `frontend`.
4. **Important** : Dans `app.js`, changez `const API_URL = 'http://localhost:8080/api'` par l'URL de votre backend Render/Clever Cloud.

### Netlify / Vercel
1. Glissez-déposez le dossier `frontend` sur leur interface.

---

## 4. Points de Vigilance ⚠️

1.  **CORS** : Dans `SecurityConfig.java`, assurez-vous que `allowedOrigins` contient l'URL de votre frontend (ou `*` pour tester).
2.  **WebSocket URL** : Dans `app.js`, modifiez l'URL de `SockJS` pour pointer vers votre serveur en ligne (ex: `https://mon-backend.onrender.com/ws`).
3.  **HTTPS** : Les navigateurs bloquent les requêtes vers un serveur `http` (non sécurisé) si le frontend est en `https`. Assurez-vous que tout est en `https`.
