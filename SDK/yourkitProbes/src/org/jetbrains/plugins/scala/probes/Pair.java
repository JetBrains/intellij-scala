package org.jetbrains.plugins.scala.probes;

/**
 * @author Nikolay.Tropin
 */
public class Pair<A, B> {
    public final A first;
    public final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }
}