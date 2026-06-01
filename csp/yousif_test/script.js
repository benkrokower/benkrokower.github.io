// ============================================
// HIGH SCORE MANAGEMENT
// ============================================

function updateHighScore(newScore) {
    let currentScore = localStorage.getItem('highScore') || 0;
    currentScore = parseInt(currentScore);
    
    if (newScore > currentScore) {
        localStorage.setItem('highScore', newScore);
        displayHighScore(newScore);
    }
}

function displayHighScore(score) {
    const display = document.getElementById('high-score-display');
    if (display) {
        display.textContent = `Best Score: ${score} points`;
    }
}

function resetScores() {
    localStorage.removeItem('highScore');
    displayHighScore(0);
    alert('High scores have been reset!');
}

// Load high score on page load
window.addEventListener('load', function() {
    const savedScore = localStorage.getItem('highScore') || 0;
    displayHighScore(savedScore);
});

// ============================================
// FEEDBACK FORM HANDLING
// ============================================

function handleSubmit(event) {
    event.preventDefault();
    
    const form = event.target;
    const name = form.querySelector('input[type="text"]').value;
    const feedback = form.querySelector('textarea').value;
    
    // Save feedback to localStorage
    let allFeedback = JSON.parse(localStorage.getItem('feedback')) || [];
    allFeedback.push({
        name: name,
        message: feedback,
        date: new Date().toLocaleDateString()
    });
    localStorage.setItem('feedback', JSON.stringify(allFeedback));
    
    // Show success
