package org.jetbrains.plugins.scala
package project

import org.jetbrains.plugins.scala.project.Version._

/**
 * @author Pavel Fatin
 */
case class Version(versionString: String) extends AnyVal with Comparable[Version] {

  def groups: Seq[Seq[Int]] = {
    versionString.split('-').map(findNumbers)
  }

  def compareTo(other: Version): Int = { //0.1.1 > 0.1-20170107
    groups.zip(other.groups).collectFirst {
      case (a, b) if a != b => compareGroups(a, b)
    } getOrElse {0}
  }

  def >(other: Version): Boolean = compareTo(other) > 0

  def >=(other: Version): Boolean = compareTo(other) >= 0

  def <(other: Version): Boolean = compareTo(other) < 0

  def <=(other: Version): Boolean = compareTo(other) <= 0

  def toLanguageLevel: Option[ScalaLanguageLevel] = ScalaLanguageLevel.from(this)
}

object Version {
  private val IntegerPattern = "\\d+".r

  private def findNumbers(s: String): Seq[Int] = IntegerPattern.findAllIn(s).toSeq.map(_.toInt)

  private def compareGroups(l: Seq[Int], r: Seq[Int]): Int = {
    l.zip(r).collectFirst {
      case (a, b) if a != b => a.compareTo(b)
    } getOrElse {
      l.size.compareTo(r.size)  //0.1.1 > 0.1
    }
  }
}

