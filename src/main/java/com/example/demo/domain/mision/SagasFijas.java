package com.example.demo.domain.mision;

import java.util.List;

/**
 * Las sagas «armadas» de Cinemarketer: las mismas que se ven en el asistente del feed (Por característica › Saga).
 * Son las ÚNICAS que se usan para las misiones de saga. El id es el de la colección en TMDB.
 *
 * IMPORTANTE: esta lista es una copia de BUSCADOR_SAGAS de feed-films.js. Cuando se suma una saga allá, hay que sumarla también acá
 * (y volver a tocar «Actualizar ahora» en el admin para que aparezca su misión).
 */
public final class SagasFijas {
    private SagasFijas() {}

    public record SagaFija(long id, String nombre) {}

    public static final List<SagaFija> TODAS = List.of(
            new SagaFija(10, "Star Wars"),
            new SagaFija(84, "Indiana Jones"),
            new SagaFija(119, "El Señor de los Anillos"),
            new SagaFija(151, "Star Trek"),
            new SagaFija(528, "Terminator"),
            new SagaFija(656, "Saw"),
            new SagaFija(748, "X-Men"),
            new SagaFija(230, "El Padrino"),
            new SagaFija(263, "Batman - El Caballero de la Noche"),
            new SagaFija(264, "Volver al Futuro"),
            new SagaFija(295, "Piratas del Caribe"),
            new SagaFija(304, "Ocean's"),
            new SagaFija(328, "Jurassic Park"),
            new SagaFija(1575, "Rocky"),
            new SagaFija(2150, "Shrek"),
            new SagaFija(2344, "Matrix"),
            new SagaFija(8650, "Transformers"),
            new SagaFija(9485, "Rápidos y Furiosos"),
            new SagaFija(9735, "Viernes 13"),
            new SagaFija(10194, "Toy Story"),
            new SagaFija(12263, "El Exorcista"),
            new SagaFija(1241, "Harry Potter"),
            new SagaFija(87359, "Misión Imposible"),
            new SagaFija(404609, "John Wick"),
            new SagaFija(8091, "Alien"),
            new SagaFija(91361, "Halloween"),
            new SagaFija(2602, "Scream"),
            new SagaFija(313086, "El Conjuro"),
            new SagaFija(17255, "Resident Evil"),
            new SagaFija(8945, "Mad Max"),
            new SagaFija(1570, "Duro de Matar"),
            new SagaFija(8580, "Karate Kid"),
            new SagaFija(8581, "Pesadilla en la Calle Elm"),
            new SagaFija(228446, "Insidious"),
            new SagaFija(2980, "Cazafantasmas"),
            new SagaFija(495527, "Jumanji"),
            new SagaFija(131635, "Los Juegos del Hambre"),
            new SagaFija(8354, "La Era de Hielo"),
            new SagaFija(86066, "Mi Villano Favorito"),
            new SagaFija(14740, "Madagascar"),
            new SagaFija(8864, "Destino Final"),
            new SagaFija(33514, "Crepúsculo"),
            new SagaFija(52984, "La Búsqueda"),
            new SagaFija(391860, "Kingsman"));
}
