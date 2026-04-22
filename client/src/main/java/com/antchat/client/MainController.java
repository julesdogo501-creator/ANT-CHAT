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
    private final String API_URL = "https://fourmi-chat-production.up.railway.app/api";

    // --- CACHES & HISTORIES ---
    private final Map<Long, VBox> privateHistories = new HashMap<>();
    private final Map<Long, VBox> groupHistories = new HashMap<>();

    // --- TEMP DATA ---
    private String pendingFileUrl = null;
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
                            authMessage.setStyle("-fx-text-fill: #10b981;");
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
        
        // Couleur avatar perso
        int hashMe = Math.abs(currentUser.getUsername().hashCode());
        myAvatarCircle.setFill(javafx.scene.paint.Color.rgb(hashMe % 150 + 50, (hashMe >> 8) % 150 + 50, (hashMe >> 16) % 150 + 50));

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
            private final HBox layout = new HBox(15);
            private final ImageView avatar = new ImageView();
            private final VBox textLayout = new VBox(3);
            private final Label nameLabel = new Label();
            private final Label statusLabel = new Label();
            {
                avatar.setFitWidth(44); avatar.setFitHeight(44);
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(44, 44);
                clip.setArcWidth(44); clip.setArcHeight(44);
                avatar.setClip(clip);
                layout.setAlignment(Pos.CENTER_LEFT);
                layout.setPadding(new javafx.geometry.Insets(10, 15, 10, 15));
                HBox.setHgrow(textLayout, Priority.ALWAYS);
                nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px;");
                statusLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");
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
                        avatar.setStyle("-fx-effect: dropshadow(three-pass-box, #bf00ff, 10, 0, 0, 0);");
                        updateAvatarImageView(avatar, null);
                    } else {
                        User u = item.getUser();
                        avatar.setStyle("");
                        statusLabel.setText(u.getId() == null ? "Chat ouvert à tous" : (u.isOnline() ? "En ligne" : "Hors ligne"));
                        updateAvatarImageView(avatar, u);
                    }
                    setGraphic(layout);
                }
            }
        });

        contactListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isSwitchingTab) {
                if (isDirectoryMode) {
                    ChatItem existing = activeChats.stream().filter(c -> c.equals(newVal)).findFirst().orElse(null);
                    if (existing == null) {
                        activeChats.add(0, newVal);
                        existing = newVal;
                    }
                    onTabDiscussions();
                    ChatItem finalItem = existing;
                    Platform.runLater(() -> contactListView.getSelectionModel().select(finalItem));
                }
                selectChat(newVal);
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
                contactListView.setPlaceholder(new Label("Aucun groupe. Créez-en un !"));
                break;
            case "DIRECTORY":
                tabDirectoryBtn.getStyleClass().add("nav-active");
                source = allUsers;
                contactListView.setPlaceholder(new Label("Personne dans l'annuaire..."));
                break;
            default:
                tabDiscussionsBtn.getStyleClass().add("nav-active");
                source = activeChats;
                contactListView.setPlaceholder(new Label("Aucune discussion active."));
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
            if (historyBox.getChildren().isEmpty()) fetchAndDisplayHistory("/groups/" + g.getId() + "/history", historyBox);
        } else {
            User u = item.getUser();
            currentChatName.setText(u.getUsername());
            updateAvatar(chatHeaderAvatar, u);
            VBox vb = new VBox(10); vb.setId("HistoryPrivate_" + u.getId());
            privateHistories.putIfAbsent(u.getId(), vb);
            historyBox = privateHistories.get(u.getId());
            if (historyBox.getChildren().isEmpty()) fetchAndDisplayHistory("/users/history/private/" + currentUser.getId() + "/" + u.getId(), historyBox);
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
                    Platform.runLater(() -> {
                        List<javafx.scene.Node> nodes = new java.util.ArrayList<>();
                        for (Message m : history) {
                            javafx.scene.Node node = createMessageNode(m);
                            if (node != null) nodes.add(node);
                        }
                        container.getChildren().setAll(nodes); // Batching pour éviter le spam Pulse !
                        
                        if (messagesScrollPane.getContent() == container) {
                            messagesScrollPane.setVvalue(1.0);
                        }
                    });
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
        title.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        ListView<ChatItem> list = new ListView<>(allUsers);
        list.getStyleClass().add("neon-list");
        Button close = new Button("Fermer");
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
        
        System.out.println("DEBUG: Envoi de message -> " + text);
        if (selectedItem != null) {
            if (selectedItem.isGroup()) {
                wsService.sendGroupMessage(currentUser.getId(), selectedItem.getGroup().getId(), text, pendingFileUrl, pendingFileType);
            } else {
                wsService.sendPrivateMessage(currentUser.getId(), selectedItem.getUser().getId(), text, pendingFileUrl, pendingFileType);
            }
        }
        messageField.clear(); pendingFileUrl = null; pendingFileType = null;
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
                String url = msg.getFileUrl().startsWith("http") ? msg.getFileUrl() : API_URL.replace("/api", "") + msg.getFileUrl();
                if (msg.getFileType() != null && msg.getFileType().startsWith("image/")) {
                    ImageView iv = new ImageView();
                    iv.setFitWidth(250);
                    iv.setPreserveRatio(true);
                    Image img = new Image(url, true); // Asynchrone natif sans setCache
                    iv.setImage(img);
                    iv.setOnMouseClicked(e -> { try { java.awt.Desktop.getDesktop().browse(URI.create(url)); } catch (Exception ex) {} });
                    content.getChildren().add(iv);
                } else {
                    Hyperlink link = new Hyperlink("📎 Fichier");
                    link.setOnAction(e -> { try { java.awt.Desktop.getDesktop().browse(URI.create(url)); } catch (Exception ex) {} });
                    content.getChildren().add(link);
                }
            } catch (Exception e) {}
        }

        if (msg.getContent() != null && !msg.getContent().isEmpty() && !msg.getContent().equals("[Fichier]")) {
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

    private void updateAvatarImageView(ImageView iv, User user) {
        if (iv == null) return;
        String url = getSafeAvatarUrl(user);
        try {
            Image img = imageCache.computeIfAbsent(url, u -> new Image(u, 60, 60, true, true, true));
            iv.setImage(img);
        } catch (Exception e) {}
    }

    private void updateAvatar(Circle circle, User user) {
        if (circle == null || user == null) return;
        String url = getSafeAvatarUrl(user);
        
        try {
            Image img = imageCache.computeIfAbsent(url, u -> new Image(u, 60, 60, true, true, true));
            
            if (img.getProgress() == 1.0 && !img.isError()) {
                try { circle.setFill(new ImagePattern(img)); } catch (Exception e) {}
                return;
            }
            if (img.isError()) { setFallbackAvatar(circle, user); return; }

            img.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() == 1.0 && !img.isError()) {
                    try { circle.setFill(new ImagePattern(img)); } catch (Exception e) {}
                }
            });
            img.errorProperty().addListener((obs, oldV, newV) -> {
                if (newV) setFallbackAvatar(circle, user);
            });
        } catch (Exception e) {
            setFallbackAvatar(circle, user);
        }
    }

    private String getSafeAvatarUrl(User user) {
        if (user == null) return "https://ui-avatars.com/api/?name=U";
        String url = user.getProfilePictureUrl();
        if (url == null || url.isEmpty()) return "https://ui-avatars.com/api/?name=" + user.getUsername();
        if (url.startsWith("data:image/png;base64,/9j/")) {
            url = url.replace("data:image/png;base64,", "data:image/jpeg;base64,");
        }
        return url;
    }

    private void setFallbackAvatar(Circle c, User u) {
        int hash = Math.abs(u.getUsername().hashCode());
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

    @FXML public void onAttachMedia() {
        FileChooser fc = new FileChooser();
        File file = fc.showOpenDialog(authScreen.getScene().getWindow());
        if (file != null) new Thread(() -> { try { uploadFile(file); } catch (Exception e) {} }).start();
    }

    private void uploadFile(File file) throws Exception {
        String boundary = "---" + System.currentTimeMillis();
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        String mime = java.nio.file.Files.probeContentType(file.toPath());
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\nContent-Type: " + (mime != null ? mime : "application/octet-stream") + "\r\n\r\n";
        byte[] start = body.getBytes(); byte[] end = ("\r\n--" + boundary + "--\r\n").getBytes();
        byte[] total = new byte[start.length + bytes.length + end.length];
        System.arraycopy(start, 0, total, 0, start.length); System.arraycopy(bytes, 0, total, start.length, bytes.length); System.arraycopy(end, 0, total, start.length + bytes.length, end.length);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(API_URL + "/files/upload")).header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(total)).build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            Map<String, String> m = mapper.readValue(res.body(), new TypeReference<Map<String, String>>() {});
            Platform.runLater(() -> { pendingFileUrl = m.get("url"); pendingFileType = m.get("type"); messageField.setText("[Fichier prêt] " + messageField.getText()); });
        }
    }

    @FXML public void onOpenSettings() {
        chatScreen.setVisible(false); settingsScreen.setVisible(true);
        settingsUsernameField.setText(currentUser.getUsername());
        settingsUrlField.setText(currentUser.getProfilePictureUrl());
    }
    @FXML public void onCloseSettings() { settingsScreen.setVisible(false); chatScreen.setVisible(true); }
    @FXML public void onSaveSettings() {
        new Thread(() -> {
            try {
                currentUser.setUsername(settingsUsernameField.getText()); currentUser.setProfilePictureUrl(settingsUrlField.getText());
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(API_URL + "/users/" + currentUser.getId())).header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(currentUser))).build();
                if (httpClient.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) Platform.runLater(() -> { myUsername.setText(currentUser.getUsername()); updateAvatar(myAvatarCircle, currentUser); onCloseSettings(); });
            } catch (Exception e) {}
        }).start();
    }
    @FXML public void onPickAvatarFile() {
        File f = new FileChooser().showOpenDialog(authScreen.getScene().getWindow());
        if (f != null) try { settingsUrlField.setText("data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(f.toPath()))); } catch (Exception e) {}
    }

    @FXML public void onTabAppels() {} @FXML public void onTabActus() {} @FXML public void onTabOutils() {}
    @FXML public void onClose() { Platform.exit(); System.exit(0); }
    @FXML public void onMousePressed(MouseEvent e) { xOffset = e.getSceneX(); yOffset = e.getSceneY(); }
    @FXML public void onMouseDragged(MouseEvent e) { chatScreen.getScene().getWindow().setX(e.getScreenX() - xOffset); chatScreen.getScene().getWindow().setY(e.getScreenY() - yOffset); }
}
