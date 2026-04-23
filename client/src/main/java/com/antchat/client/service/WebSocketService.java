package com.antchat.client.service;

import com.antchat.client.model.ChatMessageRequest;
import com.antchat.client.model.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class WebSocketService {

    // ─── Configuration ──────────────────────────────────────────────────────
    private final String url = "wss://ant-chat-production.up.railway.app/ws/websocket";

    /** Délai initial entre tentatives (ms) */
    private static final long INITIAL_DELAY_MS   = 5_000;
    /** Facteur multiplicateur du délai à chaque échec */
    private static final long BACKOFF_MULTIPLIER  = 2;
    /** Délai maximum entre tentatives (ms) */
    private static final long MAX_DELAY_MS        = 60_000;

    // ─── État de la connexion ────────────────────────────────────────────────
    private volatile StompSession          stompSession;
    private volatile boolean               intentionalDisconnect = false;
    /** true uniquement lors des reconnexions (pas au premier connect) */
    private volatile boolean               isReconnecting        = false;
    private final    ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    /** Délai courant pour le prochain backoff */
    private long currentDelayMs = INITIAL_DELAY_MS;

    // ─── Paramètres mémorisés pour la reconnexion ───────────────────────────
    private Long              savedUserId;
    private Consumer<Message> savedOnPrivate;
    private Consumer<Message> savedOnGroup;

    /** Souscriptions de groupes en attente (avant 1ère connexion) */
    private final CopyOnWriteArrayList<PendingGroupSubscription> pendingGroupSubs =
            new CopyOnWriteArrayList<>();

    /** Souscriptions actives à rejouer après reconnexion (UNIQUEMENT après drop de connexion) */
    private final CopyOnWriteArrayList<PendingGroupSubscription> activeGroupSubs =
            new CopyOnWriteArrayList<>();

    private record PendingGroupSubscription(Long groupId, Consumer<Message> handler) {}

    // ─── API publique ────────────────────────────────────────────────────────

    /**
     * Lance la connexion initiale et mémorise les paramètres pour la reconnexion.
     */
    public void connect(Long userId, Consumer<Message> onPrivate, Consumer<Message> onGroup) {
        if (isConnected()) {
            disconnect();
        }
        this.savedUserId        = userId;
        this.savedOnPrivate     = onPrivate;
        this.savedOnGroup       = onGroup;
        this.intentionalDisconnect = false;
        this.isReconnecting     = false; // première connexion
        doConnect();
    }

    /**
     * Déconnexion propre — stoppe la reconnexion automatique.
     */
    public void disconnect() {
        intentionalDisconnect = true;
        scheduler.shutdownNow();
        if (stompSession != null && stompSession.isConnected()) {
            try { stompSession.disconnect(); } catch (Exception ignored) {}
        }
        stompSession = null;
        System.out.println("🔌 Déconnexion WebSocket volontaire.");
    }

    public void sendPrivateMessage(Long senderId, Long receiverId, String content) {
        sendPrivateMessage(senderId, receiverId, content, null, null);
    }

    public void sendPrivateMessage(Long senderId, Long receiverId,
                                   String content, String fileUrl, String fileType) {
        if (!isConnected()) {
            System.err.println("⚠️ Impossible d'envoyer (privé) : non connecté.");
            return;
        }
        stompSession.send("/app/chat.privateMessage",
                new ChatMessageRequest(content, senderId, receiverId, fileUrl, fileType));
    }

    public void subscribeToGroup(Long groupId, Consumer<Message> onGroup) {
        PendingGroupSubscription sub = new PendingGroupSubscription(groupId, onGroup);
        if (isConnected()) {
            doSubscribeToGroup(stompSession, sub);
        } else {
            System.out.println("⏳ Groupe " + groupId + " mis en attente de connexion WebSocket");
            pendingGroupSubs.add(sub);
        }
    }

    public void sendGroupMessage(Long senderId, Long groupId,
                                 String content, String fileUrl, String fileType) {
        if (!isConnected()) {
            System.err.println("⚠️ Impossible d'envoyer (groupe) : non connecté.");
            return;
        }
        stompSession.send("/app/chat.groupMessage",
                new ChatMessageRequest(content, senderId, null, groupId, fileUrl, fileType));
    }

    public boolean isConnected() {
        return stompSession != null && stompSession.isConnected();
    }

    // ─── Logique interne ─────────────────────────────────────────────────────

    private void doConnect() {
        if (intentionalDisconnect) return;

        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(wsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setInboundMessageSizeLimit(10 * 1024 * 1024); // 10 MB

        System.out.println("📡 Connexion WebSocket → " + url
                + (currentDelayMs == INITIAL_DELAY_MS ? "" : " (retry backoff=" + currentDelayMs / 1000 + "s)"));

        stompClient.connect(url, new StompSessionHandlerAdapter() {

            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                stompSession   = session;
                currentDelayMs = INITIAL_DELAY_MS; // reset backoff après succès
                System.out.println("✅ WebSocket CONNECTÉ (session=" + session.getSessionId()
                        + (isReconnecting ? " [RECONNEXION]" : "") + ")");

                // Souscription canal privé
                session.subscribe("/topic/private/" + savedUserId, frameHandler(savedOnPrivate));

                if (isReconnecting) {
                    // Reconnexion : rejouer les souscriptions groupes actives mémorisées
                    for (PendingGroupSubscription s : activeGroupSubs) {
                        System.out.println("🔁 Replay souscription groupe ID=" + s.groupId());
                        session.subscribe("/topic/group/" + s.groupId(), frameHandler(s.handler()));
                    }
                } else {
                    // Première connexion : traiter la file d'attente pendante
                    for (PendingGroupSubscription s : pendingGroupSubs) {
                        doSubscribeToGroup(session, s);
                    }
                    pendingGroupSubs.clear();
                }
            }

            @Override
            public void handleException(StompSession session, StompCommand cmd,
                                        StompHeaders headers, byte[] payload, Throwable ex) {
                System.err.println("❌ Erreur STOMP : " + ex.getMessage());
            }

            @Override
            public void handleTransportError(StompSession session, Throwable ex) {
                System.err.println("❌ Transport WebSocket fermé : " + ex.getMessage());
                stompSession    = null;
                isReconnecting  = true; // les prochaines tentatives sont des reconnexions
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (intentionalDisconnect) return;

        long delay = currentDelayMs;
        // Exponentiel avec plafond
        currentDelayMs = Math.min(currentDelayMs * BACKOFF_MULTIPLIER, MAX_DELAY_MS);

        System.out.println("🔁 Reconnexion dans " + delay / 1000 + "s...");
        scheduler.schedule(this::doConnect, delay, TimeUnit.MILLISECONDS);
    }

    private void doSubscribeToGroup(StompSession session, PendingGroupSubscription sub) {
        System.out.println("📡 Souscription groupe ID=" + sub.groupId());
        session.subscribe("/topic/group/" + sub.groupId(), frameHandler(sub.handler()));
        // Mémoriser pour rejouer après une reconnexion
        if (activeGroupSubs.stream().noneMatch(s -> s.groupId().equals(sub.groupId()))) {
            activeGroupSubs.add(sub);
        }
    }

    private StompFrameHandler frameHandler(Consumer<Message> callback) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) { return Message.class; }
            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                callback.accept((Message) payload);
            }
        };
    }
}
