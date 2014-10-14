package org.jetbrains.plugins.scala
package project

/**
 * @author Pavel Fatin
 */
case class Version(value: String) extends AnyVal

object Version {
  implicit object VersionOrdering extends Ordering[Version] {
    private val NumberPattern = "\\d+".r

    override def compare(x: Version, y: Version) = (numbersIn(x.value), numbersIn(y.value)).zipped.collectFirst {
      case (a, b) if a != b => a.compareTo(b)
    } getOrElse {
      0
    }

    private def numbersIn(version: String): Stream[Int] =
      NumberPattern.findAllIn(version).map(_.toInt).toStream
  }
}

