const CLASS_DARK_MODE = "dark"
const KEY_THEME = "theme"

export function isDarkMode(): boolean {
    const htmlElement = document.getElementsByTagName("html")[0];
    return htmlElement.classList.contains(CLASS_DARK_MODE);
}

export function toggleDarkMode() {
    const htmlElement = document.getElementsByTagName("html")[0];
    const darkMode = htmlElement.classList.toggle(CLASS_DARK_MODE);

    if (darkMode) {
        localStorage.setItem(KEY_THEME, CLASS_DARK_MODE)
    } else {
        localStorage.removeItem(KEY_THEME);
    }
}

export function restoreTheme() {
    const htmlElement = document.getElementsByTagName("html")[0];
    const current = htmlElement.classList.contains(CLASS_DARK_MODE);

    const theme = localStorage.getItem(KEY_THEME);
    if (!current && theme) {
        htmlElement.classList.add(CLASS_DARK_MODE);
    } else if (current && !theme) {
        htmlElement.classList.remove(CLASS_DARK_MODE);
    }
}
