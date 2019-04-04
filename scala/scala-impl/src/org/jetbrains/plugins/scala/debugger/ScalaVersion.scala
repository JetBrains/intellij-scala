package org.jetbrains.plugins.scala
package debugger

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

/**
 * @author Nikolay.Tropin
 */
sealed abstract class ScalaVersion(val languageLevel: ScalaLanguageLevel,
                                   val minor: String) {
  def major: String = languageLevel.getVersion
}

case object Scala_2_9 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_9,
  "2.9.3"
)

case object Scala_2_10 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_10,
  "2.10.7"
)

case object Scala_2_11 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_11,
  "2.11.12"
)

case object Scala_2_12 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_12,
  "2.12.3"
)

case object Scala_2_13 extends ScalaVersion(
  ScalaLanguageLevel.Scala_2_13,
  "2.13.0-M4"
) {
  override val major: String = minor
}

case object Scala_3_0 extends ScalaVersion(
  ScalaLanguageLevel.Scala_3_0,
  "0.13.0-RC1"
)