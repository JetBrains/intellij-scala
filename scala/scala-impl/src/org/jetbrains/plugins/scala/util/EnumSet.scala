package org.jetbrains.plugins.scala.util

import scala.reflect.ClassTag

/** This trait is necessary for "opaque type" pattern, which allows to use EnumSet[E]
  * as a type-safe alternative to Int
  **/
trait EnumSetProvider {
  type EnumSet[E <: Enum[E]] <: Int

  def empty[E <: Enum[E]]: EnumSet[E]

  def single[E <: Enum[E]](e: E): EnumSet[E]

  def union[E <: Enum[E]](set1: EnumSet[E], set2: EnumSet[E]): EnumSet[E]

  def add[E <: Enum[E]](set:EnumSet[E], e: E): EnumSet[E]

  def contains[E <: Enum[E]](set: EnumSet[E], e: E): Boolean

  //for deserialization only
  def readFromInt[E <: Enum[E]](i: Int): EnumSet[E]
}

object EnumSetProvider {

  val instance: EnumSetProvider =
    new EnumSetProvider {

      type EnumSet[E <: Enum[E]] = Int

      override def empty[E <: Enum[E]]: EnumSet[E] = 0

      override def single[E <: Enum[E]](e: E): EnumSet[E] = 1 << e.ordinal()

      override def union[E <: Enum[E]](set1: EnumSet[E], set2: EnumSet[E]): EnumSet[E] = set1 | set2

      def intersect[E <: Enum[E]](set1: EnumSet[E], set2: EnumSet[E]): EnumSet[E] = set1 & set2

      override def add[E <: Enum[E]](set: EnumSet[E], e: E): EnumSet[E] = union(set, single(e))

      override def contains[E <: Enum[E]](set: EnumSet[E], e: E): Boolean = intersect(set, single(e)) == single(e)

      override def readFromInt[E <: Enum[E]](i: Int): EnumSet[E] = i
    }

}

object EnumSet {
  import EnumSetProvider.instance

  type EnumSet[E <: Enum[E]] = EnumSetProvider.instance.EnumSet[E]

  def empty[E <: Enum[E]]: EnumSet[E] = instance.empty

  def apply[E <: Enum[E]](e: E): EnumSet[E] = instance.single(e)

  def apply[E <: Enum[E]](e1: E, e2: E): EnumSet[E] = EnumSet(e1) ++ e2

  def apply[E <: Enum[E]](e1: E, e2: E, e3: E): EnumSet[E] = EnumSet(e1) ++ e2 ++ e3

  def apply[E <: Enum[E]](elems: E*): EnumSet[E] = elems.foldLeft(empty[E])(_ ++ _)

  def readFromInt[E <: Enum[E]](i: Int): EnumSet[E] = instance.readFromInt(i)

  private def values[E <: Enum[E]](implicit classTag: ClassTag[E]): Array[E] = {
    val aClass = classTag.runtimeClass.asInstanceOf[Class[E]]
    aClass.getEnumConstants
  }

  implicit class EnumSetOps[E <: Enum[E]](private val set: EnumSet[E]) extends AnyVal {
    //I would prefer `+` here, but it clashes with int addition
    def ++(e: E): EnumSet[E] = instance.add(set, e)

    def ++(e: EnumSet[E]): EnumSet[E] = instance.union(set, e)

    def contains(e: E): Boolean = instance.contains(set, e)

    def isEmpty: Boolean = set == EnumSet.empty

    def foreach(f: E => Unit)(implicit classTag: ClassTag[E]): Unit = toArray.foreach(f)

    def toArray(implicit classTag: ClassTag[E]): Array[E] = {
      values[E].filter(set.contains)
    }
  }
}
