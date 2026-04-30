const API_URL = 'https://ant-chat-production.up.railway.app/api';
let stompClient = null;
let currentUser = null;
let currentChatUserId = null; // null => Global, id => Private
let currentGroupId = null;
let wsConnected = false;
let wsConnecting = false;
let fileUploadHandlersBound = false;
let emojiPickerInitialized = false;

// DOM
const authScreen        = document.getElementById('auth-screen');
const chatScreen        = document.getElementById('chat-screen');
const usernameInput     = document.getElementById('username');
const passwordInput     = document.getElementById('password');
const contactList       = document.getElementById('contact-list');
const messagesContainer = document.getElementById('messages-container');
const messageForm       = document.getElementById('message-form');
const messageInput      = document.getElementById('message-input');
const currentChatNameEl = document.getElementById('current-chat-name');
const btnFile           = document.getElementById('btn-file');
const fileInput         = document.getElementById('file-input');
const btnEmoji          = document.getElementById('btn-emoji');
const authMsg           = document.getElementById('auth-message');
const annuaireModal     = document.getElementById('annuaire-modal');
const annuaireList      = document.getElementById('annuaire-list');
const annuaireSearch    = document.getElementById('annuaire-search-input');

let emojiPicker = null;

// État : historique en mémoire
const messageHistory = {
    global: [],
    private: {}, // { userId: [messages...] }
    groups: {}   // { groupId: [messages...] }
};
const renderedMessageKeys = new Set();
const subscribedGroupIds = new Set();

// Groups
const createGroupModal   = document.getElementById('create-group-modal');
const addMembersModal    = document.getElementById('add-members-modal');
const createGroupForm    = document.getElementById('create-group-form');
const createGroupMsg     = document.getElementById('create-group-msg');
const groupNameInput     = document.getElementById('group-name');
const addMembersMsg      = document.getElementById('add-members-msg');
const membersList        = document.getElementById('members-list');
const membersSearchInput = document.getElementById('members-search-input');
let allUsersForGroups   = [];
let allUsers            = [];
let currentGroupForMembers = null;

function looksLikeImageUrl(url) {
    if (!url) return false;
    const path = String(url).split('?')[0].toLowerCase();
    return /\.(png|jpe?g|gif|webp|bmp|svg|heic|avif)$/.test(path);
}

function isImageAttachment(fileUrl, fileType) {
    const t = (fileType || '').toLowerCase();
    if (t.startsWith('image/')) return true;
    return looksLikeImageUrl(fileUrl);
}

function getMessageKey(message) {
    if (message && message.id != null) return `id:${message.id}`;
    const senderId = message?.sender?.id ?? 'na';
    const receiverId = message?.receiver?.id ?? 'global';
    const timestamp = message?.timestamp ?? 'no-time';
    const content = message?.content ?? '';
    const fileUrl = message?.fileUrl ?? '';
    return `fallback:${senderId}:${receiverId}:${timestamp}:${content}:${fileUrl}`;
}

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
    if (emojiPickerInitialized) return;
    emojiPickerInitialized = true;
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
    if (fileUploadHandlersBound) return;
    fileUploadHandlersBound = true;
    let uploadBusy = false;
    btnFile.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', async () => {
        const file = fileInput.files[0];
        if (!file || uploadBusy) return;
        uploadBusy = true;

        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch(`${API_URL}/files/upload`, {
                method: 'POST',
                body: formData
            });
            const raw = await res.text();
            if (!res.ok) {
                throw new Error(raw || res.statusText);
            }
            const data = JSON.parse(raw);
            sendMessage(null, data.url, data.type);
        } catch (e) {
            console.error('Upload error:', e);
            alert('Erreur lors de l\'envoi du fichier : ' + (e.message || e));
        } finally {
            uploadBusy = false;
            fileInput.value = '';
        }
    });
}

