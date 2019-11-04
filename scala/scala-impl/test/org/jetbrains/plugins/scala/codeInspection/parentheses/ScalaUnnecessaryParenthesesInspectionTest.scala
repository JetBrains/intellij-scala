package org.jetbrains.plugins.scala
package codeInspection
package parentheses

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.extensions.TextRangeExt

class ScalaUnnecessaryParenthesesInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnnecessaryParenthesesInspection]

  protected override val description = "Unnecessary parentheses"

  private val hintBeginning = "Remove unnecessary parentheses"

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

  private def checkTextHasErrors(text: String): Unit = {
    val actualRanges  : Seq[TextRange] = findRanges(text)
    val expectedRanges: Seq[TextRange] = {
      val range = selectedRange(getEditor.getSelectionModel)
      val left  = TextRange.from(range.getStartOffset, 1)
      val right = TextRange.from(range.getEndOffset - 1, 1)
      if (range.getLength >= 4) {
        val middle = range.shrink(2)
        Seq(left, middle, right)
      } else {
        Seq(left, right)
      }
    }
    super.checkTextHasError(expectedRanges, actualRanges, allowAdditionalHighlights = true)
  }

  // see https://github.com/JetBrains/intellij-scala/pull/434 for more test case

  def test_1(): Unit = {
    val selected = s"$START(1 + 1)$END"
    checkTextHasErrors(selected)

    val text = s"(${CARET_MARKER}1 + 1)"
    val result = "1 + 1"
    val hint = hintBeginning + " (1 + 1)"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val text = "1 + (1 * 2)"
    checkTextHasNoErrors(text)
  }

  def test_3(): Unit = {
    val selected =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if $START(even % 2 == 0)$END => (even + 1)
         |  case odd => 1 + (odd * 3)
         |}
       """
    checkTextHasErrors(selected)

    val text =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (${CARET_MARKER}even % 2 == 0) => (even + 1)
         |  case odd => 1 + (odd * 3)
         |}
       """
    val result =
      """
        |def f(n: Int): Int = n match {
        |  case even if even % 2 == 0 => (even + 1)
        |  case odd => 1 + (odd * 3)
        |}
      """
    val hint = hintBeginning + " (even % 2 == 0)"
    testQuickFix(text, result, hint)
  }

  def test_4(): Unit = {
    val selected =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => $START(even + 1)$END
         |  case odd => 1 + (odd * 3)
         |}
       """
    checkTextHasErrors(selected)

    val text =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => (even + 1$CARET_MARKER)
         |  case odd => 1 + (odd * 3)
         |}
      """
    val result =
      """
        |def f(n: Int): Int = n match {
        |  case even if (even % 2 == 0) => even + 1
        |  case odd => 1 + (odd * 3)
        |}
      """
    val hint = hintBeginning + " (even + 1)"
    testQuickFix(text, result, hint)
  }

  def test_5(): Unit = {
    val text = "1 :: (2 :: Nil)"
    checkTextHasNoErrors(text)
  }

  def test_6(): Unit = {
    val selected = s"val a = $START(((1)))$END"
    checkTextHasErrors(selected)

    val text = s"val a = (($CARET_MARKER(1)))"
    val result = "val a = 1"
    val hint = hintBeginning + " (((1)))"
    testQuickFix(text, result, hint)
  }

  def test_7(): Unit = {
    val text =
      """def a(x: Any): Boolean = true
        |List() count (a(_))"""
    checkTextHasNoErrors(text)
  }

  def test_8(): Unit = {
    val selected = s"1 to $START((1, 2))$END"
    checkTextHasErrors(selected)

    val text = "1 to ((1, 2))"
    val result = "1 to (1, 2)"
    val hint = hintBeginning + " ((1, 2))"
    testQuickFix(text, result, hint)
  }

  def test_9(): Unit = {
    val text =
      """(List("a")
        |    :+ new String("b")
        |    :+ new String("c")
        |    :+ new String("d"))"""
    checkTextHasNoErrors(text)
  }

  def test_10(): Unit = {
    val selected = s"$START(/*b*/ 1 + /*a*/ 1 /*comment*/)$END"
    checkTextHasErrors(selected)

    val text = s"($CARET_MARKER/*b*/ 1 + /*a*/ 1 /*comment*/)"
    val result = "/*b*/ 1 + /*a*/ 1 /*comment*/"
    val hint = hintBeginning + " (1 + 1)"
    testQuickFix(text, result, hint)
  }

  def test_11(): Unit = {
    val selected = s"$START(/*1*/ 6 /*2*/ /*3*/)$END"
    checkTextHasErrors(selected)

    val text = s"($CARET_MARKER/*1*/ 6 /*2*/ /*3*/)"
    val result = "/*1*/ 6 /*2*/\n\r/*3*/"
    val hint = hintBeginning + " (6)"
    testQuickFix(text, result, hint)
  }


  def test_simpleType(): Unit = {
    val selected = s"val i: $START(Int)$END = 3"
    checkTextHasErrors(selected)

    val text = s"val i: ($CARET_MARKER Int) = 3"
    val result = "val i: Int = 3"
    val hint = hintBeginning + " (Int)"
    testQuickFix(text, result, hint)
  }


  def test_simpleTypeMultipleParen(): Unit = {
    val selected = s"val i: $START(((Int)))$END = 3"
    checkTextHasErrors(selected)

    val text = "val i: (((Int))) = 3"
    val result = "val i: Int = 3"
    val hint = hintBeginning + " (((Int)))"
    testQuickFix(text, result, hint)
  }

  def test_nestedFunctionType(): Unit = {
    val selected = s"val i: Int => $START(Int => String)$END = _"
    checkTextHasErrors(selected)

    val text = "val i: Int => (Int => String) = _"

    withSettings(ignoreAroundFunctionType) {
      checkTextHasNoErrors(text)
    }

    val result = "val i: Int => Int => String = _"
    val hint = hintBeginning + " (Int => String)"
    testQuickFix(text, result, hint)
  }

  def test_functionType(): Unit = {
    val selected = s"val i: $START(Int => String)$END = _"
    checkTextHasErrors(selected)

    val text = "val i: (Int => String) = _"

    withSettings(ignoreAroundFunctionType) {
      checkTextHasNoErrors(text)
    }

    val result = "val i: Int => String = _"
    val hint = hintBeginning + " (Int => String)"
    testQuickFix(text, result, hint)
  }

  def test_functionTypeSingleParam(): Unit = {
    val selected = s"val i: $START(Int)$END => String = _"
    checkTextHasErrors(selected)

    val text = "val i: (Int) => String = _"
    withSettings(ignoreAroundFunctionTypeParam) {
      checkTextHasNoErrors(text)
    }

    val result = "val i: Int => String = _"
    val hint = hintBeginning + " (Int)"
    testQuickFix(text, result, hint)
  }

  def test_functionSeveralParams(): Unit = {
    checkTextHasNoErrors("val i: (Int, Int) => String = _)")
  }

  def test_functionPlusInfix(): Unit = {
    val selected = s"val i: Int => $START(A op B)$END = _"
    checkTextHasNoErrors(selected)
  }

  def test_infixType_rightAssoc(): Unit = {
    val selected = s"val f: Int <<: $START(Unit <<: Unit)$END = _"
    checkTextHasErrors(selected)

    val text = s"val f: Int <<: ($CARET_MARKER Unit <<: Unit) = _"
    val result = "val f: Int <<: Unit <<: Unit = _"
    val hint = hintBeginning + " (Unit <<: Unit)"
    testQuickFix(text, result, hint)

    val correct = s"val f: (Int <<: Unit) <<: Void"
    checkTextHasNoErrors(correct)
  }


  def test_infixType_leftAssoc(): Unit = {
    val selected = s"val f: $START(Int op Unit)$END op Unit = _"
    checkTextHasErrors(selected)

    val text = s"val f: ($CARET_MARKER Int op Unit) op Unit = _"
    val result = "val f: Int op Unit op Unit = _"
    val hint = hintBeginning + " (Int op Unit)"
    testQuickFix(text, result, hint)

    val correct =
      """
        |class Foo[A, B]
        |val a: Int Foo (Int Foo String)= ???
      """.stripMargin
    checkTextHasNoErrors(correct)
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
      checkTextHasErrors(selected)

      val text = prefix + s"type Foo = $CARET_MARKER(X + X) == (X + X)"
      val result = prefix + "type Foo = X + X == (X + X)"

      testQuickFix(text, result, hintBeginning + " (X + X)")
    }
  }


  def test_InfixType_MixedAssoc(): Unit = {
    val correct = "val f: Double <<: (Int Map String)"
    checkTextHasNoErrors(correct)
  }


  def test_tupleType(): Unit = {
    val selected = s"val f: $START((Int, String))$END = _"
    checkTextHasErrors(selected)

    val text = s"val f: ($CARET_MARKER(Int, Unit)) = _"
    val result = "val f: (Int, Unit) = _"
    val hint = hintBeginning + " ((Int, Unit))"
    testQuickFix(text, result, hint)
  }


  def test_infixPatternPrecedence(): Unit = {
    val selected = s"val a +: $START(b +: c)$END = _ "
    checkTextHasErrors(selected)

    val text = s"val a +: ($CARET_MARKER b +: c) = _ "
    val result = "val a +: b +: c = _ "
    val hint = hintBeginning + " (b +: c)"
    testQuickFix(text, result, hint)
  }


  def test_lambdaParam(): Unit = {
    val r1 = s"Seq(1) map { $START(i)$END => i + 1 }"
    val r2 = s"Seq(1) map { $START(i: Int)$END => i + 1 }"
    val r3 = s"Seq(1) map ($START(i)$END => i + 1)"
    val required = "Seq(1) map ((i: Int) => i + 1)"

    checkTextHasNoErrors(required)
    checkTextHasErrors(r1)
    checkTextHasNoErrors(r2)
    checkTextHasErrors(r3)

    withSettings(ignoreAroundFunctionExprParam) {
      checkTextHasNoErrors(required)
      checkTextHasNoErrors(r1)
      checkTextHasNoErrors(r2)
      checkTextHasNoErrors(r3)
    }

    val text = s"Seq(1) map { (${CARET_MARKER}i) => i + 1 }"
    val result = s"Seq(1) map { i => i + 1 }"
    val hint = hintBeginning + " (i)"
    testQuickFix(text, result, hint)
  }

  def test_infixPatternClarifying(): Unit = {
    withSettings(considerClarifying) {
      val selected = s"val a +: $START(b *: c)$END = _ "
      checkTextHasErrors(selected)

      val text = s"val a +: ($CARET_MARKER b *: c) = _ "
      val result = "val a +: b *: c = _ "
      val hint = hintBeginning + " (b *: c)"
      testQuickFix(text, result, hint)
    }
  }

  def testFunctionInClassParents(): Unit = {
    val text = "class MyFun extends (String => Int)"
    val text2 = "class MyFun2 extends A with (String => Int)"
    checkTextHasNoErrors(text)
    checkTextHasNoErrors(text2)
  }

  def testPrecedenceNoErrors(): Unit = {
    checkTextHasNoErrors(
      """
        |class B {
        |  null match {
        |    case b @ (_: String | _: B) =>
        |  }
        |}
      """.stripMargin)
  }

  def testTypeProjection(): Unit = {
    checkTextHasNoErrors(
      """
        |class A {
        |  def traverse[F[_]] = 0
        |}
        |
        |val value = new A()
        |val result = value.traverse[({ type L[x] = Int })#L]
      """.stripMargin
    )
  }

  def testListPattern(): Unit = {
    checkTextHasNoErrors(
      """
        |val b = null match {
        |  case (param: Int) :: (rest @ _ :: _) =>
        |  case _ =>
        |}
      """.stripMargin)
  }

  def testFunctionTupleParameter(): Unit = {
    checkTextHasNoErrors("val f: ((Int, Int)) => Int = ???")
  }

  def testTraitParents(): Unit = {
    checkTextHasNoErrors("trait Foo extends (Int => String)")
  }

  def testEmptyParentheses(): Unit = {
    checkTextHasNoErrors("type Null_Unit = () => Unit")
  }

  def testSCL14395(): Unit = {
    checkTextHasNoErrors("val f: (Int => Int) => Int = ???")
  }

  def testDoubleParenthesesQuickFix(): Unit = {
    val text = s"1 + ((${CARET_MARKER}1 + 1))"
    val result = "1 + (1 + 1)"
    val hint = hintBeginning + " ((1 + 1))"
    testQuickFix(text, result, hint)
  }

  def testRepeatedParameterType(): Unit = {
    checkTextHasNoErrors("class A; def apply(pairs: (A => (A, A))*): A = new A")

    val text = s"class A; def apply(pairs: $CARET_MARKER(A)*): A = new A"
    val result = "class A; def apply(pairs: A*): A = new A"
    val hint = hintBeginning + " (A)"
    testQuickFix(text, result, hint)
  }

  def testLiteralType(): Unit = {
    val code =
      s"""
         |object Test {
         |  trait **[A, B]
         |  val a: $START(123123)$END ** (123) = ()
         |}
         |""".stripMargin
    checkTextHasErrors(code)

    val text =
      s"""
         |object Test {
         |  trait **[A, B]
         |  val a: $CARET_MARKER(123123) ** (123) = ()
         |}
         |""".stripMargin

    val result =
      s"""
         |object Test {
         |  trait **[A, B]
         |  val a: 123123 ** (123) = ()
         |}
         |""".stripMargin

    val hint = hintBeginning + " (123123)"
    testQuickFix(text, result, hint)
  }
}
