import UserStorageService from "./UserStorageService";

class CarritoService {
    getCarrito() {
        return UserStorageService.get("carrito", []);
    }

    guardarCarrito(carrito) {
        return UserStorageService.set("carrito", carrito);
    }

    vaciarCarrito() {
        UserStorageService.remove("carrito");
    }
}

export default new CarritoService();