package org.jetbrains.plugins.scala.refactoring.selectExpression

import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.junit.Assert.{assertEquals, fail}

abstract class SelectExpressionTestBase extends SimpleTestCase {

  protected val Caret: String = EditorTestUtil.CARET_TAG
  protected val Start: String = EditorTestUtil.SELECTION_START_TAG
  protected val End  : String = EditorTestUtil.SELECTION_END_TAG

  protected def language: Language

  protected def doCanSelectAtCaretTest(
    fileText: String,
    expectedExpressions: String*
  ): Unit = {
    val (file, offset) = parseTextWithCaret(fileText, language)
    val expressionsWithMaybeReasons = ScalaRefactoringUtil.possibleExpressionsWithCantIntroduceReason(file, offset)
    val expressionsWithoutReason = expressionsWithMaybeReasons.filter(_._2.isEmpty).map(_._1)
    assertEquals(
      expectedExpressions.toSeq,
      expressionsWithoutReason.map(_.getText)
    )
  }

  protected def doCantSelectAtCaretTest(
    fileText: String,
    expectedReasons: String*
  ): Unit = {
    val (file, offset) = parseTextWithCaret(fileText, language)

    val expressionsWithMaybeReasons = ScalaRefactoringUtil.possibleExpressionsWithCantIntroduceReason(file, offset)
    val reasons = expressionsWithMaybeReasons.collect {
      case (_, Some(r)) => r
      case (expr, None) =>
        fail(s"unexpected selectable expression ${expr.getText}")
    }

    assertEquals(
      expectedReasons,
      reasons.distinct
    )
  }

  // TODO: test some more high-level IDEA APIs, not the implementation details, don't test ScalaRefactoringUtil directly
  protected def doCantSelectRangeTest(
    fileText: String,
    expectedReason: String
  ): Unit = {
    val (file, selection) = parseTextWithRange(fileText, language)

    val start = selection.getStartOffset
    val end = selection.getEndOffset
    val elementsAtRange = ScalaPsiUtil.elementsAtRange[ScExpression](file, start, end)

    val expressionsWithMaybeReasons = elementsAtRange.map(e => (e, ScalaRefactoringUtil.cannotBeIntroducedReason(e)))
    val reasons = expressionsWithMaybeReasons.collect {
      case (_, Some(r)) => r
      case (expr, None) =>
        fail(s"unexpected expression to select: ${expr.getText}")
    }
    assertEquals(
      Seq(expectedReason),
      reasons.distinct
    )
  }

  private def parseText1(text: String, lang: Language): ScalaFile =
    PsiFileFactory.getInstance(fixture.getProject)
      .createFileFromText("foo.scala", lang, text, false, false)
      .asInstanceOf[ScalaFile]

  private def parseTextWithCaret(text: String, lang: Language): (ScalaFile, Int) = {
    val trimmed = text.trim

    assertEquals("expected single caret", 1, Caret.r.findAllMatchIn(text).toSeq.size)

    val caretPos = trimmed.indexOf(Caret)
    val withoutCaret = trimmed.replaceFirst(Caret, "")
    val parsed = parseText1(withoutCaret, lang)
    (parsed, caretPos)
  }

  private def parseTextWithRange(text: String, lang: Language): (ScalaFile, TextRange) = {
    val trimmed = text.trim

    assertEquals("expected single range", 1, Start.r.findAllMatchIn(text).toSeq.size)
    assertEquals("expected single range", 1, End.r.findAllMatchIn(text).toSeq.size)

    val startPos = trimmed.indexOf(Start)
    val withoutStart = trimmed.replaceFirst(Start, "")
    val endPos = withoutStart.indexOf(End)
    val withoutEnd = withoutStart.replaceFirst(End, "")
    val parsed = parseText1(withoutEnd, lang)
    (parsed, TextRange.create(startPos, endPos))
  }


  def testInfix(): Unit = doCanSelectAtCaretTest(s"${Caret}1 + 2", "1", "1 + 2")

