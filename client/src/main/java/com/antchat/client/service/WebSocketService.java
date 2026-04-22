package com.antchat.client.service;

import com.antchat.client.model.ChatMessageRequest;
import com.antchat.client.model.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class WebSocketService {
    private StompSession stompSession;
    private final String url = "wss://ant-chat-production.up.railway.app/ws/websocket";

    // File d'attente pour les souscriptions de groupes arrivées avant la connexion
    private final CopyOnWriteArrayList<PendingGroupSubscription> pendingGroupSubs = new CopyOnWriteArrayList<>();

    private record PendingGroupSubscription(Long groupId, Consumer<Message> handler) {}

    public void connect(Long currentUserId, Consumer<Message> onPrivateMessage, Consumer<Message> onGroupMessage) {
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setInboundMessageSizeLimit(10 * 1024 * 1024); // 10MB

        System.out.println("📡 Tentative de connexion WebSocket sur : " + url);
        stompClient.connect(url, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                stompSession = session;
                System.out.println("✅ CONNECTÉ AU SERVEUR ! (Session: " + session.getSessionId() + ")");

                System.out.println("📡 Souscription au canal Privé (ID=" + currentUserId + ")...");
                session.subscribe("/topic/private/" + currentUserId, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) { return Message.class; }
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        onPrivateMessage.accept((Message) payload);
                    }
                });

                // Rejouer les souscriptions de groupes qui attendaient la connexion
                for (PendingGroupSubscription pending : pendingGroupSubs) {
                    doSubscribeToGroup(session, pending.groupId(), pending.handler());
                }
                pendingGroupSubs.clear();
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                System.err.println("❌ Erreur WebSocket: " + exception.getMessage());
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.err.println("❌ Erreur de transport WebSocket: " + exception.getMessage());
            }
        });
    }

    private void doSubscribeToGroup(StompSession session, Long groupId, Consumer<Message> onGroupMessage) {
        System.out.println("📡 Souscription au groupe ID=" + groupId);
        session.subscribe("/topic/group/" + groupId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return Message.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                onGroupMessage.accept((Message) payload);
            }
        });
    }

    public void sendPrivateMessage(Long senderId, Long receiverId, String content) {
        sendPrivateMessage(senderId, receiverId, content, null, null);
    }

    public void sendPrivateMessage(Long senderId, Long receiverId, String content, String fileUrl, String fileType) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("⚠️ Impossible d'envoyer : non connecté au WebSocket.");
            return;
        }
        ChatMessageRequest request = new ChatMessageRequest(content, senderId, receiverId, fileUrl, fileType);
        stompSession.send("/app/chat.privateMessage", request);
    }

    public void subscribeToGroup(Long groupId, Consumer<Message> onGroupMessage) {
        if (stompSession != null && stompSession.isConnected()) {
            // Session déjà active : souscription directe
            doSubscribeToGroup(stompSession, groupId, onGroupMessage);
        } else {
            // Session pas encore prête : mise en file d'attente
            System.out.println("⏳ Groupe " + groupId + " mis en attente de connexion WebSocket");
            pendingGroupSubs.add(new PendingGroupSubscription(groupId, onGroupMessage));
        }
    }

    public void sendGroupMessage(Long senderId, Long groupId, String content, String fileUrl, String fileType) {
        if (stompSession == null || !stompSession.isConnected()) return;
        ChatMessageRequest request = new ChatMessageRequest(content, senderId, null, groupId, fileUrl, fileType);
        stompSession.send("/app/chat.groupMessage", request);
    }
}
