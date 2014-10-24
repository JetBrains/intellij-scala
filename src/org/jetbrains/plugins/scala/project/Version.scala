package org.jetbrains.plugins.scala
package project

/**
 * @author Pavel Fatin
 */
case class Version(number: String) extends AnyVal {
  def toLanguageLevel: Option[ScalaLanguageLevel] = ScalaLanguageLevel.from(this)
}

object Version {
  implicit object VersionOrdering extends Ordering[Version] {
    private val IntegerPattern = "\\d+".r

    override def compare(x: Version, y: Version) = (integersIn(x.number), integersIn(y.number)).zipped.collectFirst {
      case (a, b) if a != b => a.compareTo(b)
    } getOrElse {
      0
    }

    private def integersIn(number: String): Stream[Int] =
      IntegerPattern.findAllIn(number).map(_.toInt).toStream
  }
}

