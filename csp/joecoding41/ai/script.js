// SMOOTH SCROLLING FOR NAVIGATION LINKS
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

// START PLAYING BUTTON
const ctaButton = document.querySelector('.cta-button');
if (ctaButton) {
    ctaButton.addEventListener('click', function() {
        document.querySelector('#game').scrollIntoView({
            behavior: 'smooth'
        });
    });
}

// CONTACT BUTTON
const contactButton = document.querySelector('.contact-button');
if (contactButton) {
    contactButton.addEventListener('click', function() {
        alert('Thanks for your interest! Feedback feature coming soon.');
    });
}

// ADD GLOW EFFECT ON HOVER
const buttons = document.querySelectorAll('.cta-button, .contact-button');
buttons.forEach(button => {
    button.addEventListener('mouseenter', function() {
        this.style.boxShadow = '0 0 30px #39FF14, 0 0 60px #39FF14';
    });
    
    button.addEventListener('mouseleave', function() {
        this.style.boxShadow = '';
    });
});