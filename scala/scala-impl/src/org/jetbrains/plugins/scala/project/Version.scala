package org.jetbrains.plugins.scala
package project

import scala.Ordering.Implicits._

/**
 * @author Pavel Fatin
 */
// TODO Make universal (it seems that this class is now used in lots of places ourside the "proect" package).
case class Version(presentation: String) extends Ordered[Version] {
  private val groups: Seq[Group] = presentation.split('-').map(Group(_))

  private val essentialGroups: Seq[Group] =
    groups.reverse.dropWhile(_.essentialNumbers.forall(_ == 0L)).reverse

  def compare(other: Version): Int =
    implicitly[Ordering[Seq[Group]]].compare(essentialGroups, other.essentialGroups)

  /** Returns whether this version is equal to or more specific than the other version. */
  def ~=(other: Version): Boolean =
    essentialGroups.zip(other.essentialGroups).forall(p => p._1 ~= p._2) &&
      essentialGroups.lengthCompare(other.essentialGroups.length) >= 0

  /**
    * The major version of this version, in terms of the first n numbers of the dotted-numbers format.
    * E.g. Version("1.2.3-M3").major(2) == Version("1.2")
    */
  def major(n: Int): Version = Version(groups.head.numbers.take(n).mkString("."))

  def toLanguageLevel: Option[ScalaLanguageLevel] = ScalaLanguageLevel.Values.find { level =>
    presentation.startsWith(level.version)
  }

  override def toString: String = groups.map(_.toString).mkString("-")
}

object Version {
  def abbreviate(presentation: String): String = presentation.split('-').take(2).mkString("-")
}

private case class Group(numbers: Seq[Long]) extends Comparable[Group] {
  val essentialNumbers: Seq[Long] =
    numbers.reverse.dropWhile(_ == 0L).reverse

  override def compareTo(other: Group): Int =
    implicitly[Ordering[Seq[Long]]].compare(essentialNumbers, other.essentialNumbers)

  def ~=(other: Group): Boolean =
    essentialNumbers.zip(other.essentialNumbers).forall(p => p._1 == p._2) &&
      essentialNumbers.lengthCompare(other.essentialNumbers.length) >= 0

  override def toString: String = numbers.mkString(".")
}

private object Group {
  private val IntegerPattern = "\\d+".r

  def apply(presentation: String): Group =
    Group(IntegerPattern.findAllIn(presentation).map(_.toLong).toList)
}