package org.jetbrains.plugins.scala
package project

import scala.Ordering.Implicits._

/**
 * @author Pavel Fatin
 */
// TODO Make universal (it seems that this class is now used in lots of places ourside the "proect" package).
case class Version(presentation: String) extends Ordered[Version] {

  import Version._

  private val strings = presentation.split('-').toSeq
  private val groups = strings.map(Group(_))

  private val essentialGroups: Seq[Group] =
    groups.reverse.dropWhile(_.essentialNumbers.forall(_ == 0L)).reverse

  def compare(other: Version): Int =
    implicitly[Ordering[Seq[Group]]].compare(essentialGroups, other.essentialGroups)

  /** Returns whether this version is equal to or more specific than the other version.
   * A version is "more specific" if LHS contains more digits than RHS.
   */
  def ~=(other: Version): Boolean =
    zipLeft(groups, other.groups, Group(Seq(0l))).forall {
      case (l, r) => l ~= r
    } && groups.lengthCompare(other.groups.length) >= 0

  /**
   * The major version of this version, in terms of the first n numbers of the dotted-numbers format.
   * E.g. Version("1.2.3-M3").major(2) == Version("1.2")
   */
  def major(n: Int): Version = Version(groups.head.numbers.take(n).mkString("."))

  def inRange(atLeast: Version, lessThan: Version): Boolean =
    this >= atLeast && this < lessThan

  override def toString: String = groups.mkString("-")
}

object Version {

  val Default = "Unknown"

  def abbreviate(presentation: String): String = {
    val groups = Version(presentation).strings
    groups.take(if (groups.last == "NIGHTLY") 3 else 2)
      .mkString("-")
  }

  private case class Group(numbers: Seq[Long]) extends Ordered[Group] {
    val essentialNumbers: Seq[Long] =
      numbers.reverse.dropWhile(_ == 0L).reverse

    override def compare(other: Group): Int =
      implicitly[Ordering[Seq[Long]]].compare(essentialNumbers, other.essentialNumbers)

    def ~=(other: Group): Boolean = {
      zipLeft(numbers, other.numbers, 0).forall {
        case (n1, n2) => n1 == n2
      } && essentialNumbers.lengthCompare(other.essentialNumbers.length) >= 0
    }

    override def toString: String = numbers.mkString(".")
  }

  private object Group {
    private val IntegerPattern = "\\d+".r

    def apply(presentation: String): Group =
      Group(IntegerPattern.findAllIn(presentation).map(_.toLong).toList)
  }

  /** zips and pads elements if left is shorter, but not right */
  private def zipLeft[A](left: Seq[A], right: Seq[A], fill: A): Seq[(A, A)] = {
    var zipped = Seq.newBuilder[(A, A)]

    val lefts = left.iterator
    val rights = right.iterator
    while (lefts.hasNext && rights.hasNext)
      zipped += ((lefts.next(), rights.next()))
    while (rights.hasNext)
      zipped += ((fill, rights.next()))

    zipped.result()
  }
}