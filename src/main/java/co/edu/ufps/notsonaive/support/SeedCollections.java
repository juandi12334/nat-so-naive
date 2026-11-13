package co.edu.ufps.notsonaive.support;

import ufps.util.colecciones_seed.ListaCD;
import ufps.util.colecciones_seed.Secuencia;

/**
 * Helpers around the SEED data structures used as support structures in this
 * project (as required by the assignment):
 * <ul>
 *   <li>{@link ListaCD}: circular doubly linked list with header node; used when the
 *       final size is unknown, because {@code insertarAlFinal} is O(1).</li>
 *   <li>{@link Secuencia}: fixed-capacity array; used once the size is known, because
 *       {@code get(i)} is O(1).</li>
 * </ul>
 */
public final class SeedCollections {

    private SeedCollections() {
    }

    /**
     * Copies a SEED list into a SEED sequence of exact capacity, keeping the order.
     * Runs in O(size).
     */
    public static <T> Secuencia<T> toSecuencia(ListaCD<T> list) {
        // Secuencia rejects a capacity of 0, so an empty list gets capacity 1 (size stays 0).
        Secuencia<T> sequence = new Secuencia<>(Math.max(1, list.getTamanio()));
        for (T element : list) {
            sequence.insertar(element);
        }
        return sequence;
    }

    /** Copies a SEED list of integers into a primitive array, keeping the order. */
    public static int[] toIntArray(ListaCD<Integer> list) {
        int[] out = new int[list.getTamanio()];
        int i = 0;
        for (Integer value : list) {
            out[i++] = value;
        }
        return out;
    }
}
