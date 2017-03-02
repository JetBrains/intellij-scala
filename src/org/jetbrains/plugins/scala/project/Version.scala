package org.jetbrains.plugins.scala
package project

/**
 * @author Pavel Fatin
 */
case class Version(number: String) extends AnyVal with Comparable[Version] {

  def digitGroups: Seq[Seq[Int]] = {
    val groups = number.split('-')
    groups.map {
      Version.IntegerPattern.findAllIn(_).toSeq.map(_.toInt)
    }
  }

  def compareTo(other: Version): Int = { //0.1.1 is bigger than 0.1-20170107
    def compareGroups(l: Seq[Int], r: Seq[Int]) = {
      l.zip(r).collectFirst {
        case (a, b) if a != b => a.compareTo(b)
      } getOrElse {
        l.size.compareTo(r.size)
      }
    }

    digitGroups.zip(other.digitGroups).collectFirst {
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
}