// ─── WebSocket ─────────────────────────────────────────────────────────
function connectWebSocket() {
    if (wsConnected) return;
    if (wsConnecting) return;
    wsConnecting = true;
    if (stompClient) {
        try { stompClient.disconnect(); } catch (e) {}
        stompClient = null;
    }
    const socket = new SockJS('https://ant-chat-production.up.railway.app/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, () => {
        wsConnected = true;
        wsConnecting = false;
        stompClient.subscribe('/topic/global', (payload) => {
            const msg = JSON.parse(payload.body);
            const key = getMessageKey(msg);
            if (messageHistory.global.some(m => getMessageKey(m) === key)) return;
            messageHistory.global.push(msg);
            if (currentGroupId === null && currentChatUserId === null) displayMessage(msg);
        });

        stompClient.subscribe(`/topic/private/${currentUser.id}`, (payload) => {
            const msg = JSON.parse(payload.body);
            const peerId = (msg.sender.id == currentUser.id) ? msg.receiver.id : msg.sender.id;
            if (!messageHistory.private[peerId]) messageHistory.private[peerId] = [];
            const key = getMessageKey(msg);
            if (messageHistory.private[peerId].some(m => getMessageKey(m) === key)) return;
            messageHistory.private[peerId].push(msg);
            if (currentChatUserId == peerId && currentGroupId === null) displayMessage(msg);
        });

        if (currentGroupId !== null) {
            subscribeToGroup(currentGroupId);
        }
    }, () => {
        wsConnected = false;
        wsConnecting = false;
    });
}

function subscribeToGroup(groupId) {
    if (!stompClient || !wsConnected || !groupId || subscribedGroupIds.has(groupId)) return;

    stompClient.subscribe(`/topic/group/${groupId}`, (payload) => {
        const msg = JSON.parse(payload.body);
        if (!messageHistory.groups[groupId]) messageHistory.groups[groupId] = [];
        const key = getMessageKey(msg);
        if (messageHistory.groups[groupId].some(m => getMessageKey(m) === key)) return;
        messageHistory.groups[groupId].push(msg);
        if (currentGroupId == groupId) displayMessage(msg);
    });

    subscribedGroupIds.add(groupId);
}

// ─── Contacts (sidebar) ────────────────────────────────────────────────
async function loadContacts() {
    try {
        const resUsers = await fetch(`${API_URL}/users`);
        const users = await resUsers.json();
        allUsersForGroups = users;

        const resGroups = await fetch(`${API_URL}/groups/user/${currentUser.id}`);
        const groups = resGroups.ok ? await resGroups.json() : [];

        contactList.innerHTML = '';

        // Chat Global
        const globalLi = document.createElement('li');
        globalLi.dataset.userId = 'global';
        globalLi.innerHTML = `<i class="ph-fill ph-globe"></i> <span>Chat Global</span>`;
        globalLi.addEventListener('click', function () {
            currentGroupId = null;
            openChat(null, 'Chat Global', globalLi);
        });
        contactList.appendChild(globalLi);

        // Groupes
        groups.forEach(group => {
            const li = document.createElement('li');
            li.classList.add('group');
            li.dataset.groupId = String(group.id);
            li.innerHTML = `<i class="ph-fill ph-users"></i> <span>${group.name}</span>`;
            li.addEventListener('click', function () {
                currentChatUserId = null;
                currentGroupId = group.id;
                openChat(null, group.name, li);

                if (group.admin?.id == currentUser.id) {
                    setTimeout(() => {
                        const existingBtn = document.getElementById('btn-add-members-group');
                        if (!existingBtn) {
                            const headerActions = document.querySelector('.header-actions');
                            const btn = document.createElement('button');
                            btn.id = 'btn-add-members-group';
                            btn.className = 'btn-icon';
                            btn.title = 'Ajouter des membres';
                            btn.innerHTML = '<i class="ph-fill ph-user-plus"></i> <span>Ajouter</span>';
                            btn.addEventListener('click', (e) => {
                                e.preventDefault();
                                openAddMembersModal(group.id);
                            });
                            headerActions.insertBefore(btn, headerActions.firstChild);
                        }
                    }, 100);
                } else {
                    document.getElementById('btn-add-members-group')?.remove();
                }
            });
            contactList.appendChild(li);
        });

        // Utilisateurs
        users.forEach(user => {
            if (user.id == currentUser.id) return;
            const li = document.createElement('li');
            li.dataset.userId = String(user.id);
            li.innerHTML = `<i class="ph-fill ph-user"></i> <span>${user.username}</span>`;
            li.addEventListener('click', function () {
                currentGroupId = null;
                openChat(user.id, user.username, li);
                document.getElementById('btn-add-members-group')?.remove();
            });
            contactList.appendChild(li);
        });
    } catch (e) { console.error('loadContacts error:', e); }
}

