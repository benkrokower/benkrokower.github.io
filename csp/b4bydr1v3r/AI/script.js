// Handle feedback form submission
function handleFeedback(event) {
    event.preventDefault();
    
    const name = document.getElementById('name').value;
    const feedbackMessage = document.getElementById('feedback-message');
    
    feedbackMessage.textContent = `Thanks ${name}! Your feedback has been received. Keep cheering for Lando! 🏁`;
    feedbackMessage.style.display = 'block';
    
    // Clear the form
    document.querySelector('.feedback-form').reset();
    
    // Hide message after 5 seconds
    setTimeout(() => {
        feedbackMessage.style.display = 'none';
    }, 5000);
}

// Smooth scrolling for navigation links
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        e.preventDefault();
        const target = document.querySelector(this.getAttribute('href'));
        if (target) {
            target.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    });
});

// Add click animation to play button
document.querySelector('.play-btn').addEventListener('mousedown', function() {
    this.style.transform = 'scale(0.95)';
});

document.querySelector('.play-btn').addEventListener('mouseup', function() {
    this.style.transform = 'scale(1.05)';
});
