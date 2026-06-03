package org.jetbrains.plugins.scala.tasty

import org.jetbrains.plugins.scala.tasty.reader.{CompilerOptions, TastyImpl}

object TastyReader {
  def read(bytes: Array[Byte]): Option[(String, String, CompilerOptions)] = api.read(bytes)

  private lazy val api = new TastyImpl()
}