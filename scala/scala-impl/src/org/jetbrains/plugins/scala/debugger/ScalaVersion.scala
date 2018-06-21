package org.jetbrains.plugins.scala.debugger

/**
 * @author Nikolay.Tropin
 */
sealed trait ScalaVersion {
  val major: String
  val minor: String
}

case object Scala_2_9 extends ScalaVersion {
  override final val major: String = "2.9"
  override final val minor: String = "2.9.3"
}

case object Scala_2_10 extends ScalaVersion {
  override final val major: String = "2.10"
  override final val minor: String = "2.10.7"
}

case object Scala_2_11 extends ScalaVersion {
  override final val major: String = "2.11"
  override final val minor: String = "2.11.12"
}

case object Scala_2_12 extends ScalaVersion {
  override final val major: String = "2.12"
  override final val minor: String = "2.12.3"
}

case object Scala_2_13 extends ScalaVersion {
  override final val major: String = "2.13.0-M4"
  override final val minor: String = "2.13.0-M4"
}