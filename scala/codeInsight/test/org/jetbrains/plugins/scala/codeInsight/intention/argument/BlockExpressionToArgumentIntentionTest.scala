package org.jetbrains.plugins.scala.codeInsight.intention.argument

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class BlockExpressionToArgumentIntentionTest extends ScalaIntentionTestBase {
  override def familyName: String = BlockExpressionToArgumentIntention.FAMILY_NAME

  import EditorTestUtil.{CARET_TAG => CARET}

  private val AFTER_SINGLE_EXPRESSION =
    s"""object Test {
       |  Some(1).foreach(${CARET}one => println(one))
       |}
       """.stripMargin

  def testBlockWithSingleExpression_1(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET
         |    one =>
         |      println(one)
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_SINGLE_EXPRESSION)
  }

  def testBlockWithSingleExpression_2(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET one =>
         |    println(one)
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_SINGLE_EXPRESSION)
  }

  def testBlockWithSingleExpression_3(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET one => {
         |    println(one)
         |  } }
         |}
       """.stripMargin
    val after =
      s"""object Test {
         |  Some(1).foreach(${CARET}one => {
         |    println(one)
         |  })
         |}
       """.stripMargin
    doTest(before, after)
  }

  def testBlockWithSingleMultilineExpression(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET one =>
         |    "qwe"
         |       .substring(0, 1)
         |       .toString
         |  }
         |}
       """.stripMargin
    val after =
      s"""object Test {
         |  Some(1).foreach(${CARET}one => {
         |    "qwe"
         |      .substring(0, 1)
         |      .toString
         |  })
         |}
       """.stripMargin
    doTest(before, after)
  }

  private val AFTER_SINGLE_EXPRESSION_TYPED =
    s"""object Test {
       |  Some(1).foreach($CARET(one: Int) => println(one))
       |}
       """.stripMargin

  def testBlockWithSingleExpression_Typed_0(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET
         |    one: Int =>
         |      println(one)
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_SINGLE_EXPRESSION_TYPED)
  }

  def testBlockWithSingleExpression_Typed_1(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET
         |    (one: Int) =>
         |      println(one)
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_SINGLE_EXPRESSION_TYPED)
  }

  def testBlockWithSingleExpression_Typed_2(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET one: Int =>
         |    println(one)
         |  }
         |}
       """.stripMargin

    doTest(before, AFTER_SINGLE_EXPRESSION_TYPED)
  }

  def testBlockWithSingleExpression_Typed_3(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET one: Int => {
         |    println(one)
         |  } }
         |}
       """.stripMargin
    val after =
      s"""object Test {
         |  Some(1).foreach($CARET(one: Int) => {
         |    println(one)
         |  })
         |}
       """.stripMargin
    doTest(before, after)
  }

  private val AFTER_MULTIPLE_BLOCK_EXPRESSIONS =
    s"""object Test {
       |  Some(1).foreach(${CARET}one => {
       |    println(one)
       |    val two = one + 1
       |    println(two)
       |  })
       |}
       """.stripMargin

  def testBlockWithMultipleExpressions_1(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET
         |    one =>
         |      println(one)
         |      val two = one + 1
         |      println(two)
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_MULTIPLE_BLOCK_EXPRESSIONS)
  }

  def testBlockWithMultipleExpressions_2(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET one =>
         |    println(one)
         |    val two = one + 1
         |    println(two)
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_MULTIPLE_BLOCK_EXPRESSIONS)
  }

  def testBlockWithMultipleExpressions_3(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET one => {
         |    println(one)
         |    val two = one + 1
         |    println(two)
         |  }
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_MULTIPLE_BLOCK_EXPRESSIONS)
  }

  private val AFTER_MULTIPLE_BLOCK_EXPRESSIONS_TYPED =
    s"""object Test {
       |  Some(1).foreach($CARET(one: Int) => {
       |    println(one)
       |    val two = one + 1
       |    println(two)
       |  })
       |}
       """.stripMargin

  def testBlockWithMultipleExpressions_Typed_1(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET
         |    one: Int =>
         |      println(one)
         |      val two = one + 1
         |      println(two)
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_MULTIPLE_BLOCK_EXPRESSIONS_TYPED)
  }

  def testBlockWithMultipleExpressions_Typed_2(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET one: Int =>
         |    println(one)
         |    val two = one + 1
         |    println(two)
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_MULTIPLE_BLOCK_EXPRESSIONS_TYPED)
  }

  def testBlockWithMultipleExpressions_Typed_3(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET one: Int => {
         |    println(one)
         |    val two = one + 1
         |    println(two)
         |  }
         |  }
         |}
       """.stripMargin
    doTest(before, AFTER_MULTIPLE_BLOCK_EXPRESSIONS_TYPED)
  }

  def testActionShouldBeUnavailableForBlocksWithSomethingExceptFunctionExpression(): Unit = {
    val text =
      s"""Some(1).foreach {$CARET
         |  print("out of lambda")
         |  one =>
         |    println(one)
         |    val two = one + 1
         |    println(two)
         |}
      """.stripMargin
    checkIntentionIsNotAvailable(text)
  }
}
