package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Session;
import com.leasingdocument.backend.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public List<Session> getAllSessions() {
        return sessionRepository.findAll();
    }

    public Session getSessionById(Long id) {
        return sessionRepository.findById(id).orElse(null);
    }

    public Session saveSession(Session session) {
        return sessionRepository.save(session);
    }

    public void deleteSession(Long id) {
        sessionRepository.deleteById(id);
    }

    // Create a new login session
    public Session createLoginSession(
            String role,
            Long userId,
            Long deviceId
    ) {

        Session session = new Session();

        if ("ADMIN".equals(role)) {
            session.setAdminId(userId);

        } else if ("AGENT".equals(role)) {
            session.setAgentId(userId);
        }

        session.setDeviceId(deviceId);
        session.setLoginTime(LocalDateTime.now());
        session.setLogoutTime(null);
        session.setStatus("ACTIVE");

        return sessionRepository.save(session);
    }

    // Close current active session during logout
    public Session closeActiveSession(
            String role,
            Long userId,
            Long deviceId
    ) {

        List<Session> sessions =
                sessionRepository.findAll();

        Session activeSession = null;

        for (Session session : sessions) {

            boolean sameUser = false;

            if ("ADMIN".equals(role)) {

                sameUser =
                        userId.equals(session.getAdminId());

            } else if ("AGENT".equals(role)) {

                sameUser =
                        userId.equals(session.getAgentId());
            }

            boolean sameDevice =
                    deviceId.equals(session.getDeviceId());

            boolean isActive =
                    "ACTIVE".equals(session.getStatus());

            if (
                    sameUser &&
                            sameDevice &&
                            isActive
            ) {

                if (
                        activeSession == null ||
                                session.getLoginTime()
                                        .isAfter(activeSession.getLoginTime())
                ) {

                    activeSession = session;
                }
            }
        }

        if (activeSession != null) {

            activeSession.setLogoutTime(
                    LocalDateTime.now()
            );

            activeSession.setStatus(
                    "LOGGED_OUT"
            );

            return sessionRepository.save(
                    activeSession
            );
        }

        return null;
    }
}