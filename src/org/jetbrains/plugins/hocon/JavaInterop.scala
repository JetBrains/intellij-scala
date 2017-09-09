package org.jetbrains.plugins.hocon

import scala.collection.convert.{DecorateAsJava, DecorateAsScala}

object JavaInterop extends DecorateAsJava with DecorateAsScala {
  type JIterator[A] = java.util.Iterator[A]
  type JIterable[A] = java.lang.Iterable[A]
  type JCollection[A] = java.util.Collection[A]
  type JList[A] = java.util.List[A]
  type JArrayList[A] = java.util.ArrayList[A]
  type JLinkedList[A] = java.util.LinkedList[A]
  type JSet[A] = java.util.Set[A]
  type JHashSet[A] = java.util.HashSet[A]
  type JSortedSet[A] = java.util.SortedSet[A]
  type JNavigableSet[A] = java.util.NavigableSet[A]
  type JTreeSet[A] = java.util.TreeSet[A]
  type JMap[K, V] = java.util.Map[K, V]
  type JHashMap[K, V] = java.util.HashMap[K, V]
  type JLinkedHashMap[K, V] = java.util.LinkedHashMap[K, V]
  type JSortedMap[K, V] = java.util.SortedMap[K, V]
  type JNavigableMap[K, V] = java.util.NavigableMap[K, V]
  type JTreeMap[K, V] = java.util.TreeMap[K, V]

  object JList {
    def apply[A](values: A*): JList[A] =
      values.asJava
  }

}

