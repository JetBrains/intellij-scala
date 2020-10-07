package org.jetbrains.plugins.scala.testingSupport;

import scala.collection.Iterator;
import scala.collection.immutable.$colon$colon; //NOTE: $colon$colon is highlighted red but it is actually resolved
import scala.collection.immutable.Nil$;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Helper class for Scala <--> Java collections conversions that can be used in any scala version without cross compilation.<br>
 * Methods of this class are "linked" at runtime and for that reason need to be compatible with different versions of Scala.<br>
 * It is needed because {@link scala.collection.JavaConverters} and other standard library collection classes
 * are changed quite frequently without backward compatibility.
 */
public class MyJavaConverters {

  public static <T> java.util.List<T> toJava(scala.collection.Seq<T> seq) {
    ArrayList<T> list = new ArrayList<>(seq.size());
    Iterator<T> iterator = seq.iterator();
    while (iterator.hasNext()) {
      list.add(iterator.next());
    }
    return list;
  }

  /**
   * Construct scala List manually by allocating head & tails.
   * <p>
   * NOTE: We cant just use some conversion method/Builders/Buffers from scala standard library.
   * Couldn't find some common methods that would exist in scala 2.13 and scala 2.12.
   * <p>
   * For example, we can't just use `builder.$plus$eq(item)` because `+=` method is defined:<br>
   *     <ul>
   *         <li>in 2.12 in `scala.collection.mutable.Builder`</li>
   *         <li>in 2.13 in `scala.collection.mutable.Growable</li>
   *     </ul>
   * <p>
   * Builder extends Growable, BUT:
   * <ul>
   *     <li>in 2.12 Growable has base package `scala.collection.generic`</li>
   *     <li>in 2.13 Growable has base package `scala.collection.mutable`</li>
   * </ul>
   * AND
   * <ul>
   *     <li>in 2.12 Builder defines `+=` method</li>
   *     <li>in 2.13 Builder defines `+=` method</li>
   * </ul>
   * This breaks JVM virtual method dispatch in runtime.
   */
  @SuppressWarnings({"unchecked", "ConstantConditions"})
  public static <T> scala.collection.immutable.List<T> toScala(java.util.List<T> list) {
    scala.collection.immutable.List head = Nil$.MODULE$;
    for (int idx = list.size() - 1; idx >= 0; idx--) {
      head = new $colon$colon(list.get(idx), head);
    }
    return (scala.collection.immutable.List<T>) head;
  }
}
