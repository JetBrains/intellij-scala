package org.jetbrains.plugins.scala
package project

import org.jetbrains.plugins.scala.project.Version._

/**
 * @author Pavel Fatin
 */
case class Version(presentation: String) extends AnyVal with Comparable[Version] {
  private def groups: Seq[Seq[Int]] =
    presentation.split('-').map(findNumbers)

  def compareTo(other: Version): Int = { //0.1.1 > 0.1-20170107
    groups.zip(other.groups).collectFirst {
      case (a, b) if a != b => compareGroups(a, b)
    } getOrElse {0}
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
  private val IntegerPattern = "\\d+".r

  def abbreviate(presentation: String): String = presentation.split('-').take(2).mkString("-")

  private def findNumbers(s: String): Seq[Int] = IntegerPattern.findAllIn(s).toSeq.map(_.toInt)

  private def compareGroups(l: Seq[Int], r: Seq[Int]): Int = {
    l.zip(r).collectFirst {
      case (a, b) if a != b => a.compareTo(b)
    } getOrElse {
      l.size.compareTo(r.size)  //0.1.1 > 0.1
    }
  }
}

