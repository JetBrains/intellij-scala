package org.jetbrains.plugins.scala
package config

/**
 * Pavel Fatin
 */
case class Version(text: String) {
  def >= (other: Version): Boolean = {
    numbers.zip(other.numbers).forall(p => p._1 >= p._2)
  }

  lazy val numbers: Seq[Int] = text
          .takeWhile(c => c.isDigit || c == '.')
          .split('.')
          .filterNot(_.isEmpty)
          .map(_.toInt)
}