package com.lovapinto;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.UUID;


public class SessionManager {
    
    private final Map<String, MySession> sessions =
            new ConcurrentHashMap<>();

    public MySession createSession() {
        MySession session = new MySession();

        sessions.put(session.getId(), session);

        return session;
    }

    public MySession getSession(String id) {
        return sessions.get(id);
    }
}