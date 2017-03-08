package org.jetbrains.plugins.scala
package project

/**
 * @author Pavel Fatin
 */
case class Version(presentation: String) extends Comparable[Version] {
  private val groups: Seq[Group] = presentation.split('-').map(Group(_))

  def compareTo(other: Version): Int = {
    groups.zip(other.groups).collectFirst {
      case (a, b) if a != b => a.compareTo(b)
    } getOrElse {
      groups.lengthCompare(other.groups.length)
    }
  }

  def >(other: Version): Boolean = compareTo(other) > 0

  def >=(other: Version): Boolean = compareTo(other) >= 0

  def <(other: Version): Boolean = compareTo(other) < 0

  def <=(other: Version): Boolean = compareTo(other) <= 0

  /*
   Returns whether all the comparable parts are equal, e.g:
     2.10   ~=  2.10
     2.10   ~=  2.10.1
     2.10.1 ~=  2.10

     2.10  !~=  2.11
  */
  def ~=(version: Version): Boolean =
    groups.zip(version.groups).forall(==)

  def toLanguageLevel: Option[ScalaLanguageLevel] = ScalaLanguageLevel.from(this)
}

object Version {
  def abbreviate(presentation: String): String = presentation.split('-').take(2).mkString("-")
}

private case class Group(numbers: Seq[Int]) extends Comparable[Group] {
  override def compareTo(other: Group): Int = {
    numbers.zip(other.numbers).collectFirst {
      case (a, b) if a != b => a.compareTo(b)
    } getOrElse {
      numbers.lengthCompare(other.numbers.size)
    }
  }
}

private object Group {
  def apply(presentation: String): Group = Group(presentation.split('.').map(_.toInt))
}