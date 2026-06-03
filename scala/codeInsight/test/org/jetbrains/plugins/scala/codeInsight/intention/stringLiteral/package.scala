package org.jetbrains.plugins.scala.codeInsight.intention

package object stringLiteral {

  private[intention]
  implicit class StringOps(private val str: String) extends AnyVal {
    /**
     * Allows using triple quotes as content inside tripple quotes itself, like: {{{
     *   """'''text'''"""
     * }}}
     */
    def fixTripleQuotes: String =
      str.replace("'''", "\"\"\"")
  }
}
