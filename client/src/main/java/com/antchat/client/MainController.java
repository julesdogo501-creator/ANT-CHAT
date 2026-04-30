package com.antchat.client;

import com.antchat.client.model.ChatGroup;
import com.antchat.client.model.ChatItem;
import com.antchat.client.model.Message;
import com.antchat.client.model.User;
import com.antchat.client.service.WebSocketService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainController {

    // --- FXML UI COMPONENTS ---
    @FXML private VBox authScreen, settingsScreen, emptyChatPlaceholder;
    @FXML private HBox chatScreen, messageInputArea;
    @FXML private TextField usernameField, passwordField, messageField, searchField;
    @FXML private TextField settingsUrlField, settingsUsernameField;
    @FXML private Label authMessage, myUsername, currentChatName, placeholderTabLbl;
    @FXML private Circle myAvatarCircle, chatHeaderAvatar, settingsAvatarPreview;
    @FXML private ListView<ChatItem> contactListView;
    @FXML private VBox messagesContainer;
    @FXML private ScrollPane messagesScrollPane;
    @FXML private Button tabDiscussionsBtn, tabGroupsBtn, tabDirectoryBtn, btnAddMember;

    // --- DATA MODELS & LISTS ---
    private User currentUser = null;
    private ChatItem selectedItem = null;

    private final ObservableList<ChatItem> allUsers = FXCollections.observableArrayList();   // Annuaire
    private final ObservableList<ChatItem> activeChats = FXCollections.observableArrayList(); // Discussions privées
    private final ObservableList<ChatItem> groupChats = FXCollections.observableArrayList();  // Groupes
    
    // --- CACHES ---
    private final Map<String, Image> imageCache = new ConcurrentHashMap<>();
    private final Map<String, Image> emojiCacheMap = new ConcurrentHashMap<>();
    
    private FilteredList<ChatItem> filteredData;
    private javafx.beans.value.ChangeListener<String> searchListener;
    private boolean isDirectoryMode = false;
    private boolean isGroupsMode = false;
    private boolean isSwitchingTab = false;

    // --- SERVICES & REPOSITORIES ---
    private final WebSocketService wsService = new WebSocketService();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String API_URL = "https://ant-chat-production.up.railway.app/api";

    // --- CACHES & HISTORIES ---
    private final Map<Long, VBox> privateHistories = new HashMap<>();
    private final Map<Long, VBox> groupHistories   = new HashMap<>();
    // Track des conversations dont l'historique HTTP a déjà été chargé
    private final java.util.Set<String> historyLoaded = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    // --- TEMP DATA ---
    private String pendingFileUrl  = null;
    private String pendingFileType = null;
    private double xOffset = 0, yOffset = 0;


    // =========================================================================
    // INITIALIZATION & AUTHENTICATION
    // =========================================================================

    @FXML public void onLogin() { authenticate("login"); }
    @FXML public void onRegister() { authenticate("register"); }

    private void authenticate(String action) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        if (username.isEmpty() || password.isEmpty()) return;

        new Thread(() -> {
            try {
                String jsonRequest = mapper.writeValueAsString(new User(username, password));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL + "/auth/" + action))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    if (action.equals("register")) {
                        Platform.runLater(() -> {
                            authMessage.setText("Compte créé ! Connexion...");
                            authMessage.setStyle("-fx-text-fill: #e879f9;");
                            authenticate("login");
                        });
                    } else {
                        currentUser = mapper.readValue(response.body(), User.class);
                        Platform.runLater(this::initChatUI);
                    }
                } else {
                    Platform.runLater(() -> {
                        authMessage.setText(response.body());
                        authMessage.setStyle("-fx-text-fill: #ef4444;");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> authMessage.setText("Erreur réseau : Vérifiez le serveur."));
            }
        }).start();
    }

    private void initChatUI() {
        authScreen.setVisible(false);
        chatScreen.setVisible(true);
        myUsername.setText(currentUser.getUsername());
        
        // Affichage de la photo de profil perso
        updateAvatar(myAvatarCircle, currentUser);

        // Configuration ListView
        setupListView();

        // Auto-scroll scrollpane
        messagesScrollPane.contentProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal instanceof VBox) {
                ((VBox) newVal).heightProperty().addListener((vobs, vold, vnew) -> messagesScrollPane.setVvalue(1.0));
            }
        });

        loadContacts();
        wsService.connect(currentUser.getId(), this::onPrivateMessageReceived, this::onGroupMessageReceived);
    }

    private void setupListView() {
        contactListView.setPlaceholder(new Label("Aucun élément trouvé 😕"));
        contactListView.setCellFactory(lv -> new ListCell<ChatItem>() {
            private final Circle avatar = new Circle(22);
            private final Label nameLabel = new Label();
            private final Label statusLabel = new Label();
            private final VBox textLayout = new VBox(2);
            private final HBox layout = new HBox(15);
            {
                nameLabel.getStyleClass().add("sidebar-item-title");
                statusLabel.getStyleClass().add("sidebar-item-status");
                layout.setAlignment(Pos.CENTER_LEFT);
                layout.setPadding(new Insets(10, 15, 10, 15));
                avatar.getStyleClass().add("avatar-glow");
                textLayout.getChildren().addAll(nameLabel, statusLabel);
                layout.getChildren().addAll(avatar, textLayout);
            }
            @Override
            protected void updateItem(ChatItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.getName());
                    if (item.isGroup()) {
                        statusLabel.setText("Groupe - " + item.getGroup().getMembers().size() + " membres");
                        updateAvatar(avatar, null);
                    } else {
                        User u = item.getUser();
                        statusLabel.setText(u.getId() == null ? "Chat ouvert à tous" : (u.isOnline() ? "En ligne" : "Hors ligne"));
                        updateAvatar(avatar, u);
                    }
                    setGraphic(layout);
                }
            }
        });

        contactListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isSwitchingTab) {
                Platform.runLater(() -> {
                    if (isDirectoryMode) {
                        ChatItem existing = activeChats.stream().filter(c -> c.equals(newVal)).findFirst().orElse(null);
                        if (existing == null) {
                            activeChats.add(0, newVal);
                            existing = newVal;
                        }
                        onTabDiscussions();
                        contactListView.getSelectionModel().select(existing);
                    } else {
                        selectChat(newVal);
                    }
                });
            }
        });
    }

    // =========================================================================
    // NAVIGATION & SEARCH
    // =========================================================================

    @FXML public void onTabDiscussions() { switchTab("DISCUSSIONS"); }
    @FXML public void onTabGroups() { switchTab("GROUPS"); }
    @FXML public void onOpenDirectory() { switchTab("DIRECTORY"); }

    private void switchTab(String mode) {
        isSwitchingTab = true;
        contactListView.getSelectionModel().clearSelection();
        
        isDirectoryMode = mode.equals("DIRECTORY");
        isGroupsMode = mode.equals("GROUPS");

        tabDiscussionsBtn.getStyleClass().remove("nav-active");
        tabGroupsBtn.getStyleClass().remove("nav-active");
        tabDirectoryBtn.getStyleClass().remove("nav-active");

        ObservableList<ChatItem> source;
        switch (mode) {
            case "GROUPS":
                tabGroupsBtn.getStyleClass().add("nav-active");
                source = groupChats;
                contactListView.setPlaceholder(new Label("Aucun groupe."));
                break;
            case "DIRECTORY":
                tabDirectoryBtn.getStyleClass().add("nav-active");
                source = allUsers;
                contactListView.setPlaceholder(new Label("Annuaire vide."));
                break;
            default:
                tabDiscussionsBtn.getStyleClass().add("nav-active");
                source = activeChats;
                contactListView.setPlaceholder(new Label("Aucune discussion."));
                break;
        }

        updateListViewSource(source);
        contactListView.setVisible(true);
        placeholderTabLbl.setVisible(false);
    }

    private void updateListViewSource(ObservableList<ChatItem> source) {
        if (searchListener != null) searchField.textProperty().removeListener(searchListener);
        filteredData = new FilteredList<>(source, p -> true);
        searchListener = (obs, oldV, newV) -> filteredData.setPredicate(item -> 
            newV == null || newV.isEmpty() || item.getName().toLowerCase().contains(newV.toLowerCase()));
        
        Platform.runLater(() -> {
            isSwitchingTab = false;
            searchField.textProperty().addListener(searchListener);
            contactListView.setItems(filteredData);
            contactListView.refresh();
        });
    }

    private void selectChat(ChatItem item) {
        this.selectedItem = item;
        messageInputArea.setVisible(true);
        messagesScrollPane.setVisible(true);
        emptyChatPlaceholder.setVisible(false);
        btnAddMember.setVisible(item.isGroup());

        VBox historyBox;
        if (item.isGroup()) {
            ChatGroup g = item.getGroup();
            currentChatName.setText(g.getName());
            updateAvatar(chatHeaderAvatar, null);
            VBox vb = new VBox(10); vb.setId("HistoryGroup_" + g.getId());
            groupHistories.putIfAbsent(g.getId(), vb);
            historyBox = groupHistories.get(g.getId());
            String key = "group_" + g.getId();
            if (historyLoaded.add(key)) { // add() retourne false si déjà présent
                fetchAndDisplayHistory("/groups/" + g.getId() + "/history", historyBox);
            }
        } else {
            User u = item.getUser();
            currentChatName.setText(u.getUsername());
            updateAvatar(chatHeaderAvatar, u);
            VBox vb = new VBox(10); vb.setId("HistoryPrivate_" + u.getId());
            privateHistories.putIfAbsent(u.getId(), vb);
            historyBox = privateHistories.get(u.getId());
            String key = "private_" + u.getId();
            if (historyLoaded.add(key)) { // add() retourne false si déjà présent
                fetchAndDisplayHistory("/users/history/private/" + currentUser.getId() + "/" + u.getId(), historyBox);
            }
        }
        historyBox.getStyleClass().add("messages-container-neon");
        messagesScrollPane.setContent(historyBox);
    }

    private void fetchAndDisplayHistory(String endpoint, VBox container) {
        new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(API_URL + endpoint)).GET().build();
                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    List<Message> history = mapper.readValue(res.body(), new TypeReference<List<Message>>() {});

                    // Créer tous les noeuds UI dans le thread d'arrière-plan
                    List<javafx.scene.Node> nodes = new java.util.ArrayList<>();
                    for (Message m : history) {
                        javafx.scene.Node node = createMessageNode(m);
                        if (node != null) nodes.add(node);
                    }

                    if (!nodes.isEmpty()) {
                        // Ajouter TOUS les noeuds en un seul Platform.runLater (évite les NPE de ScenePulse)
                        Platform.runLater(() -> {
                            container.getChildren().setAll(nodes);
                            if (messagesScrollPane.getContent() == container) {
                                messagesScrollPane.setVvalue(1.0);
                            }
                        });
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // =========================================================================
    // DATA LOADING & GROUPS
    // =========================================================================

    private void loadContacts() {
        new Thread(() -> {
            try {
                // Fetch Users
                HttpRequest uReq = HttpRequest.newBuilder().uri(URI.create(API_URL + "/users")).GET().build();
                List<User> users = mapper.readValue(httpClient.send(uReq, HttpResponse.BodyHandlers.ofString()).body(), new TypeReference<List<User>>() {});
                
                // Fetch Groups
                HttpRequest gReq = HttpRequest.newBuilder().uri(URI.create(API_URL + "/groups/user/" + currentUser.getId())).GET().build();
                List<ChatGroup> groups = mapper.readValue(httpClient.send(gReq, HttpResponse.BodyHandlers.ofString()).body(), new TypeReference<List<ChatGroup>>() {});

                Platform.runLater(() -> {
                    java.util.List<ChatItem> newAllUsers = new java.util.ArrayList<>();
                    java.util.List<ChatItem> newActiveChats = new java.util.ArrayList<>();
                    java.util.List<ChatItem> newGroupChats = new java.util.ArrayList<>();

                    for (User u : users) {
                        if (u.getId().equals(currentUser.getId())) continue;
                        ChatItem item = new ChatItem(u);
                        newAllUsers.add(item);
                        if ("AntIA".equals(u.getUsername())) newActiveChats.add(item);
                    }

                    for (ChatGroup group : groups) {
                        ChatItem item = new ChatItem(group);
                        newGroupChats.add(item);
                        wsService.subscribeToGroup(group.getId(), this::onGroupMessageReceived);
                    }
                    
                    // Avoid IndexOutOfBounds in FilteredList by substituting full contents safely
                    if (searchListener != null) searchField.textProperty().removeListener(searchListener);
                    allUsers.setAll(newAllUsers);
                    activeChats.setAll(newActiveChats);
                    groupChats.setAll(newGroupChats);
                    
                    onTabDiscussions();
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML public void onNewGroup() {
        TextInputDialog dialog = new TextInputDialog("Mon Nouveau Groupe");
        dialog.setTitle("Nouveau Groupe");
        dialog.setHeaderText("Création d'un espace de discussion");
        dialog.setContentText("Nom du groupe :");
        
        // Fix: Toujours définir le propriétaire pour éviter les conflits de Pulse JavaFX
        if (chatScreen.getScene() != null) {
            dialog.initOwner(chatScreen.getScene().getWindow());
        }

        dialog.showAndWait().ifPresent(name -> {
            // Fix: Ne JAMAIS bloquer le thread FX avec httpClient.send()
            String encodedName;
            try { encodedName = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8); }
            catch (Exception ex) { encodedName = name.replace(" ", "%20"); }
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/groups?name=" + encodedName + "&adminId=" + currentUser.getId()))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() == 200) {
                        try {
                            ChatGroup group = mapper.readValue(res.body(), ChatGroup.class);
                            Platform.runLater(() -> {
                                ChatItem item = new ChatItem(group);
                                groupChats.add(0, item);
                                wsService.subscribeToGroup(group.getId(), this::onGroupMessageReceived);
                                onTabGroups();
                                selectChat(item);
                            });
                        } catch (Exception e) {}
                    }
                });
        });
    }

    @FXML public void onAddMember() {
        if (selectedItem == null || !selectedItem.isGroup()) return;
        VBox overlay = new VBox(10);
        overlay.setStyle("-fx-background-color: rgba(20, 20, 25, 0.95); -fx-padding: 20; -fx-background-radius: 15;");
        overlay.setPrefSize(300, 400);
        Label title = new Label("Inviter un membre");
        title.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");
        
        ListView<ChatItem> list = new ListView<>(allUsers);
        list.setPlaceholder(new Label("Aucun autre utilisateur trouvé."));
        list.setStyle("-fx-background-color: #12121a; -fx-control-inner-background: #12121a; -fx-background-insets: 0; -fx-border-color: rgba(255,255,255,0.1);");
        list.setCellFactory(lv -> new ListCell<ChatItem>() {
            private final Circle avatar = new Circle(14);
            private final Label name = new Label();
            private final HBox layout = new HBox(10, avatar, name);
            { layout.setAlignment(Pos.CENTER_LEFT); layout.setPadding(new Insets(5, 10, 5, 10)); name.setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }
            @Override
            protected void updateItem(ChatItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    name.setText(item.getName());
                    updateAvatar(avatar, item.getUser());
                    setGraphic(layout);
                    setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                }
            }
        });
        Button close = new Button("Fermer");
        close.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: white; -fx-cursor: hand;");
        overlay.getChildren().addAll(title, list, close);
        
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.getContent().add(overlay);
        popup.setAutoHide(true);
        if (btnAddMember.getScene() != null) {
            popup.show(btnAddMember.getScene().getWindow());
        }

        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(API_URL + "/groups/" + selectedItem.getGroup().getId() + "/members/" + newV.getUser().getId())).POST(HttpRequest.BodyPublishers.noBody()).build();
                    httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
                    popup.hide();
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
        close.setOnAction(e -> popup.hide());
    }

    // =========================================================================
    // MESSAGING & WEBSOCKET
    // =========================================================================

    @FXML public void onSendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty() && pendingFileUrl == null) return;
        
        // Si on envoie un fichier sans texte, on met un placeholder pour le contenu
        if (text.isEmpty() && pendingFileUrl != null) {
            text = "[Fichier]";
        }

        System.out.println("DEBUG: Envoi de message -> " + text + " (File: " + pendingFileUrl + ")");
        if (selectedItem != null) {
            if (selectedItem.isGroup()) {
                wsService.sendGroupMessage(currentUser.getId(), selectedItem.getGroup().getId(), text, pendingFileUrl, pendingFileType);
            } else {
                wsService.sendPrivateMessage(currentUser.getId(), selectedItem.getUser().getId(), text, pendingFileUrl, pendingFileType);
            }
        }
        messageField.clear(); 
        messageField.setPromptText("Écrivez votre message...");
        pendingFileUrl = null; 
        pendingFileType = null;
    }

    private void onPrivateMessageReceived(Message msg) {
        if (msg.getSender() == null || currentUser == null) return;
        Platform.runLater(() -> {
            Long myId = currentUser.getId();
            Long senderId = msg.getSender().getId();
            Long receiverId = (msg.getReceiver() != null) ? msg.getReceiver().getId() : null;
            
            Long peerId = senderId.equals(myId) ? receiverId : senderId;
            if (peerId == null) return;

            VBox vb = privateHistories.get(peerId);
            if (vb == null) {
                vb = new VBox(10);
                vb.setId("HistoryPrivate_" + peerId);
                vb.getStyleClass().add("messages-container-neon");
                privateHistories.put(peerId, vb);
            }
            addMessageUI(msg, vb);
        });
    }

    private void onGroupMessageReceived(Message msg) {
        if (msg.getChatGroup() == null) return;
        Platform.runLater(() -> {
            Long groupId = msg.getChatGroup().getId();
            VBox vb = groupHistories.get(groupId);
            if (vb == null) {
                vb = new VBox(10);
                vb.setId("HistoryGroup_" + groupId);
                vb.getStyleClass().add("messages-container-neon");
                groupHistories.put(groupId, vb);
            }
            addMessageUI(msg, vb);
        });
    }

    private void addMessageUI(Message msg, VBox container) {
        if (msg.getSender() == null || container == null) return;
        
        javafx.scene.Node node = createMessageNode(msg);
        if (node != null) {
            container.getChildren().add(node);
            if (messagesScrollPane.getContent() == container) {
                messagesScrollPane.setVvalue(1.0);
            }
        }
    }

    private javafx.scene.Node createMessageNode(Message msg) {
        if (msg.getSender() == null) return null;
        
        boolean isMe = currentUser != null && msg.getSender().getId().equals(currentUser.getId());
        HBox wrapper = new HBox(10);
        wrapper.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        wrapper.setPadding(new Insets(5, 10, 5, 10));

        // Utilisation d'un ImageView avec un clip au lieu de Circle/ImagePattern pour la stabilité
        ImageView avatarIv = new ImageView();
        avatarIv.setFitWidth(30);
        avatarIv.setFitHeight(30);
        Rectangle clip = new Rectangle(30, 30);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        avatarIv.setClip(clip);
        updateAvatarImageView(avatarIv, msg.getSender());

        VBox content = new VBox(5);
        content.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (msg.getFileUrl() != null && !msg.getFileUrl().isEmpty()) {
            try {
                String fileUrl = (msg.getFileUrl().startsWith("http") || msg.getFileUrl().startsWith("data:"))
                    ? msg.getFileUrl()
                    : "https://ant-chat-production.up.railway.app" + msg.getFileUrl();
                String fileType = msg.getFileType() != null ? msg.getFileType().toLowerCase() : "";
                String lowerUrl = fileUrl.toLowerCase();
                String fileName = fileUrl.contains("_") ? fileUrl.substring(fileUrl.lastIndexOf('_') + 1) : fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                if (fileName.length() > 28) fileName = fileName.substring(0, 25) + "...";

                boolean isImage = fileType.startsWith("image/") || lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".webp");

                if (isImage) {
                    System.out.println("DEBUG: Affichage image -> " + (fileUrl.length() > 100 ? fileUrl.substring(0, 50) + "..." : fileUrl));
                    ImageView iv = new ImageView();
                    iv.setFitWidth(220);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    iv.setCache(true);
                    iv.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 0, 2); -fx-cursor: hand;");
                    
                    loadSafeImage(iv, fileUrl, 600, content);
                    
                    final String finalUrl = fileUrl;
                    iv.setOnMouseClicked(e -> { 
                        if (!finalUrl.startsWith("data:")) {
                            try { java.awt.Desktop.getDesktop().browse(URI.create(finalUrl)); } catch (Exception ex) {} 
                        }
                    });
                    content.getChildren().add(iv);

                } else if (fileType.startsWith("audio/")) {
                    // --- Audio : lecteur compact ---
                    HBox audioBox = new HBox(8);
                    audioBox.setAlignment(Pos.CENTER_LEFT);
                    audioBox.setStyle("-fx-background-color: rgba(191,0,255,0.15); -fx-background-radius: 12; -fx-padding: 8 14 8 14; -fx-border-color: rgba(191,0,255,0.3); -fx-border-radius: 12;");
                    Label icon = new Label("🎵");
                    icon.setStyle("-fx-font-size: 18px;");
                    Label durLabel = new Label("Audio");
                    durLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11px;");
                    Button playBtn = new Button("▶");
                    playBtn.setStyle("-fx-background-color: #bf00ff; -fx-text-fill: white; -fx-background-radius: 50%; -fx-min-width: 30; -fx-min-height: 30; -fx-font-size: 12px; -fx-cursor: hand;");
                    final String audioFinal = fileUrl;
                    final javafx.scene.media.MediaPlayer[] playerRef = {null};
                    playBtn.setOnAction(e -> {
                        try {
                            if (playerRef[0] != null) {
                                playerRef[0].stop();
                                playerRef[0].dispose();
                                playerRef[0] = null;
                                playBtn.setText("▶");
                                return;
                            }
                            javafx.scene.media.Media media = new javafx.scene.media.Media(audioFinal);
                            playerRef[0] = new javafx.scene.media.MediaPlayer(media);
                            playerRef[0].setOnEndOfMedia(() -> Platform.runLater(() -> { playBtn.setText("▶"); playerRef[0] = null; }));
                            playerRef[0].play();
                            playBtn.setText("■");
                        } catch (Exception ex) {
                            try { java.awt.Desktop.getDesktop().browse(URI.create(audioFinal)); } catch (Exception ex2) {}
                        }
                    });
                    Label dlBtn = new Label("⬇");
                    dlBtn.setStyle("-fx-text-fill: #bf00ff; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0 0 0 8;");
                    dlBtn.setOnMouseClicked(e -> { try { java.awt.Desktop.getDesktop().browse(URI.create(audioFinal)); } catch (Exception ex) {} });
                    audioBox.getChildren().addAll(icon, playBtn, durLabel, dlBtn);
                    content.getChildren().add(audioBox);

                } else {
                    // --- Fichier générique : bulle avec icône et bouton télécharger ---
                    String fileIcon = fileType.contains("pdf") ? "📄" :
                                      fileType.contains("zip") || fileType.contains("compressed") || fileType.contains("rar") ? "🗜️" :
                                      fileType.contains("video") ? "🎬" :
                                      fileType.contains("text") || fileType.contains("txt") ? "📝" :
                                      fileType.contains("word") || fileType.contains("doc") ? "📘" :
                                      fileType.contains("excel") || fileType.contains("sheet") ? "📗" :
                                      fileType.contains("powerpoint") || fileType.contains("presentation") ? "📙" : "📎";
                    HBox fileBox = new HBox(10);
                    fileBox.setAlignment(Pos.CENTER_LEFT);
                    fileBox.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 12; -fx-padding: 10 16 10 16; -fx-border-color: rgba(255,255,255,0.15); -fx-border-radius: 12; -fx-cursor: hand;");
                    Label iconLbl = new Label(fileIcon);
                    iconLbl.setStyle("-fx-font-size: 22px;");
                    VBox fileInfo = new VBox(2);
                    Label nameLbl = new Label(fileName);
                    nameLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");
                    Label typeLbl = new Label(fileType.isEmpty() ? "fichier" : fileType);
                    typeLbl.setStyle("-fx-text-fill: #999; -fx-font-size: 10px;");
                    fileInfo.getChildren().addAll(nameLbl, typeLbl);
                    Label dlLbl = new Label("⬇");
                    dlLbl.setStyle("-fx-text-fill: #bf00ff; -fx-font-size: 18px; -fx-padding: 0 0 0 8;");
                    final String fileFinal = fileUrl;
                    fileBox.setOnMouseClicked(e -> { try { java.awt.Desktop.getDesktop().browse(URI.create(fileFinal)); } catch (Exception ex) {} });
                    fileBox.getChildren().addAll(iconLbl, fileInfo, new Region(), dlLbl);
                    HBox.setHgrow(fileInfo, Priority.ALWAYS);
                    content.getChildren().add(fileBox);
                }
            } catch (Exception e) {}
        }

        if (msg.getContent() != null && !msg.getContent().isEmpty() && !msg.getContent().startsWith("[Fichier")) {
            TextFlow flow = renderEmojiText(msg.getContent(), isMe ? "msg-bubble-me" : "msg-bubble-other");
            flow.setMaxWidth(350); 
            content.getChildren().add(flow);
        }

        if (isMe) wrapper.getChildren().addAll(content, avatarIv);
        else wrapper.getChildren().addAll(avatarIv, content);

        return wrapper;
    }

    // =========================================================================
    // UI HELPERS (AVATARS, EMOJIS, SETTINGS)
    // =========================================================================

    private void loadSafeImage(ImageView iv, String url, int requestedSize, Pane errorContainer) {
        if (url == null || url.isEmpty()) return;

        new Thread(() -> {
            try {
                Image img;
                if (url.startsWith("data:image")) {
                    try {
                        String base64 = url.substring(url.indexOf(",") + 1);
                        byte[] bytes = java.util.Base64.getDecoder().decode(base64);
                        img = new Image(new java.io.ByteArrayInputStream(bytes), requestedSize, requestedSize, true, true);
                    } catch (Exception e) {
                        System.err.println("Erreur decodage Base64: " + e.getMessage());
                        img = null;
                    }
                } else if (url.startsWith("file:")) {
                    img = new Image(url, requestedSize, requestedSize, true, true);
                } else {
                    img = new Image(url, requestedSize, requestedSize, true, true, false);
                }
                
                if (img != null && !img.isError()) {
                    final Image finalImg = img;
                    Platform.runLater(() -> {
                        try { iv.setImage(finalImg); } catch (Exception e) {}
                    });
                } else if (errorContainer != null) {
                    Platform.runLater(() -> {
                        String msg = (url.startsWith("data:")) ? "⚠️ Image corrompue" : "⚠️ Image non disponible";
                        Label err = new Label(msg);
                        err.setStyle("-fx-text-fill: #ff4444; -fx-font-size: 10px; -fx-background-color: rgba(0,0,0,0.3); -fx-padding: 5;");
                        errorContainer.getChildren().add(err);
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private String compressAndEncodeImage(File file) {
        try {
            javafx.scene.image.Image original = new javafx.scene.image.Image(file.toURI().toString());
            double w = original.getWidth();
            double h = original.getHeight();
            
            double max = 1024;
            if (w > max || h > max) {
                if (w > h) { h = (max / w) * h; w = max; }
                else { w = (max / h) * w; h = max; }
            }
            
            final double finalW = w;
            final double finalH = h;
            
            // Le snapshot DOIT être sur le thread JavaFX
            java.util.concurrent.CompletableFuture<javafx.scene.image.WritableImage> future = new java.util.concurrent.CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    ImageView tempIv = new ImageView(original);
                    tempIv.setFitWidth(finalW);
                    tempIv.setFitHeight(finalH);
                    javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                    params.setFill(javafx.scene.paint.Color.TRANSPARENT);
                    future.complete(tempIv.snapshot(params, null));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            
            javafx.scene.image.WritableImage resized = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(resized, null), "png", out);
            
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void updateAvatarImageView(ImageView iv, User user) {
        if (iv == null) return;
        loadSafeImage(iv, getSafeAvatarUrl(user), 60, null);
    }

    private void updateAvatar(Circle circle, User user) {
        if (circle == null) return;
        String url = getSafeAvatarUrl(user);

        // 1. Appliquer le cache immédiatement
        Image cached = imageCache.get(url);
        if (cached != null) {
            circle.setFill(new ImagePattern(cached));
            return;
        }

        // 2. Fallback
        if (!(circle.getFill() instanceof ImagePattern)) {
            setFallbackAvatar(circle, user);
        }

        if (url == null || url.isEmpty()) return;

        new Thread(() -> {
            try {
                Image img;
                if (url.startsWith("data:image")) {
                    String base64 = url.substring(url.indexOf(",") + 1);
                    byte[] bytes = java.util.Base64.getDecoder().decode(base64);
                    img = new Image(new java.io.ByteArrayInputStream(bytes), 120, 120, true, true);
                } else if (url.startsWith("file:")) {
                    img = new Image(url, 120, 120, true, true);
                } else {
                    img = new Image(url, 120, 120, true, true, false);
                }
                
                if (!img.isError()) {
                    imageCache.put(url, img);
                    Platform.runLater(() -> circle.setFill(new ImagePattern(img)));
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private String getSafeAvatarUrl(User user) {
        if (user == null) return "https://ui-avatars.com/api/?name=U&background=random";
        String url = user.getProfilePictureUrl();
        String name = user.getUsername() != null ? user.getUsername() : "User";
        
        if (url == null || url.isEmpty()) {
            try {
                return "https://ui-avatars.com/api/?name=" + java.net.URLEncoder.encode(name, "UTF-8") + "&background=random&color=fff";
            } catch (Exception e) {
                return "https://ui-avatars.com/api/?name=" + name.replace(" ", "+") + "&background=random";
            }
        }
        
        // Si c'est déjà une URL complète
        if (url.startsWith("http") || url.startsWith("file:") || url.startsWith("data:")) {
            return url;
        }
        
        if (url.startsWith("/uploads/")) {
            return "https://ant-chat-production.up.railway.app" + url;
        }
        
        return url;
    }

    private void setFallbackAvatar(Circle c, User u) {
        String name = (u != null) ? u.getUsername() : "G";
        int hash = Math.abs(name.hashCode());
        c.setFill(javafx.scene.paint.Color.rgb(hash % 100 + 155, (hash >> 8) % 100 + 100, (hash >> 16) % 100 + 155));
    }

    private TextFlow renderEmojiText(String content, String style) {
        TextFlow flow = new TextFlow();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("[\uD83C-\uDBFF\uDC00-\uDFFF]+|[^\uD83C-\uDBFF\uDC00-\uDFFF]+");
        java.util.regex.Matcher m = p.matcher(content);
        while (m.find()) {
            String part = m.group();
            if (part.matches("[\uD83C-\uDBFF\uDC00-\uDFFF]+")) {
                for (int i = 0; i < part.length(); ) {
                    int cp = part.codePointAt(i);
                    String hex = Integer.toHexString(cp).toLowerCase();
                    String emojiUrl = "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/" + hex + ".png";
                    
                    Image img = emojiCacheMap.computeIfAbsent(emojiUrl, u -> new Image(u, 20, 20, true, true, true));
                    ImageView iv = new ImageView(img);
                    
                    flow.getChildren().add(iv); 
                    i += Character.charCount(cp);
                }
            } else {
                Text t = new Text(part); t.getStyleClass().add(style); t.setFill(javafx.scene.paint.Color.WHITE); flow.getChildren().add(t);
            }
        }
        return flow;
    }

    @FXML public void onOpenEmojiPicker() {
        Platform.runLater(() -> {
            try { messageField.requestFocus(); Robot r = new Robot();
                r.keyPress(KeyEvent.VK_WINDOWS); r.keyPress(KeyEvent.VK_PERIOD);
                r.keyRelease(KeyEvent.VK_PERIOD); r.keyRelease(KeyEvent.VK_WINDOWS);
            } catch (Exception e) {}
        });
    }

    // (Méthode popup supprimée — fusionnée avec onOpenSettings FXML ci-dessous)

    @FXML public void onAttachMedia() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Joindre un fichier");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"),
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"),
            new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx", "*.xlsx", "*.txt"),
            new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.mkv", "*.avi"),
            new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.wav", "*.ogg")
        );
        File file = fc.showOpenDialog(authScreen.getScene().getWindow());
        if (file != null) {
            Platform.runLater(() -> {
                messageField.setText("");
                messageField.setPromptText("⬆ Envoi du fichier : " + file.getName() + "...");
                messageField.setDisable(true);
            });
            
            new Thread(() -> {
                try {
                    // Toujours passer par l'upload HTTP : évite les messages STOMP énormes (base64)
                    // qui échouent souvent ou dépassent les limites proxy / WebSocket.
                    uploadAndSend(file);
                } catch (Throwable e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        messageField.setDisable(false);
                        messageField.setPromptText("Échec de l'envoi");
                    });
                } finally {
                    Platform.runLater(() -> messageField.setDisable(false));
                }
            }).start();
        }
    }

    private String uploadFileSync(java.io.File file) throws Exception {
        String boundary = "---" + System.currentTimeMillis();
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        String mime = java.nio.file.Files.probeContentType(file.toPath());
        if (mime == null) {
            String name = file.getName().toLowerCase();
            if      (name.endsWith(".wav")) mime = "audio/wav";
            else if (name.endsWith(".mp3")) mime = "audio/mpeg";
            else if (name.endsWith(".ogg")) mime = "audio/ogg";
            else if (name.endsWith(".png")) mime = "image/png";
            else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) mime = "image/jpeg";
            else if (name.endsWith(".gif")) mime = "image/gif";
            else if (name.endsWith(".webp")) mime = "image/webp";
            else                            mime = "application/octet-stream";
        }
        String body  = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + file.getName() + "\"\r\nContent-Type: " + mime + "\r\n\r\n";
        byte[] start = body.getBytes();
        byte[] end   = ("\r\n--" + boundary + "--\r\n").getBytes();
        byte[] total = new byte[start.length + bytes.length + end.length];
        System.arraycopy(start, 0, total, 0, start.length);
        System.arraycopy(bytes, 0, total, start.length, bytes.length);
        System.arraycopy(end,   0, total, start.length + bytes.length, end.length);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(API_URL + "/files/upload"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(total))
            .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            try {
                Map<String, String> m = mapper.readValue(res.body(), new TypeReference<Map<String, String>>() {});
                String url = m.get("url");
                if (url == null || url.isBlank()) {
                    System.err.println("Upload: réponse JSON sans 'url' : " + res.body());
                    return null;
                }
                return url + "::" + (m.get("type") != null ? m.get("type") : "");
            } catch (Exception parseEx) {
                System.err.println("Upload: JSON invalide (" + res.statusCode() + "): " + res.body());
                return null;
            }
        }
        System.err.println("Upload échoué HTTP " + res.statusCode() + " : " + res.body());
        return null;
    }

    private void uploadAndSend(java.io.File file) throws Exception {
        String result = uploadFileSync(file);
        if (result != null) {
            String[] parts = result.split("::", 2);
            final String url  = parts[0];
            final String type = parts.length > 1 ? parts[1] : "";
            // Envoi immédiat sans passer par le champ texte
            Platform.runLater(() -> {
                pendingFileUrl  = url;
                pendingFileType = type;
                onSendMessage();
            });
        } else {
            Platform.runLater(() ->
                messageField.setPromptText("Échec upload : vérifiez le serveur ou la taille du fichier."));
        }
    }

    @FXML public void onOpenSettings() {
        chatScreen.setVisible(false); settingsScreen.setVisible(true);
        settingsUsernameField.setText(currentUser.getUsername());

        String picUrl = currentUser.getProfilePictureUrl();
        if (picUrl != null && picUrl.startsWith("data:image") && picUrl.length() > 100) {
            settingsUrlField.setText(picUrl.substring(0, 50) + "...");
        } else {
            settingsUrlField.setText(picUrl != null ? picUrl : "");
        }

        updateAvatar(settingsAvatarPreview, currentUser);
    }
    @FXML public void onCloseSettings() { settingsScreen.setVisible(false); chatScreen.setVisible(true); }
    @FXML public void onSaveSettings() {
        new Thread(() -> {
            try {
                currentUser.setUsername(settingsUsernameField.getText());
                String newUrl = settingsUrlField.getText();

                // Invalider le cache de l'ANCIENNE URL avant de changer
                String oldCacheKey = getSafeAvatarUrl(currentUser);

                // Ne pas écraser si c'est la version tronquée affichée ou un état temporaire
                if (!newUrl.equals("Upload...") && !newUrl.equals("Envoi en cours...") && !newUrl.endsWith("...")) {
                    currentUser.setProfilePictureUrl(newUrl);
                }

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/users/" + currentUser.getId()))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(currentUser)))
                    .build();

                if (httpClient.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) {
                    imageCache.remove(oldCacheKey);
                    imageCache.remove(getSafeAvatarUrl(currentUser));
                    Platform.runLater(() -> {
                        myUsername.setText(currentUser.getUsername());
                        updateAvatar(myAvatarCircle, currentUser);
                        updateAvatar(settingsAvatarPreview, currentUser);
                        onCloseSettings();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    @FXML public void onPickAvatarFile() {
        File f = new FileChooser().showOpenDialog(authScreen.getScene().getWindow());
        if (f != null) {
            try {
                // 1. Conversion Base64 (nécessaire pour la persistance sur Railway)
                byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                String mime = java.nio.file.Files.probeContentType(f.toPath());
                if (mime == null) mime = "image/png";
                String base64 = "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);

                // 2. Preview immédiat
                Image img = new Image(new java.io.ByteArrayInputStream(bytes), 120, 120, true, true);
                settingsAvatarPreview.setFill(new ImagePattern(img));

                // 3. Mise à jour de l'objet utilisateur (sera envoyé au prochain "Enregistrer")
                currentUser.setProfilePictureUrl(base64);

                // 4. Affichage tronqué dans le champ texte
                settingsUrlField.setText(base64.substring(0, Math.min(base64.length(), 50)) + "...");
                
            } catch (Exception e) {
                e.printStackTrace();
                settingsUrlField.setText("Erreur de lecture du fichier");
            }
        }
    }
    @FXML public void onClose() { Platform.exit(); System.exit(0); }
    @FXML public void onMousePressed(MouseEvent e) { xOffset = e.getSceneX(); yOffset = e.getSceneY(); }
    @FXML public void onMouseDragged(MouseEvent e) { chatScreen.getScene().getWindow().setX(e.getScreenX() - xOffset); chatScreen.getScene().getWindow().setY(e.getScreenY() - yOffset); }
}
