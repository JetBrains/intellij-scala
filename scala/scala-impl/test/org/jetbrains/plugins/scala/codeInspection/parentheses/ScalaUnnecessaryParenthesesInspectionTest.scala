package org.jetbrains.plugins.scala
package codeInspection
package parentheses

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture.CARET_MARKER
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestFixture.{ExpectedHighlight, TestPrepareResult}
import org.jetbrains.plugins.scala.extensions.TextRangeExt

abstract class ScalaUnnecessaryParenthesesInspectionTestBase extends ScalaInspectionTestBase {

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnnecessaryParenthesesInspection]

  protected override val description = "Unnecessary parentheses"

  protected val hintBeginning = "Remove unnecessary parentheses"

  protected def defaultSettings = UnnecessaryParenthesesSettings.default

  protected def considerClarifying = defaultSettings.copy(ignoreClarifying = false)

  protected def ignoreAroundFunctionType = defaultSettings.copy(ignoreAroundFunctionType = true)

  protected def ignoreAroundFunctionTypeParam = defaultSettings.copy(ignoreAroundFunctionTypeParam = true)

  protected def ignoreAroundFunctionExprParam = defaultSettings.copy(ignoreAroundFunctionExprParam = true)

  protected def withSettings(settings: UnnecessaryParenthesesSettings)(body: => Unit): Unit = {

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

  protected def checkTextHasErrors(text: String): Unit = {
    val expectedHighlights = configureByText(text)
    val actualHighlights = findMatchingHighlightings(text)
    val expectedParenthesesHighlights: Seq[ExpectedHighlight] = {
      val ExpectedHighlight(range) = expectedHighlights.head
      val left = TextRange.from(range.getStartOffset, 1)
      val right = TextRange.from(range.getEndOffset - 1, 1)
      val ranges = if (range.getLength >= 4) {
        val middle = range.shrink(2)
        Seq(left, middle, right)
      } else {
        Seq(left, right)
      }
      ranges.map(ExpectedHighlight)
    }
    super.assertTextHasError(expectedParenthesesHighlights, actualHighlights, allowAdditionalHighlights = true)
  }
}

class ScalaUnnecessaryParenthesesInspectionTest_Scala2 extends ScalaUnnecessaryParenthesesInspectionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version < ScalaVersion.Latest.Scala_3_0



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
       """.stripMargin
    checkTextHasErrors(selected)

    val text =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (${CARET_MARKER}even % 2 == 0) => (even + 1)
         |  case odd => 1 + (odd * 3)
         |}
       """.stripMargin
    val result =
      """
        |def f(n: Int): Int = n match {
        |  case even if even % 2 == 0 => (even + 1)
        |  case odd => 1 + (odd * 3)
        |}
      """.stripMargin
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
       """.stripMargin
    checkTextHasErrors(selected)

    val text =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => (even + 1$CARET_MARKER)
         |  case odd => 1 + (odd * 3)
         |}
      """.stripMargin
    val result =
      """
        |def f(n: Int): Int = n match {
        |  case even if (even % 2 == 0) => even + 1
        |  case odd => 1 + (odd * 3)
        |}
      """.stripMargin
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
        |List() count (a(_))""".stripMargin
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
        |    :+ new String("d"))""".stripMargin
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
    val result = "/*1*/ 6 /*2*/\n/*3*/"
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

  // SCL-17859
  def testInfixType(): Unit = checkTextHasNoErrors(
    """
      |class Plus[Lhs, Rhs]
      |class Implies[Lhs, Rhs]
      |
      |type X = (Int Plus Int) Implies Double
      |""".stripMargin
  )

  // SCL-18524
  def testTypeAroundFunctionTypeInParenlessParamClause(): Unit = checkTextHasNoErrors(
    """
      |def test[T](x: T): Unit = ()
      |test {
      |  collect: (Int =>Unit) =>
      |}
      |""".stripMargin
  )

  def test_nested_if_with_outer_else(): Unit =
    checkTextHasNoErrors("object A { if (true) (if (true) println(1)) else println(2) }")

  def test_nested_if_else_if_with_outer_else(): Unit =
    checkTextHasNoErrors("object A { if (true) (if (true) println(1) else if (true) println(2)) else println(3) }")

  def test_nested_if_else_if_else_if_with_outer_else(): Unit =
    checkTextHasNoErrors("object A { if (true) (if (true) println(1) else if (true) println(2) else if (true) println(3)) else println(4) }")

  def test_nested_if_else_with_outer_else(): Unit =
    checkTextHasErrors(s"object A { if (true) $START(if (true) println(1) else println(2))$END else println(3) }")

  def test_nested_if_else_if_else_with_outer_else(): Unit =
    checkTextHasErrors(s"object A { if (true) $START(if (true) println(1) else if (true) println(2) else println(3))$END else println(4) }")

  def test_nested_if_else_if_else_if_else_with_outer_else(): Unit =
    checkTextHasErrors(s"object A { if (true) $START(if (true) println(1) else if (true) println(2) else if (true) println(3) else println(4))$END else println(5) }")

  def test_nested_if_without_outer_else(): Unit =
    checkTextHasErrors(s"object A { if (true) $START(if (true) println(1))$END }")

  def test_nested_if_else_if_without_outer_else(): Unit =
    checkTextHasErrors(s"object A { if (true) $START(if (true) println(1) else if (true) println(2))$END }")

  def test_nested_if_else_if_else_if_without_outer_else(): Unit =
    checkTextHasErrors(s"object A { if (true) $START(if (true) println(1) else if (true) println(2) else if (true) println(3))$END }")

  def test_nested_curly_braced_if_with_outer_else(): Unit =
    checkTextHasErrors(s"object A { if (false) { $START(if (true) println(1))$END } else println(2) }")

  def test_case_clause_with_destructuring(): Unit =
    checkTextHasNoErrors(s"object A { 1 match { case (_: _) *: _ => } }")

  def test_parenthesized_non_tuple_match_scrutinee(): Unit =
    checkTextHasErrors(s"$START(1)$END match { case _ => }")

  def test_tuple_match_scrutinee(): Unit =
    checkTextHasNoErrors(s"(1, 2) match { case _ => }")

  def test_parenthesized_tuple_match_scrutinee(): Unit =
    checkTextHasErrors(s"$START((1, 2))$END match { case _ => }")

  def test_annotated_expression(): Unit = checkTextHasErrors(
    s"""import scala.annotation.nowarn
       |
       |object Scope {
       |  val foo = $START(true)$END : @nowarn("cat=deprecation")
       |}
       |""".stripMargin)

  def test_annotated_match_expression(): Unit = checkTextHasNoErrors(
    s"""import scala.annotation.nowarn
       |object Scope {
       |  (1 match { case _ => }) : @nowarn("msg=exhaustive")
       |}
       |""".stripMargin)

  def test_underscore_function(): Unit =
    checkTextHasNoErrors("object A { val x = (_.length): String => Int }")

  def test_sequence_argument(): Unit =
    checkTextHasErrors(s"""object A { def f(s: String*): Unit = (); f($START(Seq(""))$END: _*) }""")

  def test_literal_typed_expression(): Unit =
    checkTextHasErrors(s"""object A { $START("foo")$END: "foo"; $START(1)$END: 1 }""")

  def test_typed_argument(): Unit =
    checkTextHasErrors(s"""object A { val l = Seq($START("")$END: Any) }""")
}

