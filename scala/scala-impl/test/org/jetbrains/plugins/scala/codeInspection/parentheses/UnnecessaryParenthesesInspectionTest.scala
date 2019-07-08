package org.jetbrains.plugins.scala
package codeInspection
package parentheses

import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.testFramework.EditorTestUtil

/**
 * Nikolay.Tropin
 * 4/29/13
 */
class UnnecessaryParenthesesInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection =
    classOf[ScalaUnnecessaryParenthesesInspection]

  protected override val description =
    InspectionBundle.message("unnecessary.parentheses.name")

  private def defaultSettings = UnnecessaryParenthesesSettings.default

  private def considerClarifying = defaultSettings.copy(ignoreClarifying = false)

  private def ignoreAroundFunctionType = defaultSettings.copy(ignoreAroundFunctionType = true)

  private def ignoreAroundFunctionTypeParam = defaultSettings.copy(ignoreAroundFunctionTypeParam = true)

  private def ignoreAroundFunctionExprParam = defaultSettings.copy(ignoreAroundFunctionExprParam = true)

  private def withSettings(settings: UnnecessaryParenthesesSettings)(body: => Unit): Unit = {

    val tool = InspectionProfileManager.getInstance(getProject)
      .getCurrentProfile
      .getInspectionTool("ScalaUnnecessaryParentheses", getProject)
      .getTool

    tool match {
      case check: ScalaUnnecessaryParenthesesInspection =>
        val oldSettings = check.currentSettings()
        try {
          check.setSettings(settings)
          body
        } finally {
          check.setSettings(oldSettings)
        }
      case _ =>
    }
  }

  private def checkTextHasError(text: String): Unit = {
    super.checkTextHasError(text, allowAdditionalHighlights = true)
  }

  // see https://github.com/JetBrains/intellij-scala/pull/434 for more test case

  def test_1(): Unit = {
    checkTextHasError(s"$START(1 + 1)$END")

    testQuickFix(
      s"(${CARET}1 + 1)",
      "1 + 1",
      "(1 + 1)"
    )
  }

  def test_2(): Unit = {
    checkTextHasNoErrors("1 + (1 * 2)")
  }

  def test_3(): Unit = {
    val selected =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if $START(even % 2 == 0)$END => (even + 1)
         |  case odd => 1 + (odd * 3)
         |}"""
    checkTextHasError(selected, allowAdditionalHighlights = true)

    testQuickFix(
      text =
        s"""
           |def f(n: Int): Int = n match {
           |  case even if (${CARET}even % 2 == 0) => (even + 1)
           |  case odd => 1 + (odd * 3)
           |}""",
      expected =
        """
          |def f(n: Int): Int = n match {
          |  case even if even % 2 == 0 => (even + 1)
          |  case odd => 1 + (odd * 3)
          |}""",
      expressionText = "(even % 2 == 0)"
    )
  }

  def test_4(): Unit = {
    val selected =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => $START(even + 1)$END
         |  case odd => 1 + (odd * 3)
         |}"""
    checkTextHasError(selected, allowAdditionalHighlights = true)

    testQuickFix(
      text =
        s"""
           |def f(n: Int): Int = n match {
           |  case even if (even % 2 == 0) => (even + 1$CARET)
           |  case odd => 1 + (odd * 3)
           |}""",
      expected =
        """
          |def f(n: Int): Int = n match {
          |  case even if (even % 2 == 0) => even + 1
          |  case odd => 1 + (odd * 3)
          |}""",
      "(even + 1)"
    )
  }

  def test_5(): Unit = checkTextHasNoErrors(
    "1 :: (2 :: Nil)"
  )

  def test_6(): Unit = {
    checkTextHasError(s"val a = $START(((1)))$END")

    testQuickFix(
      s"val a = (($CARET(1)))",
      "val a = 1",
      "(((1)))"
    )
  }

  def test_7(): Unit = {
    val text =
      """def a(x: Any): Boolean = true
        |List() count (a(_))"""
    checkTextHasNoErrors(text)
  }

  def test_8(): Unit = {
    checkTextHasError(s"1 to $START((1, 2))$END")

    testQuickFix(
      "1 to ((1, 2))",
      "1 to (1, 2)",
      "((1, 2))"
    )
  }

  def test_9(): Unit = checkTextHasNoErrors(
      """(List("a")
        |    :+ new String("b")
        |    :+ new String("c")
        |    :+ new String("d"))"""
  )

  def test_10(): Unit = {
    checkTextHasError(s"$START(/*b*/ 1 + /*a*/ 1 /*comment*/)$END")

    testQuickFix(
      s"($CARET/*b*/ 1 + /*a*/ 1 /*comment*/)",
      "/*b*/ 1 + /*a*/ 1 /*comment*/",
      "(1 + 1)"
    )
  }

  def test_11(): Unit = {
    checkTextHasError(s"$START(/*1*/ 6 /*2*/ /*3*/)$END")

    testQuickFix(
      s"($CARET/*1*/ 6 /*2*/ /*3*/)",
      "/*1*/ 6 /*2*/\n\r/*3*/",
      "(6)"
    )
  }


  def test_simpleType(): Unit = {
    checkTextHasError(s"val i: $START(Int)$END = 3")

    testQuickFix(
      s"val i: ($CARET Int) = 3",
      "val i: Int = 3",
      "(Int)"
    )
  }


  def test_simpleTypeMultipleParen(): Unit = {
    checkTextHasError(s"val i: $START(((Int)))$END = 3")

    testQuickFix(
      "val i: (((Int))) = 3",
      "val i: Int = 3",
      "(((Int)))"
    )
  }

  def test_nestedFunctionType(): Unit = {
    checkTextHasError(s"val i: Int => $START(Int => String)$END = _")

    val text = "val i: Int => (Int => String) = _"

    withSettings(ignoreAroundFunctionType) {
      checkTextHasNoErrors(text)
    }

    testQuickFix(
      text,
      "val i: Int => Int => String = _",
      "(Int => String)"
    )
  }

  def test_functionType(): Unit = {
    val selected = s"val i: $START(Int => String)$END = _"
    checkTextHasError(selected)

    val text = "val i: (Int => String) = _"

    withSettings(ignoreAroundFunctionType) {
      checkTextHasNoErrors(text)
    }

    testQuickFix(
      text,
      "val i: Int => String = _",
      "(Int => String)"
    )
  }

  def test_functionTypeSingleParam(): Unit = {
    checkTextHasError(s"val i: $START(Int)$END => String = _")

    val text = "val i: (Int) => String = _"
    withSettings(ignoreAroundFunctionTypeParam) {
      checkTextHasNoErrors(text)
    }

    testQuickFix(
      text,
      "val i: Int => String = _",
      "(Int)"
    )
  }

  def test_functionSeveralParams(): Unit = checkTextHasNoErrors(
    "val i: (Int, Int) => String = _)"
  )

  def test_functionPlusInfix(): Unit = checkTextHasNoErrors(
    s"val i: Int => $START(A op B)$END = _"
  )

  def test_infixType_rightAssoc(): Unit = {
    checkTextHasError(s"val f: Int <<: $START(Unit <<: Unit)$END = _")

    testQuickFix(
      s"val f: Int <<: ($CARET Unit <<: Unit) = _",
      "val f: Int <<: Unit <<: Unit = _",
      "(Unit <<: Unit)"
    )

    checkTextHasNoErrors(s"val f: (Int <<: Unit) <<: Void")
  }


  def test_infixType_leftAssoc(): Unit = {
    checkTextHasError(s"val f: $START(Int op Unit)$END op Unit = _")

    testQuickFix(
      s"val f: ($CARET Int op Unit) op Unit = _",
      "val f: Int op Unit op Unit = _",
      "(Int op Unit)"
    )

    checkTextHasNoErrors(
      """
        |class Foo[A, B]
        |val a: Int Foo (Int Foo String)= ???
      """.stripMargin
    )
  }

  def test_infixTypeClarifying(): Unit = {
    val prefix =
      """
        |class X
        |class ==[A, B]
        |class +[A, B]
        |""".stripMargin
    val clarifying = prefix + "type Foo = (X + X) == (X + X)"

    checkTextHasNoErrors(clarifying)

    withSettings(considerClarifying) {
      val selected = prefix + s"type Foo = $START(X + X)$END == (X + X)"
      checkTextHasError(selected)

      testQuickFix(
        prefix + s"type Foo = $CARET(X + X) == (X + X)",
        prefix + "type Foo = X + X == (X + X)",
        "(X + X)"
      )
    }
  }


  def test_InfixType_MixedAssoc(): Unit = checkTextHasNoErrors(
    "val f: Double <<: (Int Map String)"
  )


  def test_tupleType(): Unit = {
    val selected = s"val f: $START((Int, String))$END = _"
    checkTextHasError(selected)

    testQuickFix(
      s"val f: ($CARET(Int, Unit)) = _",
      "val f: (Int, Unit) = _",
      "((Int, Unit))"
    )
  }


  def test_infixPatternPrecedence(): Unit = {
    checkTextHasError(s"val a +: $START(b +: c)$END = _ ")

    testQuickFix(
      s"val a +: ($CARET b +: c) = _ ",
      "val a +: b +: c = _ ",
      "(b +: c)"
    )
  }


  def test_lambdaParam(): Unit = {
    val r1 = s"Seq(1) map { $START(i)$END => i + 1 }"
    val r2 = s"Seq(1) map { $START(i: Int)$END => i + 1 }"
    val r3 = s"Seq(1) map ($START(i)$END => i + 1)"
    val required = "Seq(1) map ((i: Int) => i + 1)"

    checkTextHasNoErrors(required)
    checkTextHasError(r1)
    checkTextHasError(r2)
    checkTextHasError(r3)

    withSettings(ignoreAroundFunctionExprParam) {
      checkTextHasNoErrors(r1)
      checkTextHasNoErrors(r2)
      checkTextHasNoErrors(r3)
    }

    testQuickFix(
      s"Seq(1) map { (${CARET}i: Int) => i + 1 }",
      s"Seq(1) map { i: Int => i + 1 }",
      "(i: Int)"
    )
  }

  def test_infixPatternClarifying(): Unit = withSettings(considerClarifying) {
    checkTextHasError(s"val a +: $START(b *: c)$END = _ ")

    testQuickFix(
      s"val a +: ($CARET b *: c) = _ ",
      "val a +: b *: c = _ ",
      "(b *: c)"
    )
  }

  def testFunctionInClassParents(): Unit = {
    checkTextHasNoErrors("class MyFun extends (String => Int)")
    checkTextHasNoErrors("class MyFun2 extends A with (String => Int)")
  }

  def testPrecedenceNoErrors(): Unit = checkTextHasNoErrors(
    """
      |class B {
      |  null match {
      |    case b @ (_: String | _: B) =>
      |  }
      |}
      """.stripMargin
  )

  def testTypeProjection(): Unit = checkTextHasNoErrors(
    """
      |class A {
      |  def traverse[F[_]] = 0
      |}
      |
      |val value = new A()
      |val result = value.traverse[({ type L[x] = Int })#L]""".stripMargin
  )

  def testListPattern(): Unit = checkTextHasNoErrors(
    """
      |val b = null match {
      |  case (param: Int) :: (rest @ _ :: _) =>
      |  case _ =>
      |}""".stripMargin
  )

  def testFunctionTupleParameter(): Unit = checkTextHasNoErrors(
    "val f: ((Int, Int)) => Int = ???"
  )

  def testTraitParents(): Unit = checkTextHasNoErrors(
    "trait Foo extends (Int => String)"
  )

  def testEmptyParentheses(): Unit = checkTextHasNoErrors(
    "type Null_Unit = () => Unit"
  )

  def testSCL14395(): Unit = checkTextHasNoErrors(
    "val f: (Int => Int) => Int = ???"
  )

  def testDoubleParenthesesQuickFix(): Unit = testQuickFix(
    s"1 + ((${CARET}1 + 1))",
    "1 + (1 + 1)",
    "((1 + 1))"
  )

  def testRepeatedParameterType(): Unit = {
    checkTextHasNoErrors("class A; def apply(pairs: (A => (A, A))*): A = new A")

    testQuickFix(
      s"class A; def apply(pairs: $CARET(A)*): A = new A",
      "class A; def apply(pairs: A*): A = new A",
      "(A)"
    )
  }

  override protected def testQuickFix(text: String,
                                      expected: String,
                                      expressionText: String): Unit =
    super.testQuickFix(
      text,
      expected,
      InspectionBundle.message("remove.unnecessary.parentheses.fix", expressionText)
    )
}
