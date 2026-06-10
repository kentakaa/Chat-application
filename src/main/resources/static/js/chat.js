var socket = null; // Native WebSocket Connection Instance
var username = window.CURRENT_USERNAME;
var currentRoom = null;
var joinedRooms = new Set();
let searchTimeout = null; 
var roomDetailsCache = {};
let lastTypingTime = 0; 
const TYPING_TIMER_LENGTH = 2000; 

window.onload = function () {
    if (username && username !== 'Unknown') {
        document.getElementById('my-avatar').innerText = username.charAt(0).toUpperCase();
        
        // 🚀 PRINCIPAL CORE UPGRADE: Pure Native Secure Pipeline Establishment
        // Localhost par 'ws://', Production SSL lagte hi automatic 'wss://' banega frontend standard par
        const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
        socket = new WebSocket(protocol + window.location.host + '/ws');

        // Hooking Global Native Browser Event Listeners
        socket.onopen = function() {
            console.log("🚀 TCP Pipe Open: Native WebSocket connected successfully!");
            fetchAndDisplayRooms(false); 
        };

        socket.onmessage = function(event) {
            onMessageReceived(event.data);
        };

        socket.onclose = function(e) {
            console.warn("⚠️ WebSocket Connection Closed. Attempting reconnecting strategy...", e.reason);
        };

        socket.onerror = function(err) {
            console.error("❌ Critical WebSocket Core Error:", err);
        };

    } else {
        window.location.href = '/login';
    }

    // Dropdown close handler
    window.addEventListener('click', function(e) {
        const dropdown = document.getElementById('header-context-dropdown');
        if (dropdown && !e.target.matches('.fa-ellipsis-vertical')) {
            dropdown.style.display = 'none';    
        }
    });
};

// Helper function: DM Rooms ke liye name calculate karne ke liye
function getRoomDisplayName(roomName) {
    if (roomName && roomName.includes('_')) {
        let users = roomName.split('_');
        // Agar mera naam phla element h to dusra dikhao, nahi to phla dikhao
        return users[0].toLowerCase() === username.toLowerCase() ? users[1] : users[0];
    }
    return roomName; // Group chats ke liye default original name
}

function fetchAndDisplayRooms(autoJoin = false) {
    fetch('/api/rooms')
        .then(response => response.json())
        .then(rooms => {
            const listDiv = document.getElementById('dynamic-room-list');
            listDiv.innerHTML = ''; 
            rooms.forEach(room => {
                roomDetailsCache[room.name] = room; 
                
                // 🎯 SOLUTION: Dynamic Display Name Rule Injection
                let displayName = getRoomDisplayName(room.name);
                let initial = displayName.charAt(0).toUpperCase();
                
                listDiv.innerHTML += `
                    <div class="chat-list-item" id="btn-${room.name}" onclick="openChannel('${room.name}')">
                        <div class="chat-avatar">${initial}</div>
                        <div class="chat-info">
                            <div class="chat-row">
                                <span class="chat-name">${displayName}</span>
                                <span class="chat-time">Active</span>
                            </div>
                            <div class="chat-row">
                                <span class="chat-last-msg">
                                    <i class="fa-solid fa-check-double tick-blue"></i> 
                                    Click to open chat
                                </span>
                            </div>
                        </div>
                    </div>
                `;
            });
            if (autoJoin && rooms.length > 0) openChannel(rooms[0].name);
        });
}

function createNewRoom() {
    var roomName = prompt("Enter new contact/group name:");
    if (roomName && roomName.trim() !== "") {
        fetch('/api/rooms', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: roomName })
        }).then(response => {
            if (!response.ok) throw new Error("Channel already exists");
            return response.json();
        }).then(newRoom => {
            fetchAndDisplayRooms(false);
            setTimeout(() => openChannel(newRoom.name), 200);
        }).catch(err => alert(err.message));
    }
}

function openChannel(roomName) {
    currentRoom = roomName;
    document.getElementById('placeholder').style.display = 'none';
    document.getElementById('chat-main-area').style.display = 'flex';
    
    // 🎯 SOLUTION: Header title update dynamically
    let displayName = getRoomDisplayName(roomName);
    document.getElementById('current-room-name').innerText = displayName;
    document.getElementById('current-room-avatar').innerText = displayName.charAt(0).toUpperCase();

    checkRoomUIState(roomDetailsCache[roomName]);

    document.querySelectorAll('.chat-list-item').forEach(el => el.classList.remove('active'));
    var activeBtn = document.getElementById('btn-' + roomName);
    if (activeBtn) activeBtn.classList.add('active');

    document.getElementById('messageArea').innerHTML = '';

    // Framework level clean extraction done here (No unsubscribe needed)
    loadHistory(currentRoom);
}

