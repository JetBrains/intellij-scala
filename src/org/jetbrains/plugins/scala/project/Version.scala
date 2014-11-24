package org.jetbrains.plugins.scala
package project

/**
 * @author Pavel Fatin
 */
case class Version(number: String) extends AnyVal with Comparable[Version] {
  def digits: Stream[Int] = Version.IntegerPattern.findAllIn(number).map(_.toInt).toStream

  def compareTo(other: Version) = (digits, other.digits).zipped.collectFirst {
    case (a, b) if a != b => a.compareTo(b)
  } getOrElse {
    0
  }

  def >(other: Version): Boolean = compareTo(other) > 0

  def >=(other: Version): Boolean = compareTo(other) >= 0

  def <(other: Version): Boolean = compareTo(other) < 0

  def <=(other: Version): Boolean = compareTo(other) < 0

  def toLanguageLevel: Option[ScalaLanguageLevel] = ScalaLanguageLevel.from(this)
}

object Version {
  private val IntegerPattern = "\\d+".r
}

