package org.jetbrains.plugins.cbt

import java.io.File

import scala.xml.NodeSeq

object Helpers {
  implicit class XmlOps(val xml: NodeSeq) {
    def value: String =
      xml.text.trim
  }

  implicit class StringOps(val str: String) {
    def toFile: File =
      new File(str)
  }
}
