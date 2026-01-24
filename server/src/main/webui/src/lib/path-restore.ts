const key = "last-path";

export function recordPath() {
    window.localStorage.setItem(key, window.location.pathname)
}

export function getLastPath() {
    return window.localStorage.getItem(key) || "/"
}