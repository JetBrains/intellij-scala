package org.jetbrains.plugins.scala
package lang
package completeStatement

import com.intellij.testFramework.EditorTestUtil

/**
  * @author Ksenia.Sautina
  * @since 2/25/13
  */
class ScalaCompleteIfConditionTest extends ScalaCompleteStatementTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testIfCondition1(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    if $CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    if ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testIfCondition2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    if$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    if ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testIfCondition3(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    if ($CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    if ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testIfCondition4(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    if (true$CARET) {
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    if (true) {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testIfCondition5(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    if (true) {$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    if (true) {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testIfCondition6(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    if (true$CARET) {
         |      println()
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    if (true) {
         |      $CARET
         |      println()
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testIfCondition7(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    if ()$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    if ($CARET) {
         |
         |    }
         |  }
         |}
      """.stripMargin
  )
}
