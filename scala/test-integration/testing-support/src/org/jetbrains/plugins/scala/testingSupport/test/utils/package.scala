package org.jetbrains.plugins.scala.testingSupport.test

package object utils {

  implicit class StringOps(private val str: String) extends AnyVal {

    def withoutBackticks: String =
      str.replace("`", "")

    def withQuotedSpaces: String =
      if (str.contains(" ")) s""""$str"""" else str

  }
}
