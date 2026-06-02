// ===== SCORE TRACKING =====
// Initialize score from localStorage (saves even after page refresh)
let score = localStorage.getItem('highFiveScore') ? parseInt(localStorage.getItem('highFiveScore')) : 0;

// Display score when page loads
document.addEventListener('DOMContentLoaded', function() {
    updateScoreDisplay();
});

// Increment score when user clicks "I Just Played!"
function incrementScore() {
    score++;
    localStorage.setItem('highFiveScore', score);
    updateScoreDisplay();
    showScoreAnimation();
}

// Update the score display on the page
function updateScoreDisplay() {
    document.getElementById('score').textContent = score;
}

// Show a fun animation when score increases
function showScoreAnimation() {
    const scoreElement = document.getElementById('score');
    scoreElement.style.transform = 'scale(1.2)';
    setTimeout(() => {
        scoreElement.style.transform = 'scale(1)';
    }, 200);
