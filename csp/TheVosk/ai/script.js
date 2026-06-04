// script.js — The Cybird on the Run
// Interactive functionality for the CMU Game Showcase page

// =====================
// 1. Rating display updater
// =====================
function updateRating(value) {
  const stars = ["", "⭐", "⭐⭐", "⭐⭐⭐", "⭐⭐⭐⭐", "⭐⭐⭐⭐⭐"];
  const display = document.getElementById("rating-display");
  if (display) {
    display.textContent = stars[parseInt(value)];
  }
}

// =====================
// 2. Feedback form submission
// =====================
function submitFeedback() {
  const name = document.getElementById("name").value.trim();
  const email = document.getElementById("email").value.trim();
  const comments = document.getElementById("comments").value.trim();
  const msg = document.getElementById("form-msg");

  // Basic validation
  if (!name || !comments) {
    alert("Please fill in your name and comments before submitting!");
    return;
  }

  // Show thank-you message
  msg.style.display = "block";
  msg.textContent = `Thanks, ${name}! Your feedback has been (not)received. 🐦`;

  // Clear the form fields
  document.getElementById("name").value = "";
  document.getElementById("email").value = "";
  document.getElementById("comments").value = "";
  document.getElementById("rating").value = 5;
  updateRating(5);
}

// =====================
// 3. Navbar scroll effect — shrink on scroll
// =====================
window.addEventListener("scroll", function () {
  const navbar = document.getElementById("navbar");
  if (window.scrollY > 60) {
    navbar.style.padding = "8px 40px";
    navbar.style.borderBottomColor = "#e05c00";
  } else {
    navbar.style.padding = "14px 40px";
    navbar.style.borderBottomColor = "#fb972e";
  }
});

// =====================
// 4. Highlight active nav link based on scroll position
// =====================
window.addEventListener("scroll", function () {
  const sections = document.querySelectorAll("section[id], header[class='hero']");
  const navLinks = document.querySelectorAll(".nav-links a");

  let current = "";
  sections.forEach(function (section) {
    const sectionTop = section.offsetTop - 80;
    if (window.scrollY >= sectionTop) {
      current = section.getAttribute("id") || "play";
    }
  });

  navLinks.forEach(function (link) {
    link.style.color = "";
    if (link.getAttribute("href") === "#" + current) {
      link.style.color = "#fb972e";
    }
  });
});

// =====================
// 5. Animate sections into view on scroll
// =====================
function revealOnScroll() {
  const sections = document.querySelectorAll(".section, .alt-section");
  sections.forEach(function (el) {
    const rect = el.getBoundingClientRect();
    if (rect.top < window.innerHeight - 80) {
      el.style.opacity = "1";
      el.style.transform = "translateY(0)";
    }
  });
}

// Set initial state for animation
document.addEventListener("DOMContentLoaded", function () {
  const sections = document.querySelectorAll(".section, .alt-section");
  sections.forEach(function (el) {
    el.style.opacity = "0";
    el.style.transform = "translateY(24px)";
    el.style.transition = "opacity 0.5s ease, transform 0.5s ease";
  });
  revealOnScroll(); // Run once on load
});

window.addEventListener("scroll", revealOnScroll);
