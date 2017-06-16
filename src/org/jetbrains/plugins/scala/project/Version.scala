package org.jetbrains.plugins.scala
package project

import Ordering.Implicits._

/**
 * @author Pavel Fatin
 */
case class Version(presentation: String) extends Ordered[Version] {
  private val groups: Seq[Group] = presentation.split('-').map(Group(_))

  def compare(other: Version): Int =
    implicitly[Ordering[Seq[Group]]].compare(groups, other.groups)

  /** Returns whether this version is equal to or more specific than the other version. */
  def ~=(other: Version): Boolean =
    groups.zip(other.groups).forall(p => p._1 ~= p._2) &&
      groups.lengthCompare(other.groups.length) >= 0

  /**
    * The major version of this version, in terms of the first n numbers of the dotted-numbers format.
    * E.g. Version("1.2.3-M3").major(2) == Version("1.2")
    */
  def major(n: Int): Version = Version(groups.head.numbers.take(n).mkString("."))

  def toLanguageLevel: Option[ScalaLanguageLevel] = ScalaLanguageLevel.from(this)

  override def toString: String = groups.map(_.numbers.mkString(".")).mkString("-")
}

object Version {
  def abbreviate(presentation: String): String = presentation.split('-').take(2).mkString("-")
}

private case class Group(numbers: Seq[Long]) extends Comparable[Group] {
  override def compareTo(other: Group): Int =
    implicitly[Ordering[Seq[Long]]].compare(numbers, other.numbers)

  def ~=(other: Group): Boolean =
    numbers.zip(other.numbers).forall(p => p._1 == p._2) &&
      numbers.lengthCompare(other.numbers.length) >= 0
}

private object Group {
  private val IntegerPattern = "\\d+".r

  def apply(presentation: String): Group =
    Group(IntegerPattern.findAllIn(presentation).map(_.toLong).toList)
}