  def testInfix2(): Unit = doCanSelectAtCaretTest(s"1 + ${Caret}2", "2", "1 + 2")

  def testNoInfixOp(): Unit = doCanSelectAtCaretTest(s"1 ${Caret}+ 2", "1 + 2")

  def testMethodCallFromRef(): Unit = doCanSelectAtCaretTest(s"${Caret}abc(1)", "abc", "abc(1)")

  def testMethodCallFromArg(): Unit = doCanSelectAtCaretTest(s"abc(${Caret}1)", "1", "abc(1)")

  def testGenericCallNoRef(): Unit = doCanSelectAtCaretTest(s"${Caret}foo[Int](1)", "foo[Int]", "foo[Int](1)")

  def testNamedArgument(): Unit = doCanSelectAtCaretTest(s"foo(${Caret}age = 33)", "foo(age = 33)")

  def testCantSelect_TopLevelClassParam(): Unit = doCantSelectAtCaretTest(
    s"class A(x: Int = ${Caret}42",
    "Refactoring is not supported for parameters of top level classes"
  )

  def testCantSelect_SelfInvocationArg(): Unit = doCantSelectAtCaretTest(
    s"""
       |class A(i: Int) {
       |  def this(s: String) = {
       |    this(${Caret}s.length)
       |  }
       |}""".stripMargin,
    "Refactoring is not supported for arguments of self-invocation in the constructor body",
    "Refactoring is not supported for the constructor call in auxiliary constructor"
  )

  def testCantSelect_SelfInvocationArg_DeepExpression(): Unit = doCantSelectAtCaretTest(
    s"""
       |class A(i: Int) {
       |  def this(s: String) = {
       |    this(s.length + 1 + ${Caret}2 + 3 + 4)
       |  }
       |}""".stripMargin,
    "Refactoring is not supported for arguments of self-invocation in the constructor body",
    "Refactoring is not supported for the constructor call in auxiliary constructor"
  )

  def testCantSelect_SelfInvocationArg_MultipleExpressions(): Unit = doCantSelectAtCaretTest(
    s"""class A(x: Int) {
       |  def this() = {
       |    this(${Caret}2)
       |    println(42)
       |  }
       |}
       |""".stripMargin,
    "Refactoring is not supported for arguments of self-invocation in the constructor body",
    "Refactoring is not supported for the constructor call in auxiliary constructor"
  )

  def testCantSelect_SelfInvocation(): Unit = doCantSelectRangeTest(
    s"""class A(x: Int) {
       |  def this() = {
       |    ${Start}this(2)$End
       |    println(42)
       |  }
       |}
       |""".stripMargin,
    "Refactoring is not supported for the constructor call in auxiliary constructor"
  )

  def testCantSelect_SelfInvocationArg_NoBraces(): Unit = doCantSelectAtCaretTest(
    s"""
       |class A(i: Int) {
       |  def this(s: String) =
       |    this(${Caret}s.length)
       |}""".stripMargin,
    "Refactoring is not supported for arguments of self-invocation in the constructor body",
    "Refactoring is not supported for the constructor call in auxiliary constructor"
  )

  def testCanSelect_NonSelfInvocationInsideConstructor(): Unit = doCanSelectAtCaretTest(
    s"""class A(x: Int) {
       |  def this() = {
       |    this(2)
       |    println(${Caret}42)
       |  }
       |}
       |""".stripMargin,
    "42",
    "println(42)"
  )

  def testCanSelect_WholeConstructorBody(): Unit = doCantSelectRangeTest(
    s"""class A(x: Int) {
       |  def this() = $Start{
       |    this(2)
       |    println(42)
       |  }$End
       |}
       |""".stripMargin,
    "Selected block shouldn't be presented as constructor expression"
  )

