package org.jetbrains.plugins.scala.tasty

import org.jetbrains.plugins.scala.tasty.reader.TastyImpl

object TastyReader {
  def read(bytes: Array[Byte]): Option[(String, String)] = api.read(bytes)

  private lazy val api = new TastyImpl()
}