function sendMessage() {
    var messageContent = document.querySelector('#message').value.trim();
    if (messageContent && socket && socket.readyState === WebSocket.OPEN && currentRoom) {
        
        // Pure Native JSON Structure Wrapper
        var chatMessage = { 
            sender: username, 
            content: messageContent, 
            type: 'CHAT',
            roomId: currentRoom // Explicit reference for native router mapping
        };
        
        // 🚀 Native WebSocket Message Stream Injection
        socket.send(JSON.stringify(chatMessage));
        document.querySelector('#message').value = '';
    }
}

function handleKeyPress(event) { if (event.key === "Enter") sendMessage(); }

function onMessageReceived(rawData) { 
    try {
        let message = JSON.parse(rawData);
        
        // Global Broadcast Handling (Naya Room Status Trigger)
        if (message.type === 'SYSTEM' && message.content.includes("initialized by")) {
            fetchAndDisplayRooms(false);
            return;
        }

        // 🛡️ SECURITY OVERRIDE & CHECK: Sirf usi message ko show karo jiska panel active hai
        if (currentRoom === message.roomId) {
            displayMessage(message);
        } else {
            console.log(`📩 Background Packet optimization caught message for room: ${message.roomId}`);
            // Agar piche kisi room mein live notification chahiha to sidebar sync kardo
            if (message.type === 'SYSTEM' || message.type === 'LEAVE_EVENT') {
                fetchAndDisplayRooms(false);
            }
        }
    } catch(err) {
        console.error("Error parsing native text message payload packet:", err);
    }
}

function displayMessage(message) {
    var messageArea = document.querySelector('#messageArea');
    var messageRow = document.createElement('div');

    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        messageRow.className = 'message-row message-system';
        var text = message.type === 'JOIN' ? ' joined' : ' left';
        messageRow.innerHTML = `<span>${message.sender}${text}</span>`;
    } 
    else if (message.type === 'LEAVE_EVENT') {
        let leaver = message.content.split(' ')[0];
        let isMe = leaver === username;
        let leaveText = isMe ? "You left this chat." : `${leaver} has left the room.`;

        const inputArea = document.getElementById('chat-input-area');
        const blockedArea = document.getElementById('blocked-area');
        const activeActions = document.getElementById('active-chat-actions');
        
        let expiryTime = new Date();
        expiryTime.setHours(expiryTime.getHours() + 24);
        let timeString = expiryTime.toLocaleString('en-IN', { 
            day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: true 
        });

        if (inputArea && blockedArea) {
            inputArea.style.display = 'none';
            blockedArea.style.display = 'block';
            
            blockedArea.innerHTML = `
                <div style="font-size: 16px;">${leaveText}</div>
                <div style="font-size: 13px; color: #fca5a5; margin-top: 6px; font-weight: normal;">
                    <i class="fa-regular fa-clock"></i> Chat will be permanently deleted on <b>${timeString}</b>
                </div>
            `;
            if(activeActions) activeActions.style.display = 'none';
        }

        if(roomDetailsCache[message.roomId]) {
            roomDetailsCache[message.roomId].requestStatus = 'CLOSED';
            roomDetailsCache[message.roomId].leftBy = leaver;
            roomDetailsCache[message.roomId].closedAt = new Date().toISOString();
        }
        
        messageRow.className = 'message-row message-system';
        messageRow.innerHTML = `<span style="background: #7f1d1d; border: 1px solid #ef4444; padding: 6px 16px; border-radius: 20px; color: #fca5a5; font-size: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.3);">
            <b>${leaveText}</b> <br> <i class="fa-regular fa-clock"></i> Deletes on ${timeString}
        </span>`;
    }
    else if (message.type === 'TYPING') {
        if (message.sender === username) return;

        const typingIndicator = document.getElementById('typing-status'); 
        if (typingIndicator) {
            typingIndicator.innerText = "typing...";
            typingIndicator.style.color = "#22c55e"; 
            typingIndicator.style.display = "block";

            clearTimeout(window.typingTimer);

            window.typingTimer = setTimeout(() => {
                typingIndicator.style.display = "none";
            }, 2000);
        }
        return; 
    }
    else if (message.type === 'SYSTEM') {
        messageRow.className = 'message-row message-system';
        messageRow.innerHTML = `<span style="background: #334155; padding: 5px 15px; border-radius: 10px; color: #cbd5e1; font-size: 12px;">${message.content}</span>`;
        let displayContent = message.content;
        if (displayContent.startsWith(username + " ")) {
            displayContent = displayContent.replace(username + " ", "You ");
        }
        if (message.content.includes("accepted the request")) {
            fetch('/api/rooms')
                .then(res => res.json())
                .then(rooms => {
                    rooms.forEach(r => roomDetailsCache[r.name] = r);
                    if (currentRoom) checkRoomUIState(roomDetailsCache[currentRoom]);
                });
        }
    }
    else if (message.type === 'ROOM_DELETED') {
        if (currentRoom === message.roomId) {
            currentRoom = null;
            document.getElementById('chat-main-area').style.display = 'none';
            document.getElementById('placeholder').style.display = 'flex';
        }
        fetchAndDisplayRooms(false);
    } else {
        var isMe = message.sender === username;
        messageRow.className = 'message-row ' + (isMe ? 'message-sent' : 'message-received');
        var time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        messageRow.innerHTML = `
            <div class="message-bubble">
                ${!isMe ? `<div class="sender-name">${message.sender}</div>` : ''}
                <span>${message.content}</span>
                <span class="msg-footer">
                    <span class="msg-time">${time}</span>
                    ${isMe ? `<i class="fa-solid fa-check-double tick-blue"></i>` : ''}
                </span> 
            </div>
        `;
        const typingIndicator = document.getElementById('typing-status');
        if (typingIndicator) typingIndicator.style.display = "none";
        clearTimeout(window.typingTimer);
    }
    
    messageArea.appendChild(messageRow);
    messageArea.scrollTop = messageArea.scrollHeight;
}

