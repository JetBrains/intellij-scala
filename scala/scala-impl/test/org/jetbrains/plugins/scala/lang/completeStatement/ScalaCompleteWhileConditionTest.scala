package org.jetbrains.plugins.scala
package lang
package completeStatement

class ScalaCompleteWhileConditionTest extends ScalaCompleteStatementTestBase {

  def testWhileCondition1(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    while $CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    while ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testWhileCondition2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    while$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    while ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testWhileCondition3(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    while ($CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    while ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )


  def testWhileCondition4(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    while (true$CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    while (true) {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testWhileCondition5(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    while (true) {$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    while (true) {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testWhileCondition6(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    while (true$CARET) {
         |      println()
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    while (true) {
         |      $CARET
         |      println()
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testWhileCondition7(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    while ()$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    while ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testWhileCondition8(): Unit = doCompletionTest(
    fileText =
      s"""
         |object A {
         |  while (true)$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |object A {
         |  while (true) {
         |    $CARET
         |  }
         |}
      """.stripMargin
  )
}
