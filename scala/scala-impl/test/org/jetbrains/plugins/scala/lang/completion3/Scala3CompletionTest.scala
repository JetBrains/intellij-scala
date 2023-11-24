package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase

class Scala3CompletionTest extends ScalaCompletionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  def testDoNotShowAnonymousContextParametersInCompletionList(): Unit = {
    val text =
      s"""def foo(using String, Short): Unit =
         |  val x$$23 = 23
         |  x$$$CARET
         |""".stripMargin
    checkNoBasicCompletion(text, "x$1") //~ `String`
    checkNoBasicCompletion(text, "x$2") //~ `Short`

    doCompletionTest(
      text,
      s"""def foo(using String, Short): Unit =
        |  val x$$23 = 23
        |  x$$23$CARET
        |""".stripMargin,
      "x$23"
    )
  }
}
