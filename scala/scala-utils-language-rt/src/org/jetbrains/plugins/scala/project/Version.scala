package org.jetbrains.plugins.scala.project

import org.jetbrains.annotations.NonNls

import scala.math.Ordering.Implicits.seqOrdering
import scala.math.Ordered.orderingToOrdered

// TODO Make universal (move to a proper package)
//  (it seems that this class is now used in lots of places outside the "project" package).
case class Version(@NonNls presentation: String) extends Ordered[Version] {

  import Version._

  private val dashParts = presentation.split('-').toSeq
  private val groups = dashParts.zipWithIndex.map { case (part, idx) => Group(part, idx == 0) }

  private val essentialGroups: Seq[Group] =
    groups.reverse.dropWhile(_.isNotEssential).reverse

  override def compare(other: Version): Int = {
    val emptyGroup = Group(Seq.empty, VersionStatus.DEFAULT)
    val (groups1, groups2) = essentialGroups.zipAll(other.essentialGroups, emptyGroup, emptyGroup).unzip
    groups1 compare groups2
  }

  /** Returns whether this version is equal to or more specific than the other version.
   * A version is "more specific" if LHS contains more digits than RHS.
   */
  def ~=(other: Version): Boolean =
    zipLeft(groups, other.groups, Group(Seq(0L), VersionStatus.DEFAULT)).forall {
      case (l, r) => l ~= r
    } && groups.lengthCompare(other.groups.length) >= 0

  /**
   * The major version of this version, in terms of the first n numbers of the dotted-numbers format.
   * E.g., `Version("1.2.3-M3").major(2) == Version("1.2")`
   */
  def major(n: Int): Version = Version(groups.head.numbers.take(n).mkString("."))

  def inRange(atLeast: Version, lessThan: Version): Boolean =
    this >= atLeast && this < lessThan

  override def toString: String = dashParts.mkString("-")
}

object Version {

  val Default = "Unknown"

  def abbreviate(presentation: String): String = {
    val groups = Version(presentation).dashParts
    groups.take(if (groups.last == "NIGHTLY") 3 else 2)
      .mkString("-")
  }

  private case class Group(numbers: Seq[Long], status: VersionStatus) extends Ordered[Group] {
    private val essentialNumbers: Seq[Long] =
      numbers.reverse.dropWhile(_ == 0L).reverse

    override def compare(other: Group): Int = {
      (status, essentialNumbers) compare (other.status, other.essentialNumbers)
    }

    def ~=(other: Group): Boolean = {
      zipLeft(numbers, other.numbers, 0).forall {
        case (n1, n2) => n1 == n2
      } && essentialNumbers.lengthCompare(other.essentialNumbers.length) >= 0
    }

    override def toString: String = numbers.mkString(".")

    def isNotEssential: Boolean = status == VersionStatus.DEFAULT && essentialNumbers.forall(_ == 0L)
  }

  private object Group {

    def apply(presentation: String, isFirstGroup: Boolean): Group = {
      val prefix =
        if (presentation.startsWith("M")) VersionStatus.MILESTONE
        else if (presentation.startsWith("RC")) VersionStatus.RC
        else if (isFirstGroup) VersionStatus.DEFAULT
        else VersionStatus.OTHER
      Group(findAllNumbersInVersion(presentation), prefix)
    }
  }

  /** zips and pads elements if left is shorter, but not right */
  private def zipLeft[A](left: Seq[A], right: Seq[A], fill: A): Seq[(A, A)] = {
    val zipped = Seq.newBuilder[(A, A)]

    val lefts = left.iterator
    val rights = right.iterator
    while (lefts.hasNext && rights.hasNext)
      zipped += ((lefts.next(), rights.next()))
    while (rights.hasNext)
      zipped += ((fill, rights.next()))

    zipped.result()
  }

  def findAllNumbersInVersion(version: String): Seq[Long] = {
    val IntegerPattern = "\\d+".r
    IntegerPattern.findAllIn(version).map(_.toLong).toList
  }
}