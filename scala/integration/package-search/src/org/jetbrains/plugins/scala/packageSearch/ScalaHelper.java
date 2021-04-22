package org.jetbrains.plugins.scala.packageSearch;

import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;

import java.util.Iterator;

public class ScalaHelper {
    static <T> Sequence<T> toKotlinSequence(Iterator<T> it) {
        return SequencesKt.asSequence(it);
    }
}
