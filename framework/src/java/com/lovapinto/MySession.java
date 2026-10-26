package com.lovapinto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MySession {

    // Identifiant unique de la session
    private final String id;

    // Données stockées dans la session
    private final Map<String, Object> attributes =
            new ConcurrentHashMap<>();

    // Date de création de la session
    private final long creationTime;

    // Date de la dernière utilisation
    private volatile long lastAccessedTime;

    // Durée maximale d'inactivité en secondes (30 minutes)
    private volatile int maxInactiveInterval = 1800;

    // État de la session
    private volatile boolean active = true;

    // Constructeur : crée une nouvelle session
    public MySession() {
        this.id = UUID.randomUUID().toString();
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = creationTime;
    }

    // Retourne l'identifiant de la session
    public String getId() {
        return id;
    }

    // Enregistre une donnée dans la session
    public synchronized void setAttribute(
            String key, Object value) {

        checkValid();

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                "La clé ne peut pas être vide"
            );
        }

        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    // Récupère une donnée de la session
    public Object getAttribute(String key) {
        checkValid();
        return attributes.get(key);
    }

    // Supprime une donnée de la session
    public void removeAttribute(String key) {
        checkValid();
        attributes.remove(key);
    }

    // Retourne les noms des données stockées
    public Set<String> getAttributeNames() {
        checkValid();
        return Set.copyOf(attributes.keySet());
    }

    // Retourne la date de création
    public long getCreationTime() {
        return creationTime;
    }

    // Retourne la date de dernière utilisation
    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    // Met à jour la date de dernière utilisation
    public void updateLastAccessedTime() {
        checkValid();
        lastAccessedTime = System.currentTimeMillis();
    }

    // Définit la durée maximale d'inactivité
    public void setMaxInactiveInterval(int seconds) {
        checkValid();

        if (seconds < 0) {
            throw new IllegalArgumentException(
                "La durée ne peut pas être négative"
            );
        }

        maxInactiveInterval = seconds;
    }

    // Retourne la durée maximale d'inactivité
    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    // Vérifie si la session est encore valide
    public boolean isValid() {
        if (!active) {
            return false;
        }

        // Une durée de zéro signifie expiration immédiate
        if (maxInactiveInterval == 0
                || System.currentTimeMillis()
                - lastAccessedTime
                >= maxInactiveInterval * 1000L) {
            invalidate();
            return false;
        }

        return active;
    }

    // Détruit la session et efface ses données
    public synchronized void invalidate() {
        attributes.clear();
        active = false;
    }

    // Vérifie la validité avant toute opération
    private void checkValid() {
        if (!isValid()) {
            throw new IllegalStateException(
                "La session est invalide ou expirée"
            );
        }
    }
}