class ScalaUnnecessaryParenthesesInspectionTest_Scala3 extends ScalaUnnecessaryParenthesesInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  def testFor(): Unit = {
    val text = "(for (x <- 0 to 2) println())"
    val expected = "for (x <- 0 to 2) println()"
    val hint = hintBeginning + " (for (...) {...})"
    testQuickFix(text, expected, hint)
  }

  def testForYield(): Unit = {
    val text = "(for (x <- 0 to 2) yield println())"
    val expected = "for (x <- 0 to 2) yield println()"
    val hint = hintBeginning + " (for (...) yield {...})"
    testQuickFix(text, expected, hint)
  }

  def testForDo(): Unit = {
    val text = "(for (x <- 0 to 2) do println())"
    val expected = "for (x <- 0 to 2) do println()"
    val hint = hintBeginning + " (for (...) do {...})"
    testQuickFix(text, expected, hint)
  }

  def testPolymorphicFunctionType(): Unit = {
    checkTextHasErrors(s"type T = $START([X] => X => Any)$END")
    checkTextHasErrors(s"type T = [X] => $START(X => Any)$END")
  }

  def testPolymorphicFunction(): Unit = {
    checkTextHasErrors(s"val v = $START([X] => (x: X) => 0)$END")
    checkTextHasErrors(s"val v = [X] => $START((x: X) => 0)$END")

    checkTextHasNoErrors("val v = [X] => (x: X) => 0")
  }

  def testTypeLambda(): Unit = {
    checkTextHasErrors(s"type T = $START([X] =>> Any)$END")
    checkTextHasErrors(s"type T = $START([X] =>> [Y] => Any)$END")
    checkTextHasErrors(s"type T = [X] =>> $START([Y] =>> Any)$END")

    checkTextHasErrors(s"type T = [X] =>> $START(X => Any)$END")
    checkTextHasErrors(s"type T = [X] =>> $START([X] => X => Any)$END")
  }

  def testMatchType(): Unit = {
    checkTextHasErrors(s"type T[A] = $START(A match { case Int => Char })$END")

    checkTextHasNoErrors(s"type T[A] = (A match { case Int => Char }) match { case Long => String }")

    checkTextHasNoErrors(s"type T[A] = (A match { case Int => Char }) => Any")
    checkTextHasNoErrors(s"type T[A] = [X] => (A match { case Int => Char }) => Any")
  }

  def testUnionTypeInMatch(): Unit = {
    // SCL-21744
    checkTextHasNoErrors(
      """
        |"1" match
        |  case s:(String|CharSequence) => println(s)
        |""".stripMargin
    )
    checkTextHasNoErrors("val s@(_: (String && Int)) = null")
    checkTextHasErrors(s"val s: $START(String && Int)$END = null")
    checkTextHasErrors(s"type X[A] = A match { case $START(Int || Float)$END => Int }")
  }
}
