// NAYA CHANGE: Modern JavaScript (ES6+) standard code
document.addEventListener("DOMContentLoaded", () => {
    
    console.log("Profile JS Loaded successfully!");

    const statusDropdown = document.querySelector('.status-dropdown');
    const statusDot = document.querySelector('.status-dot');
    const alertBox = document.getElementById('statusAlert');

    // Extra safety check
    if (!statusDropdown || !statusDot || !alertBox) {
        console.error("Warning: HTML elements nahi mile!");
        return;
    }

    // Dropdown change handler ko async banaya taaki await use kar sakein
    statusDropdown.addEventListener('change', async function() {
        const newStatus = this.value;
        console.log("Dropdown change detect hua! Nayi value:", newStatus); 
        
        // Dynamic class toggle (Modern way using classList.toggle)
        statusDot.classList.toggle('active', newStatus !== 'SLEEPING');
        statusDot.classList.toggle('sleeping', newStatus === 'SLEEPING');

        // CSRF Token Headers configuration
        const headers = { 'Content-Type': 'application/json' };
        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        
        if (csrfMeta && csrfHeaderMeta) {
            headers[csrfHeaderMeta.getAttribute('content')] = csrfMeta.getAttribute('content');
        }

        console.log("API ko request bhej rahe hain..."); 

        // Modern Async/Await with Try-Catch Block (No more ugly .then chains)
        try {
            const response = await fetch('/api/profile/status', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ status: newStatus })
            });

            // Agar backend se HTTP error code aaye (jaise 404 ya 500)
            if (!response.ok) {
                throw new Error(`Server responded with status: ${response.status}`);
            }

            const data = await response.json();
            console.log("Backend se response aagaya:", data); 

            if (data.error) {
                showAlert(data.error, 'error');
            } else {
                showAlert(data.message, 'success');
            }

        } catch (error) {
            console.error('Asli Error yahan hai:', error);
            showAlert('Failed to update database or network error.', 'error');
        }
    });

    // Arrow function aur clean template literals ka use kiya
    const showAlert = (message, type) => {
        alertBox.textContent = message;
        alertBox.className = `status-alert ${type}`; // Template literal syntax
        alertBox.style.display = 'block';
        
        setTimeout(() => { 
            alertBox.style.display = 'none'; 
        }, 3000);
    };

});