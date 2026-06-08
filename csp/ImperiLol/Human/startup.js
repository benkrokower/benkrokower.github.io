const logo = document.querySelector(".gba-logo-text");

setInterval(() => {
    logo.style.visibility =
        logo.style.visibility === "hidden" ? "visible" : "hidden";
}, 1000);