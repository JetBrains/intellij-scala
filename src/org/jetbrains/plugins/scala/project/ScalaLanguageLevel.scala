package org.jetbrains.plugins.scala
package project

/**
 * @author Pavel Fatin
 */
sealed case class ScalaLanguageLevel(ordinal: Int, version: String, virtualized: Boolean = false) {
  def name: String = {
    val prefix: String = "Scala " + version
    if (virtualized) prefix + " virtualized" else prefix
  }
  
  def >(level: ScalaLanguageLevel): Boolean = ordinal > level.ordinal

  def >=(level: ScalaLanguageLevel): Boolean = ordinal >= level.ordinal

  def <(level: ScalaLanguageLevel): Boolean = ordinal < level.ordinal

  def <=(level: ScalaLanguageLevel): Boolean = ordinal < level.ordinal
}

object ScalaLanguageLevel {
  val Values = Array(Scala_2_7, Scala_2_8, Scala_2_9, Scala_2_10, Scala_2_10_V, Scala_2_11, Scala_2_11_V)

  val Default = Scala_2_11

  def from(version: Version): Option[ScalaLanguageLevel] =
    ScalaLanguageLevel.Values.find(it => version.number.startsWith(it.version))

  object Scala_2_7 extends ScalaLanguageLevel(0, "2.7")

  object Scala_2_8 extends ScalaLanguageLevel(1, "2.8")

  object Scala_2_9 extends ScalaLanguageLevel(2, "2.9")

  object Scala_2_10 extends ScalaLanguageLevel(3, "2.10")

  object Scala_2_10_V extends ScalaLanguageLevel(4, "2.10", true)

  object Scala_2_11 extends ScalaLanguageLevel(5, "2.11")

  object Scala_2_11_V extends ScalaLanguageLevel(6, "2.11", true)
}
