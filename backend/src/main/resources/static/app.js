const API_URL = 'https://ant-chat-production.up.railway.app/api';
let stompClient = null;
let currentUser = null;
let currentChatUserId = null; // null => Global, id => Private

// DOM
const authScreen        = document.getElementById('auth-screen');
const chatScreen        = document.getElementById('chat-screen');
const usernameInput     = document.getElementById('username');
const passwordInput     = document.getElementById('password');
const contactList       = document.getElementById('contact-list');
const messagesContainer = document.getElementById('messages-container');
const messageForm       = document.getElementById('message-form');
const currentChatNameEl = document.getElementById('current-chat-name');
const btnFile           = document.getElementById('btn-file');
const fileInput         = document.getElementById('file-input');
const btnEmoji          = document.getElementById('btn-emoji');

let emojiPicker = null;

// État : historique en mémoire
const messageHistory = {
    global: [],
    private: {} // { userId: [messages...] }
};

// ─── Auth ──────────────────────────────────────────────────────────────
document.getElementById('btn-login').addEventListener('click',    (e) => handleAuth(e, 'login'));
document.getElementById('btn-register').addEventListener('click', (e) => handleAuth(e, 'register'));

async function handleAuth(e, action) {
    if (e) e.preventDefault();
    const username = usernameInput.value.trim();
    const password = passwordInput.value.trim();
    if (!username || !password) return;

    try {
        const response = await fetch(`${API_URL}/auth/${action}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) throw new Error(await response.text());

        if (action === 'register') {
            authMsg.style.color = '#10b981';
            authMsg.textContent = 'Compte créé ! Veuillez vous connecter.';
        } else {
            currentUser = await response.json();
            authScreen.classList.add('hidden');
            chatScreen.classList.remove('hidden');
            document.getElementById('my-username').textContent = currentUser.username;
            document.getElementById('my-avatar').textContent = currentUser.username.substring(0, 1).toUpperCase();
            initChat();
        }
    } catch (error) {
        authMsg.style.color = '#ef4444';
        authMsg.textContent = error.message;
    }
}

// ─── Init ──────────────────────────────────────────────────────────────
function initChat() {
    loadContacts();
    connectWebSocket();
    initEmojiPicker();
    initFileUpload();
    Notification.requestPermission();
}

function initEmojiPicker() {
    const { createPicker } = window.picmo;
    const pickerContainer = document.createElement('div');
    pickerContainer.className = 'emoji-picker-container hidden';
    document.body.appendChild(pickerContainer);

    emojiPicker = createPicker({ rootElement: pickerContainer });
    emojiPicker.addEventListener('emoji:select', (selection) => {
        messageInput.value += selection.emoji;
        pickerContainer.classList.add('hidden');
    });

    btnEmoji.addEventListener('click', (e) => {
        e.stopPropagation();
        pickerContainer.classList.toggle('hidden');
    });

    document.addEventListener('click', () => {
        pickerContainer.classList.add('hidden');
    });
}

function initFileUpload() {
    btnFile.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', async () => {
        const file = fileInput.files[0];
        if (!file) return;

        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch(`${API_URL}/files/upload`, {
                method: 'POST',
                body: formData
            });
            const data = await res.json();
            sendMessage(null, data.url, data.type);
        } catch (e) {
            console.error('Upload error:', e);
            alert('Erreur lors de l\'envoi du fichier');
        }
        fileInput.value = '';
    });
}

// ─── WebSocket ─────────────────────────────────────────────────────────
function connectWebSocket() {
    const socket = new SockJS('https://ant-chat-production.up.railway.app/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, () => {
        stompClient.subscribe('/topic/global', (payload) => {
            const msg = JSON.parse(payload.body);
            messageHistory.global.push(msg);
            if (currentChatUserId === null) displayMessage(msg);
        });

        stompClient.subscribe(`/topic/private/${currentUser.id}`, (payload) => {
            const msg = JSON.parse(payload.body);
            const peerId = (msg.sender.id == currentUser.id) ? msg.receiver.id : msg.sender.id;
            if (!messageHistory.private[peerId]) messageHistory.private[peerId] = [];
            messageHistory.private[peerId].push(msg);
            if (currentChatUserId == peerId) displayMessage(msg);
        });
    });
}

// ─── Contacts (sidebar) ────────────────────────────────────────────────
async function loadContacts() {
    try {
        const res = await fetch(`${API_URL}/users`);
        const users = await res.json();

        contactList.innerHTML = '';

        // Chat Global
        const globalLi = document.createElement('li');
        globalLi.dataset.userId = 'global';
        globalLi.innerHTML = `<i class="ph-fill ph-globe"></i> <span>Chat Global</span>`;
        globalLi.addEventListener('click', function () {
            openChat(null, 'Chat Global', globalLi);
        });
        contactList.appendChild(globalLi);

        // Utilisateurs
        users.forEach(user => {
            if (user.id == currentUser.id) return;
            const li = document.createElement('li');
            li.dataset.userId = String(user.id);
            li.innerHTML = `<i class="ph-fill ph-user"></i> <span>${user.username}</span>`;
            li.addEventListener('click', function () {
                openChat(user.id, user.username, li);
            });
            contactList.appendChild(li);
        });
    } catch (e) { console.error('loadContacts error:', e); }
}

// ─── Ouvrir une conversation ───────────────────────────────────────────
async function openChat(peerId, name, listItem) {
    currentChatUserId = peerId;
    currentChatNameEl.textContent = name;
    messageForm.classList.remove('hidden');
    messagesContainer.innerHTML = '';

    // Highlight dans la sidebar
    Array.from(contactList.children).forEach(li => li.classList.remove('active'));
    if (listItem) listItem.classList.add('active');

    try {
        if (peerId === null) {
            // Chat Global
            if (messageHistory.global.length === 0) {
                const res = await fetch(`${API_URL}/users/history/global`);
                messageHistory.global = await res.json();
            }
            messageHistory.global.forEach(displayMessage);
        } else {
            // Chat Privé
            if (!messageHistory.private[peerId]) {
                const res = await fetch(`${API_URL}/users/history/private/${currentUser.id}/${peerId}`);
                messageHistory.private[peerId] = await res.json();
            }
            messageHistory.private[peerId].forEach(displayMessage);
        }
    } catch (e) { console.error('openChat error:', e); }
}

// ─── Envoi de message ──────────────────────────────────────────────────
messageForm.addEventListener('submit', (e) => {
    e.preventDefault();
    sendMessage(messageInput.value.trim());
    messageInput.value = '';
});

function sendMessage(content, fileUrl = null, fileType = null) {
    if ((!content && !fileUrl) || !stompClient || !currentUser) return;

    const payload = {
        content: content || (fileUrl ? "[Fichier]" : ""),
        senderId: currentUser.id,
        receiverId: currentChatUserId,
        fileUrl: fileUrl,
        fileType: fileType
    };

    const destination = currentChatUserId === null ? '/app/chat.sendMessage' : '/app/chat.privateMessage';
    stompClient.send(destination, {}, JSON.stringify(payload));
}

// ─── Affichage d'un message ────────────────────────────────────────────
function displayMessage(message) {
    const isMe = (message.sender.id == currentUser.id);
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${isMe ? 'me' : 'other'}`;
    const time = new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    let contentHtml = `<div class="msg-bubble">${message.content}</div>`;
    
    if (message.fileUrl) {
        const fullUrl = message.fileUrl.startsWith('http') ? message.fileUrl : `https://ant-chat-production.up.railway.app${message.fileUrl}`;
        if (message.fileType && message.fileType.startsWith('image/')) {
            contentHtml = `<div class="msg-bubble">
                <img src="${fullUrl}" class="msg-img" onclick="window.open('${fullUrl}')">
                ${message.content && message.content !== '[Fichier]' ? `<p>${message.content}</p>` : ''}
            </div>`;
        } else {
            contentHtml = `<div class="msg-bubble">
                <a href="${fullUrl}" target="_blank" class="msg-file">
                    <i class="ph-fill ph-file"></i>
                    <span>Fichier attaché</span>
                </a>
                ${message.content && message.content !== '[Fichier]' ? `<p>${message.content}</p>` : ''}
            </div>`;
        }
    }

    msgDiv.innerHTML = `
        <div class="msg-info">${isMe ? 'Vous' : message.sender.username} • ${time}</div>
        ${contentHtml}
    `;

    messagesContainer.appendChild(msgDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;

    // Notification si pas actif
    if (!isMe && document.hidden) {
        new Notification(`Nouveau message de ${message.sender.username}`, {
            body: message.content,
            icon: '/favicon.ico'
        });
    }
}

// ─── Annuaire ──────────────────────────────────────────────────────────
const annuaireModal  = document.getElementById('annuaire-modal');
const annuaireList   = document.getElementById('annuaire-list');
const annuaireSearch = document.getElementById('annuaire-search-input');
let allUsers = [];

document.getElementById('btn-annuaire').addEventListener('click', openAnnuaire);
document.getElementById('btn-close-annuaire').addEventListener('click', closeAnnuaire);

// Clic sur le fond de la modal => fermer
annuaireModal.addEventListener('click', (e) => {
    if (e.target === annuaireModal) closeAnnuaire();
});

// Recherche en temps réel
annuaireSearch.addEventListener('input', () => {
    const q = annuaireSearch.value.trim().toLowerCase();
    const filtered = q ? allUsers.filter(u => u.username.toLowerCase().includes(q)) : allUsers;
    renderAnnuaireList(filtered);
});

function closeAnnuaire() {
    annuaireModal.classList.add('hidden');
}

async function openAnnuaire() {
    if (!currentUser) return; // Ne pas ouvrir si pas connecté
    annuaireModal.classList.remove('hidden');
    annuaireSearch.value = '';

    try {
        const res = await fetch(`${API_URL}/users`);
        allUsers = await res.json();
        renderAnnuaireList(allUsers);
    } catch (e) {
        console.error('openAnnuaire error:', e);
    }
}

function renderAnnuaireList(users) {
    annuaireList.innerHTML = '';

    const currentId = currentUser ? currentUser.id : null;

    users.forEach(user => {
        // Exclure soi-même (comparaison non-stricte pour éviter les bugs de type Number vs String)
        if (currentId != null && user.id == currentId) return;

        const isBot = user.username === 'AntIA';
        const li = document.createElement('li');
        if (isBot) li.classList.add('bot-user');

        const avatar = document.createElement('div');
        avatar.className = 'user-avatar';
        avatar.textContent = user.username.substring(0, 1).toUpperCase();

        const details = document.createElement('div');
        details.className = 'user-details';
        details.innerHTML = `
            <div class="uname">${user.username} ${isBot ? '🤖' : ''}</div>
            <div class="urole">${isBot ? 'Intelligence Artificielle' : 'Utilisateur'}</div>
        `;

        const btnMsg = document.createElement('button');
        btnMsg.className = 'btn-msg';
        btnMsg.textContent = 'Envoyer';
        btnMsg.addEventListener('click', (e) => {
            e.stopPropagation(); // Empêcher la propagation vers le li
            closeAnnuaire();

            // Trouver et marquer l'élément dans la sidebar
            let sidebarItem = null;
            Array.from(contactList.children).forEach(el => {
                el.classList.remove('active');
                if (el.dataset.userId == String(user.id)) {
                    el.classList.add('active');
                    sidebarItem = el;
                }
            });

            openChat(user.id, user.username, sidebarItem);
        });

        li.appendChild(avatar);
        li.appendChild(details);
        li.appendChild(btnMsg);
        annuaireList.appendChild(li);
    });

    if (annuaireList.children.length === 0) {
        annuaireList.innerHTML = '<li style="color:var(--text-secondary);padding:20px;text-align:center;">Aucun utilisateur trouvé</li>';
    }
}

// ─── Déconnexion ───────────────────────────────────────────────────────
document.getElementById('btn-logout').addEventListener('click', () => {
    if (stompClient) {
        try { stompClient.disconnect(); } catch(e) {}
        stompClient = null;
    }
    currentUser = null;
    currentChatUserId = null;
    messageHistory.global = [];
    messageHistory.private = {};
    allUsers = [];
    annuaireModal.classList.add('hidden');
    chatScreen.classList.add('hidden');
    authScreen.classList.remove('hidden');
    usernameInput.value = '';
    passwordInput.value = '';
    authMsg.textContent = '';
});