function handleTypingEvent() {
    if (!currentRoom || !socket || socket.readyState !== WebSocket.OPEN) return;

    let timeNow = new Date().getTime();
    if (timeNow - lastTypingTime > TYPING_TIMER_LENGTH) {
        lastTypingTime = timeNow;
        
        var typingMessage = { 
            sender: username, 
            type: 'TYPING', 
            roomId: currentRoom 
        };
        
        socket.send(JSON.stringify(typingMessage));
    }
}

function loadHistory(roomName) {
    fetch(`/api/history?room=${roomName}`)
        .then(response => response.json())
        .then(messages => {
            messages.forEach(message => {
                // Backend schema updates room ID tracking perfectly
                message.roomId = roomName; 
                displayMessage(message);
            });
        }).catch(error => console.error("History load error:", error));
} 

function handleSearchInput(event) {
    const query = event.target.value.trim();
    const resultsDiv = document.getElementById('search-results');

    if (query.length === 0) {
        if (resultsDiv) resultsDiv.style.display = 'none';
        return;
    }

    clearTimeout(searchTimeout); 
    searchTimeout = setTimeout(() => {
        fetch(`/api/users/search?q=${query}`)
            .then(res => {
                if (res.status === 401) window.location.href = '/login';
                return res.json();
            })
            .then(users => displaySearchResults(users))
            .catch(err => console.error("Search Error:", err));
    }, 300);
}

function displaySearchResults(users) {
    const resultsDiv = document.getElementById('search-results');
    if (!resultsDiv) return; 

    resultsDiv.innerHTML = ''; 

    if (users.length === 0) {
        resultsDiv.innerHTML = '<div style="padding: 10px; color: #94a3b8; text-align: center;">No users found</div>';
    } else {
        users.forEach(user => {
            let initial = user.username.charAt(0).toUpperCase();
            resultsDiv.innerHTML += `
                <div class="search-item" onclick="startChatFromSearch('${user.username}')" style="display: flex; align-items: center; padding: 10px; cursor: pointer; border-bottom: 1px solid #334155;">
                    <div class="chat-avatar" style="margin-right: 10px;">${initial}</div>
                    <span class="chat-name">${user.username}</span>
                </div>
            `;
        });
    }
    resultsDiv.style.display = 'block'; 
}

function startChatFromSearch(targetUser) {
    const sortedNames = [username, targetUser].sort();
    const privateRoomName = sortedNames[0] + "_" + sortedNames[1];
    const searchInput = document.getElementById('search-input');
    const searchResults = document.getElementById('search-results');
    if (searchInput) searchInput.value = '';
    if (searchResults) searchResults.style.display = 'none';

    fetch('/api/rooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: privateRoomName })
    })
    .then(response => response.json())
    .then(room => {
        roomDetailsCache[room.name] = room;
        fetchAndDisplayRooms(false);
        setTimeout(() => openChannel(room.name), 200);
    })
    .catch(err => alert("Could not start chat: " + err.message));
}

