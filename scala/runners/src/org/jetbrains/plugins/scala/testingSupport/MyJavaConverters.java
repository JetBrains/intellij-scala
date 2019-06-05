package org.jetbrains.plugins.scala.testingSupport;

import scala.collection.Iterator;
import scala.collection.Seq;
import scala.collection.Seq$;
import scala.collection.mutable.Builder;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for Scala <--> Java collections conversions.
 * It is needed due to {@link scala.collection.JavaConverters} and other standard library collection classes
 * are changed quite frequently without backward compatibility
 */
public class MyJavaConverters {

  public static <T> List<T> asJava(scala.collection.immutable.Seq<T> seq) {
    ArrayList<T> list = new ArrayList<>(seq.size());
    Iterator<T> iterator = seq.iterator();
    while (iterator.hasNext()) {
      list.add(iterator.next());
    }
    return list;
  }

  @SuppressWarnings("unchecked")
  public static <T> Seq<T> asScala(List<T> list) {
    Builder<Object, Seq<Object>> builder = Seq$.MODULE$.newBuilder();
    for (T item : list) {
      builder.$plus$eq(item);
    }
    return (Seq<T>) builder.result();
  }

}
