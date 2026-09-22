import UserStorageService from "./UserStorageService";

class FavoritoService {
    getFavoritosIDs() {
        return UserStorageService.get("favoritos", []);
    }

    toggleFavorito(id) {
        let favoritos = this.getFavoritosIDs();

        if (favoritos.includes(id)) {
            favoritos = favoritos.filter(
                (favId) => favId !== id
            );
        } else {
            favoritos.push(id);
        }

        UserStorageService.set("favoritos", favoritos);

        return favoritos;
    }

    esFavorito(id) {
        const favoritos = this.getFavoritosIDs();

        return favoritos.includes(id);
    }
}

export default new FavoritoService();