function respondToRequest(statusAction) {
    if (!currentRoom) return;

    fetch(`/api/rooms/${currentRoom}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requestStatus: statusAction }) 
    })
    .then(response => {
        if (!response.ok) throw new Error("Failed to update status");
        return response.json();
    })
    .then(updatedRoom => {
        fetchAndDisplayRooms(false);
        setTimeout(() => checkRoomUIState(updatedRoom), 200); 
    })
    .catch(err => alert(err.message));
}

function checkRoomUIState(roomInfo) {
    const inputArea = document.getElementById('chat-input-area');
    const actionArea = document.getElementById('request-action-area');
    const blockedArea = document.getElementById('blocked-area');
    const messageInput = document.getElementById('message');
    const activeActions = document.getElementById('active-chat-actions');

    if (!inputArea || !actionArea || !blockedArea) return;

    inputArea.style.display = 'none';
    actionArea.style.display = 'none';
    blockedArea.style.display = 'none';
    if(activeActions) activeActions.style.display = 'none'; 
    messageInput.disabled = false;
    messageInput.placeholder = "Type a message";

    if (!roomInfo) {
        inputArea.style.display = 'flex'; 
        if(activeActions) activeActions.style.display = 'inline-block';
        return;
    }

    if (roomInfo.requestStatus === 'CLOSED') {
        blockedArea.style.display = 'block';
        let leaver = roomInfo.leftBy === username ? 'You' : roomInfo.leftBy;
        let leaveText = leaver === 'You' ? "You left this chat." : `${leaver} has left the room.`;
        
        if (roomInfo.closedAt) {
            let closedTime = new Date(roomInfo.closedAt);
            closedTime.setHours(closedTime.getHours() + 24); 

            let timeString = closedTime.toLocaleString('en-IN', { 
                day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: true 
            });

            blockedArea.innerHTML = `
                <div style="font-size: 16px;">${leaveText}</div>
                <div style="font-size: 13px; color: #fca5a5; margin-top: 6px; font-weight: normal;">
                    <i class="fa-regular fa-clock"></i> Chat will be permanently deleted on <b>${timeString}</b>
                </div>
            `;
        } else {
            blockedArea.innerText = leaveText;
        }
    }
    else if (roomInfo.requestStatus === 'REJECTED') {
        blockedArea.style.display = 'block';
        if (roomInfo.initiator === username) {
            blockedArea.innerText = "Your request was rejected.";
        } else {
            blockedArea.innerText = "You rejected this request.";
        }
    } 
    else if (roomInfo.requestStatus === 'PENDING') {
        if(activeActions) activeActions.style.display = 'inline-block'; 
        if (roomInfo.initiator === username) {
            inputArea.style.display = 'flex';
            messageInput.disabled = true;
            messageInput.placeholder = "Waiting for request to be accepted...";
        } else {
            actionArea.style.display = 'block';
        }
    } 
    else {
        inputArea.style.display = 'flex';
        if(activeActions) activeActions.style.display = 'inline-block'; 
    }
}

function toggleContextDropdown(event) {
    event.stopPropagation(); 
    if (!currentRoom) return;

    const dropdown = document.getElementById('header-context-dropdown');
    const roomInfo = roomDetailsCache[currentRoom];
    
    if (!dropdown || !roomInfo) return;

    if (dropdown.style.display === 'block') {
        dropdown.style.display = 'none';
        return;
    }

    dropdown.innerHTML = '';
    
    if (roomInfo.requestStatus === 'CLOSED' || roomInfo.requestStatus === 'REJECTED') {
        dropdown.innerHTML = `
            <div class="danger-action" onclick="deleteRoomPermanently()">
                <i class="fa-solid fa-trash-can"></i> Delete Room
            </div>
        `;
    } else {
        dropdown.innerHTML = `
            <div class="danger-action" onclick="leaveChat()">
                <i class="fa-solid fa-right-from-bracket"></i> Leave Chat
            </div>
        `;
    }
    
    dropdown.style.display = 'block';
}

function leaveChat() {
    if (!currentRoom) return;
    if (!confirm("Are you sure you want to leave this chat? You won't be able to send messages.")) return;

    fetch(`/api/rooms/${currentRoom}/leave`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' }
    })
    .then(response => { 
        if (!response.ok) throw new Error("Failed to leave chat");
        
        if(roomDetailsCache[currentRoom]) {
            roomDetailsCache[currentRoom].requestStatus = 'CLOSED';
            roomDetailsCache[currentRoom].leftBy = username;
        }

        currentRoom = null;
        document.getElementById('chat-main-area').style.display = 'none';
        document.getElementById('placeholder').style.display = 'flex';
        fetchAndDisplayRooms(false); 
    })
    .catch(err => alert(err.message));
}

function deleteRoomPermanently() {
    if (!currentRoom) return;
    if (!confirm("Danger! This will permanently wipe out the room from your view. Proceed?")) return;

    fetch(`/api/rooms/${currentRoom}/leave`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' }
    })
    .then(response => {
        if (!response.ok) throw new Error("Wipe out sequence failed");
        
        currentRoom = null;
        document.getElementById('chat-main-area').style.display = 'none';
        document.getElementById('placeholder').style.display = 'flex';
        fetchAndDisplayRooms(false);
    })
    .catch(err => alert(err.message));
}