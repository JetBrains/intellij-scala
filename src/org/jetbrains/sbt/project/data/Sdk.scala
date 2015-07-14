package org.jetbrains.sbt.project.data

/**
 * @author Nikolay Obedin
 * @since 7/14/15.
 */
sealed trait Sdk {
  val version: String
}

final case class Jdk(version: String) extends Sdk
final case class Android(version: String) extends Sdk

object Sdk {

}
