package org.jetbrains.plugins.scala.tasty

// TODO Cross-compile a tasty-api module instead of duplicating the classes
trait TastyApi {
  def read(bytes: Array[Byte]): Option[(String, String)]
}