// ─── Ouvrir une conversation ───────────────────────────────────────────
async function openChat(peerId, name, listItem) {
    renderedMessageKeys.clear();
    currentChatUserId = peerId;
    currentChatNameEl.textContent = name;
    messageForm.classList.remove('hidden');
    messagesContainer.innerHTML = '';

    // Highlight dans la sidebar
    Array.from(contactList.children).forEach(li => li.classList.remove('active'));
    if (listItem) listItem.classList.add('active');

    try {
        if (currentGroupId !== null) {
            if (!messageHistory.groups[currentGroupId]) {
                const res = await fetch(`${API_URL}/groups/${currentGroupId}/history`);
                messageHistory.groups[currentGroupId] = await res.json();
            }
            messageHistory.groups[currentGroupId].forEach(displayMessage);
            subscribeToGroup(currentGroupId);
        } else if (peerId === null) {
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
        receiverId: null,
        groupId: null,
        fileUrl: fileUrl,
        fileType: fileType
    };

    let destination;
    if (currentGroupId !== null) {
        payload.groupId = currentGroupId;
        destination = '/app/chat.groupMessage';
    } else if (currentChatUserId === null) {
        destination = '/app/chat.sendMessage';
    } else {
        payload.receiverId = currentChatUserId;
        destination = '/app/chat.privateMessage';
    }

    stompClient.send(destination, {}, JSON.stringify(payload));
}

// ─── Affichage d'un message ────────────────────────────────────────────
function displayMessage(message) {
    const key = getMessageKey(message);
    if (renderedMessageKeys.has(key)) return;
    renderedMessageKeys.add(key);

    const isMe = (message.sender.id == currentUser.id);
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${isMe ? 'me' : 'other'}`;
    const time = new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    let contentHtml = `<div class="msg-bubble">${message.content}</div>`;
    
    if (message.fileUrl) {
        const fullUrl = message.fileUrl.startsWith('http') ? message.fileUrl : `https://ant-chat-production.up.railway.app${message.fileUrl}`;
        if (isImageAttachment(message.fileUrl, message.fileType)) {
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

document.getElementById('btn-create-group').addEventListener('click', () => {
    createGroupModal.classList.remove('hidden');
    groupNameInput.value = '';
    createGroupMsg.textContent = '';
});

document.getElementById('btn-close-create-group').addEventListener('click', () => {
    createGroupModal.classList.add('hidden');
});

createGroupModal.addEventListener('click', (e) => {
    if (e.target === createGroupModal) {
        createGroupModal.classList.add('hidden');
    }
});

createGroupForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const groupName = groupNameInput.value.trim();
    if (!groupName) return;

    try {
        const response = await fetch(`${API_URL}/groups?name=${encodeURIComponent(groupName)}&adminId=${currentUser.id}`, {
            method: 'POST'
        });

        if (response.ok) {
            const group = await response.json();
            createGroupMsg.style.color = '#10b981';
            createGroupMsg.textContent = 'Groupe créé avec succès !';
            groupNameInput.value = '';
            setTimeout(() => {
                createGroupModal.classList.add('hidden');
                loadContacts();
            }, 1000);
        } else {
            createGroupMsg.style.color = '#ef4444';
            createGroupMsg.textContent = 'Erreur lors de la création du groupe';
        }
    } catch (e) {
        createGroupMsg.style.color = '#ef4444';
        createGroupMsg.textContent = 'Erreur : ' + e.message;
    }
});

document.getElementById('btn-close-add-members').addEventListener('click', () => {
    addMembersModal.classList.add('hidden');
});

addMembersModal.addEventListener('click', (e) => {
    if (e.target === addMembersModal) {
        addMembersModal.classList.add('hidden');
    }
});

membersSearchInput.addEventListener('input', () => {
    const query = membersSearchInput.value.trim().toLowerCase();
    const filtered = query ? allUsersForGroups.filter(u => u.username.toLowerCase().includes(query)) : allUsersForGroups;
    renderMembersToAdd(filtered);
});

function openAddMembersModal(groupId) {
    currentGroupForMembers = groupId;
    addMembersModal.classList.remove('hidden');
    membersSearchInput.value = '';
    addMembersMsg.textContent = '';
    renderMembersToAdd(allUsersForGroups);
}

function renderMembersToAdd(users) {
    membersList.innerHTML = '';

    users.forEach(user => {
        if (user.id == currentUser.id) return;

        const li = document.createElement('li');
        const avatar = document.createElement('div');
        avatar.className = 'user-avatar';
        avatar.textContent = user.username.substring(0, 1).toUpperCase();

        const details = document.createElement('div');
        details.className = 'user-details';
        details.innerHTML = `<div class="uname">${user.username}</div>`;

        const btnAdd = document.createElement('button');
        btnAdd.className = 'btn-add';
        btnAdd.textContent = 'Ajouter';
        btnAdd.addEventListener('click', (e) => {
            e.stopPropagation();
            addMemberToGroup(currentGroupForMembers, user.id, btnAdd);
        });

        li.appendChild(avatar);
        li.appendChild(details);
        li.appendChild(btnAdd);
        membersList.appendChild(li);
    });

    if (membersList.children.length === 0) {
        membersList.innerHTML = '<li style="color:var(--text-secondary);padding:20px;text-align:center;">Aucun utilisateur trouvé</li>';
    }
}

async function addMemberToGroup(groupId, userId, buttonElement) {
    console.log('addMemberToGroup called with:', { groupId, userId, currentUserId: currentUser?.id });
    try {
        const url = `${API_URL}/groups/${groupId}/members/${userId}?requesterId=${currentUser.id}`;
        console.log('Making request to:', url);
        const response = await fetch(url, {
            method: 'POST'
        });

        if (response.ok) {
            addMembersMsg.style.color = '#10b981';
            addMembersMsg.textContent = 'Membre ajouté !';
            buttonElement.disabled = true;
            buttonElement.textContent = 'Ajouté ✓';
        } else {
            const errorText = await response.text();
            console.error('Add member error:', errorText);
            addMembersMsg.style.color = '#ef4444';
            addMembersMsg.textContent = errorText || 'Erreur lors de l\'ajout';
        }
    } catch (e) {
        console.error('Add member exception:', e);
        addMembersMsg.style.color = '#ef4444';
        addMembersMsg.textContent = 'Erreur : ' + e.message;
    }
}

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
    wsConnected = false;
    wsConnecting = false;
    currentUser = null;
    currentChatUserId = null;
    messageHistory.global = [];
    messageHistory.private = {};
    renderedMessageKeys.clear();
    allUsers = [];
    annuaireModal.classList.add('hidden');
    chatScreen.classList.add('hidden');
    authScreen.classList.remove('hidden');
    usernameInput.value = '';
    passwordInput.value = '';
    authMsg.textContent = '';
});
