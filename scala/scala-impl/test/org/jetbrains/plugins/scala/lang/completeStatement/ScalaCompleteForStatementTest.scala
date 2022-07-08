package org.jetbrains.plugins.scala
package lang
package completeStatement

class ScalaCompleteForStatementTest extends ScalaCompleteStatementTestBase {

  def testForStatement1(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for $CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET) {
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForStatement2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET) {
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForStatement3(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForStatement4(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10$CARET) {
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10) {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForStatement5(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10) {$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10) {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForStatement6(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10$CARET) {
         |      println()
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10) {
         |      $CARET
         |      println()
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForStatement7(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for ()$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )
}
