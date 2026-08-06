package org.jetbrains.plugins.scala.util

import org.junit.ComparisonFailure

object extensions {

  implicit class ComparisonFailureOps(private val failure: ComparisonFailure) extends AnyVal {

    // add "before" state to conveniently view failed tests
    def withBeforePrefix(textBefore: String): ComparisonFailure =
      new org.junit.ComparisonFailure(
        failure.getMessage,
        addBeforePrefix(textBefore, failure.getExpected),
        addBeforePrefix(textBefore, failure.getActual)
      )

    // add "before" state to conveniently view failed tests
    private def addBeforePrefix(textBefore: String, textAfter: String)= {
      s"""<<<Before>>>:
         |$textBefore
         |----------------------------------------------------
         |<<<After>>>:
         |$textAfter""".stripMargin
    }
  }
}
