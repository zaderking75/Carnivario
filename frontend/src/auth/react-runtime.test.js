import { expect, it } from "vitest";
import { createElement } from "react";
import { createRoot } from "react-dom/client";
import { renderToString } from "react-dom/server";

it("carga los renderizadores de React y renderiza sin incompatibilidad de versiones", () => {
    expect(typeof createRoot).toBe("function");
    expect(renderToString(createElement("h1", null, "Carnivario")))
        .toBe("<h1>Carnivario</h1>");
});
