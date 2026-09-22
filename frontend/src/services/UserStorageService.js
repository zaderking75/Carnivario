import AuthService from "./AuthService";

class UserStorageService {
    getUserKey(storageName) {
        const user = AuthService.getCurrentUser();

        if (!user) {
            return null;
        }

        const userIdentifier = user.id ?? user.email;

        if (userIdentifier === undefined || userIdentifier === null) {
            return null;
        }

        return `carnivario:${storageName}:usuario:${userIdentifier}`;
    }

    get(storageName, defaultValue = []) {
        const key = this.getUserKey(storageName);

        if (!key) {
            return defaultValue;
        }

        try {
            const storedValue = localStorage.getItem(key);
            return storedValue
                ? JSON.parse(storedValue)
                : defaultValue;
        } catch (error) {
            console.error(
                `No se pudo leer ${storageName} del usuario:`,
                error
            );

            return defaultValue;
        }
    }

    set(storageName, value) {
        const key = this.getUserKey(storageName);

        if (!key) {
            return false;
        }

        localStorage.setItem(key, JSON.stringify(value));
        return true;
    }

    remove(storageName) {
        const key = this.getUserKey(storageName);

        if (key) {
            localStorage.removeItem(key);
        }
    }
}

export default new UserStorageService();