  def testInterpolatedString(): Unit = doCanSelectAtCaretTest(s"""${Caret}s"foo"""", """s"foo"""")

  def testInterpolatedStringInjection(): Unit = doCanSelectAtCaretTest(s"""s"foo$$${Caret}bar"""", "bar", """s"foo$bar"""")

  def testInterpolatedStringBlockInjection(): Unit = doCanSelectAtCaretTest(
    s"""val x = s"str $${${Caret}1 + 2}"""",
    "1",
    "1 + 2",
    "{1 + 2}",
    s"""s"str $${1 + 2}""""
  )

  def testOneExpressionBlockWithoutBraces(): Unit = doCanSelectAtCaretTest(
    s"""1 match {
       |  case 1 => ${Caret}"42"
       |}""".stripMargin, """"42"""")

  def testLargeMethodCall(): Unit = doCanSelectAtCaretTest(
    s"""foo(1)
       |  ${Caret}.bar(2)
       |  .baz(3)
       |""".stripMargin,

    """foo(1)
      |  .bar""".stripMargin,
    """foo(1)
      |  .bar(2)""".stripMargin,
    """foo(1)
      |  .bar(2)
      |  .baz""".stripMargin,
    """foo(1)
      |  .bar(2)
      |  .baz(3)""".stripMargin)

  def testMatchStmt(): Unit = doCanSelectAtCaretTest(
    s"""${Caret}1 match {
       |  case 0 => "zero"
       |  case 1 => "one"
       |}
       |""".stripMargin,
    "1",
    """1 match {
      |  case 0 => "zero"
      |  case 1 => "one"
      |}""".stripMargin
  )

  def testMatchStmtFromKeyword(): Unit = doCanSelectAtCaretTest (
    s"""1 m${Caret}atch {
       |  case 0 => "zero"
       |  case 1 => "one"
       |}
       |""".stripMargin,
    """1 match {
      |  case 0 => "zero"
      |  case 1 => "one"
      |}""".stripMargin
  )

  def testMatchStmtFromCase(): Unit = doCanSelectAtCaretTest(
    s"""1 match {
       |  ${Caret}case 0 => "zero"
       |  case 1 => "one"
       |}
       |""".stripMargin,
    """1 match {
      |  case 0 => "zero"
      |  case 1 => "one"
      |}""".stripMargin
  )

  def testNoMatchStmtFromCaseBlock(): Unit = doCanSelectAtCaretTest(
    s"""1 match {
       |  case 0 => ${Caret}"zero"
       |  case 1 => "one"
       |}
       |""".stripMargin,
    """"zero""""
  )

  def testIfStmt(): Unit = doCanSelectAtCaretTest(
    s"""if (${Caret}true) false
       |else true
       |""".stripMargin,
    "true",
    """if (true) false
      |else true""".stripMargin
  )
}

//noinspection RedundantBlock
class SelectExpressionTest extends SelectExpressionTestBase {
  protected def language: Language = ScalaLanguage.INSTANCE
}

class SelectExpressionTest_Scala_3_0 extends SelectExpressionTestBase {
  protected def language: Language = Scala3Language.INSTANCE

  def testCanSelect_NonSelfInvocationInsideConstructor_BracelessSyntax(): Unit = doCanSelectAtCaretTest(
    s"""class A(x: Int) {
       |  def this() =
       |    this(2)
       |    println(${Caret}42)
       |}
       |""".stripMargin,
    "42", "println(42)"
  )

  def testCantSelect_SelfInvocationArg_BracelessSyntax(): Unit = doCantSelectAtCaretTest(
    s"""class A(x: Int) {
       |  def this() =
       |    this(${Caret}2)
       |    println(42)
       |}
       |""".stripMargin,
    "Refactoring is not supported for arguments of self-invocation in the constructor body",
    "Refactoring is not supported for the constructor call in auxiliary constructor"
  )

  def testCantSelect_SelfInvocation_BracelessSyntax(): Unit = doCantSelectRangeTest(
    s"""class A(x: Int) {
       |  def this() =
       |    ${Start}this(2)$End
       |    println(42)
       |}
       |""".stripMargin,
    "Refactoring is not supported for the constructor call in auxiliary constructor"
  )
}