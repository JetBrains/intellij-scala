package org.jetbrains.plugins.scala
package project

/**
 * @author Pavel Fatin
 */
sealed case class ScalaLanguageLevel(ordinal: Int, version: String) extends Named {
  def getName: String = version

  def >(level: ScalaLanguageLevel): Boolean = ordinal > level.ordinal

  def >=(level: ScalaLanguageLevel): Boolean = ordinal >= level.ordinal

  def <(level: ScalaLanguageLevel): Boolean = ordinal < level.ordinal

  def <=(level: ScalaLanguageLevel): Boolean = ordinal <= level.ordinal
}

object ScalaLanguageLevel {
  val Values = Array(
    Snapshot,
    Scala_2_8,
    Scala_2_9,
    Scala_2_10,
    Scala_2_11,
    Scala_2_12
  )

  val Default: ScalaLanguageLevel = Scala_2_12

  object Snapshot extends ScalaLanguageLevel(0, "SNAPSHOT")

  object Scala_2_8 extends ScalaLanguageLevel(1, "2.8")

  object Scala_2_9 extends ScalaLanguageLevel(2, "2.9")

  object Scala_2_10 extends ScalaLanguageLevel(3, "2.10")

  object Scala_2_11 extends ScalaLanguageLevel(4, "2.11")

  object Scala_2_12 extends ScalaLanguageLevel(5, "2.12")
}
