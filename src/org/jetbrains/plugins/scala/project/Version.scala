package org.jetbrains.plugins.scala
package project

/**
 * @author Pavel Fatin
 */
case class Version(presentation: String) extends Comparable[Version] {
  private val groups: Seq[Group] = presentation.split('-').map(Group(_))

  def compareTo(other: Version): Int = {
    groups.zip(other.groups).collectFirst {
      case (g1, g2) if g1 != g2 => g1.compareTo(g2)
    } getOrElse {
      groups.lengthCompare(other.groups.length)
    }
  }

  def >(other: Version): Boolean = compareTo(other) > 0

  def >=(other: Version): Boolean = compareTo(other) >= 0

  def <(other: Version): Boolean = compareTo(other) < 0

  def <=(other: Version): Boolean = compareTo(other) <= 0

  // Returns whether this version is equal to or more specific than the other version
  def ~=(other: Version): Boolean = {
    groups.zip(other.groups).collectFirst {
      case (g1, g2) if !(g1 ~= g2) => false
    } getOrElse {
      groups.lengthCompare(other.groups.length) >= 0
    }
  }

  def toLanguageLevel: Option[ScalaLanguageLevel] = ScalaLanguageLevel.from(this)
}

object Version {
  def abbreviate(presentation: String): String = presentation.split('-').take(2).mkString("-")
}

private case class Group(numbers: Seq[Int]) extends Comparable[Group] {
  override def compareTo(other: Group): Int = {
    numbers.zip(other.numbers).collectFirst {
      case (n1, n2) if n1 != n2 => n1.compareTo(n2)
    } getOrElse {
      numbers.lengthCompare(other.numbers.size)
    }
  }

  def ~=(other: Group): Boolean = {
    numbers.zip(other.numbers).collectFirst {
      case (n1, n2) if n1 != n2 => false
    } getOrElse {
      numbers.lengthCompare(other.numbers.length) >= 0
    }
  }
}

private object Group {
  private val IntegerPattern = "\\d+".r

  def apply(presentation: String): Group =
    Group(IntegerPattern.findAllIn(presentation).map(_.toInt).toSeq)
}