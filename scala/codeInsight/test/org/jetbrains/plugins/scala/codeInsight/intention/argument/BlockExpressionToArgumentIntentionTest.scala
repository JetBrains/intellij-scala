package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

abstract class BlockExpressionToArgumentIntentionTestBase extends ScalaIntentionTestBase {
  override def familyName: String = ScalaCodeInsightBundle.message("family.name.convert.to.argument.in.parentheses")

  protected val AFTER_SINGLE_EXPRESSION =
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

  protected val AFTER_SINGLE_EXPRESSION_TYPED =
    s"""object Test {
       |  Some(1).foreach($CARET(one: Int) => println(one))
       |}
       """.stripMargin

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

  protected val AFTER_MULTIPLE_BLOCK_EXPRESSIONS_TYPED =
    s"""object Test {
       |  Some(1).foreach($CARET(one: Int) => {
       |    println(one)
       |    val two = one + 1
       |    println(two)
       |  })
       |}
       """.stripMargin

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

  def testBlockNonFunctionArgument_SCL15724(): Unit = {
    val before =
      s"""object Test {
         |  identity {$CARET 42 }
         |}
         |""".stripMargin
    val after =
      """object Test {
        |  identity(42)
        |}
        |""".stripMargin
    doTest(before, after)

  }

  def testBlockNonFunctionArgument_SCL15724_1(): Unit = {
    val before =
      s"""object Test {
         |  identity {$CARET
         |    42
         |  }
         |}
         |""".stripMargin
    val after =
      """object Test {
        |  identity(42)
        |}
        |""".stripMargin
    doTest(before, after)
  }

  def testBlockNonFunctionArgument_SCL15724_2(): Unit = {
    val before =
      s"""object Test {
         |  identity {$CARET
         |    println(42)
         |  }
         |}
         |""".stripMargin
    val after =
      """object Test {
        |  identity(println(42))
        |}
        |""".stripMargin
    doTest(before, after)
  }

  def testBlockNonFunctionArgument_SCL15724_3(): Unit = {
    val before =
      s"""object Test {
         |  identity {$CARET
         |    "hello".toUpperCase()
         |      .substring(0)
         |  }
         |}
         |""".stripMargin
    val after =
      """object Test {
        |  identity("hello".toUpperCase()
        |    .substring(0))
        |}
        |""".stripMargin
    doTest(before, after)
  }

  def testBlockNonFunctionArgument_ShouldNotBeAvailable_SCL15724(): Unit = {
    val before =
      s"""object Test {
         |  identity {$CARET 42; 23 }
         |}
         |""".stripMargin
    checkIntentionIsNotAvailable(before)
  }

  def testBlockNonFunctionArgument_ShouldNotBeAvailable_SCL15724_1(): Unit = {
    val before =
      s"""object Test {
         |  identity {$CARET
         |    42;
         |    23
         |  }
         |}
         |""".stripMargin
    checkIntentionIsNotAvailable(before)
  }
}

class BlockExpressionToArgumentIntentionTest_Scala2 extends BlockExpressionToArgumentIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version < LatestScalaVersions.Scala_3_0


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
}

class BlockExpressionToArgumentIntentionTest_Scala3 extends BlockExpressionToArgumentIntentionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testActionShouldBeUnavailableForFewerBracesBlocksWithMultipleExpressions(): Unit = {
    val text =
      s"""Some(1).foreach:$CARET
         |  print("out of lambda")
         |  one =>
         |    println(one)
         |    val two = one + 1
         |    println(two)
      """.stripMargin
    checkIntentionIsNotAvailable(text)
  }

  def testFewerBracesBlockWithSingleExpression_1(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach:$CARET
         |    one =>
         |      println(one)
         |}
       """.stripMargin
    doTest(before, AFTER_SINGLE_EXPRESSION)
  }

  def testFewerBracesBlockWithSingleExpression_2(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach:$CARET one =>
         |    println(one)
         |}
       """.stripMargin
    doTest(before, AFTER_SINGLE_EXPRESSION)
  }

  def testBlockWithSingleExpression_Typed_0(): Unit = {
    val before =
      s"""object Test {
         |  Some(1).foreach {$CARET
         |    func: arg =>
         |      println(arg)
         |  }
         |}
         |""".stripMargin

    val after =
      s"""object Test {
         |  Some(1).foreach(func: arg =>
         |    println(arg))
         |}
         |""".stripMargin

    doTest(before, after)
  }
}
