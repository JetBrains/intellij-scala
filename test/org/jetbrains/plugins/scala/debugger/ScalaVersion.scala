package org.jetbrains.plugins.scala.debugger

/**
 * @author Nikolay.Tropin
 */
sealed trait ScalaVersion {
  val major: String
  val minor: String
}

case object Scala_2_10 extends ScalaVersion {
  override final val major: String = "2.10"
  override final val minor: String = "2.10.6"
}

case object Scala_2_11 extends ScalaVersion {
  override final val major: String = "2.11"
  override final val minor: String = "2.11.7"
}

case object Scala_2_11_8 extends ScalaVersion {
  override final val major: String = "2.11"
  override final val minor: String = "2.11.8"
}

case object Scala_2_12 extends ScalaVersion {
  override final val major: String = "2.12"
  override final val minor: String = "2.12.0"
}

trait ScalaSdkOwner {
  implicit val version: ScalaVersion
}

// Java compatibility
trait DefaultScalaSdkOwner extends ScalaSdkOwner {
  override implicit val version: ScalaVersion = Scala_2_10
}
