package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

import com.intellij.application.options.CodeStyle
import org.jetbrains.plugins.scala.lang.actions.editor.enter.Scala2AndScala3EnterActionCommonTests
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

/** NOTE: much more tests are generated and run in [[Scala3BracelessSyntaxEnterHandlerTest_Exhaustive]] */
class Scala3EnterTest extends DoEditorStateTestOps with Scala2AndScala3EnterActionCommonTests {

  import Scala3TestDataBracelessCode._

  private def doTypingTestInWrapperContexts(
    contextCode: String,
    codeToType: CodeWithDebugName,
    wrapperContexts: Seq[CodeWithDebugName]
  ): Unit =
    for {
      wrapperContext <- wrapperContexts
    } {
      val contextCodeNew = injectCodeWithIndentAdjust(contextCode, wrapperContext.code)
      checkIndentAfterTypingCode(contextCodeNew, codeToType.code, myFixture)
    }

  private def doEnterAfterFunctionTypeArrow(contextCode: String): Unit = {
    doTypingTestInWrapperContexts(contextCode, CodeToType.BlankLines, WrapperCodeContexts.AllContexts)
    assert(contextCode.contains("=>"))

    val contextCode2 = contextCode.replace("=>", "?=>")
    doTypingTestInWrapperContexts(contextCode2, CodeToType.BlankLines, WrapperCodeContexts.AllContexts)
  }

  def testAfterFunctionTypeArrow_1(): Unit =
    doEnterAfterFunctionTypeArrow(s"""type Contextual1[T] = ExecutionContext =>$CARET""")

  def testAfterFunctionTypeArrow_2(): Unit =
    doEnterAfterFunctionTypeArrow(s"""type Contextual1[T] = ExecutionContext =>$CARET   T""")

  def testAfterFunctionTypeArrow_3(): Unit =
    doEnterAfterFunctionTypeArrow(s"""type Contextual1[T] = ExecutionContext =>   ${CARET}T""")

  def testAfterFunctionTypeArrow_4(): Unit =
    doEnterAfterFunctionTypeArrow(s"""type Contextual1[T] = ExecutionContext =>  $CARET  T""")

  // TODO ignored until we fix parsing of comments, see SCL
  def _testFunctionWithDocComment(): Unit = doEnterTest(
    s"""// foo
       |def foo =$CARET
       |  ???""".stripMargin,
    s"""// foo
       |def foo =
       |  $CARET
       |  ???""".stripMargin)

  def textTopLevelFunctionWithNonEmptyBody_EOF(): Unit = {
    def doMyTest(context: String): Unit =
      checkIndentAfterTypingCode(context, CodeToType.BlankLines.code, myFixture)

    doMyTest(
      s"""def foo =
         |  1$CARET""".stripMargin
    )
    doMyTest(
      s"""def foo =
         |  1$CARET   ${""}""".stripMargin
    )
    doMyTest(
      s"""def foo =
         |  1   $CARET   ${""}""".stripMargin
    )
    doMyTest(
      s"""def foo =
         |  1   $CARET""".stripMargin
    )
    doMyTest(
      s"""def foo =
         |  1   $CARET
         |""".stripMargin
    )
    doMyTest(
      s"""def foo =
         |  1   $CARET   ${""}
         |""".stripMargin

    )
    doMyTest(
      s"""def foo =
         |  1$CARET   ${""}
         |""".stripMargin
    )
    doMyTest(
      s"""def foo =
         |  1
         |
         |  $CARET""".stripMargin
    )
    doMyTest(
      s"""def foo =
         |  1
         |
         |  $CARET   ${""}""".stripMargin
    )
    doMyTest(
      s"""def foo =
         |  1
         |
         |  $CARET
         |     ${""}
         |     ${""}
         |  """.stripMargin
    )
  }

  def testUnindentedCaret_InIndentedFunctionBody_BetweenMultipleStatements(): Unit = {
    val text1 =
      s"""class A {
         |  def foo =
         |    val x = 1
         |    val y = 2
         |  $CARET
         |    val z = 3
         |}""".stripMargin
    val text2 =
      s"""class A {
         |  def foo =
         |    val x = 1
         |    val y = 2
         |
         |    $CARET
         |    val z = 3
         |}""".stripMargin
    doTestForAllDefTypes(text1, text2)
  }

  protected def doTestForAllDefTypes(textBefore: String, textAfter: String): Unit = {
    doEnterTest(textBefore, textAfter)
    if (textBefore.contains("def")) {
      doEnterTest(textBefore.replace("def", "val"), textAfter.replace("def", "val"))
      doEnterTest(textBefore.replace("def", "var"), textAfter.replace("def", "var"))
    }
  }

  def testUnindentedCaret_Extension_Collective(): Unit =
    doEnterTest(
      s"""object A {
         |  extension[T] (xs: List[T])(using Ordering[T])
         |    def smallest(n: Int): List[T] = xs.sorted.take(n)
         |$CARET
         |    def smallestIndices(n: Int): List[Int] =
         |      val limit = smallest(n).max
         |      xs.zipWithIndex.collect { case (x, i) if x <= limit => i }
         |}""".stripMargin,
      s"""object A {
         |  extension[T] (xs: List[T])(using Ordering[T])
         |    def smallest(n: Int): List[T] = xs.sorted.take(n)
         |
         |    $CARET
         |    def smallestIndices(n: Int): List[Int] =
         |      val limit = smallest(n).max
         |      xs.zipWithIndex.collect { case (x, i) if x <= limit => i }
         |}""".stripMargin
    )

  def testAfterFunctionBodyOnSameLineWithEquals(): Unit = doEditorStateTest(myFixture, (
    s"""class B:
       |  def foo = 1$CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText.Enter
  ), (
    s"""class B:
       |  def foo = 1
       |  $CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText("def baz = 23")
  ), (
    s"""class B:
       |  def foo = 1
       |  def baz = 23$CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText("def baz = 23")
  ))

  def testAfterFunctionBodyOnSameLineWithEquals_IndentedCaretAfterCompleteBody(): Unit = doEnterTest(
    s"""class B:
       |  def foo = 1
       |    $CARET
       |
       |  def bar = 2
       |""".stripMargin,
    s"""class B:
       |  def foo = 1
       |
       |  $CARET
       |
       |  def bar = 2
       |""".stripMargin
  )

  def testAfterFunctionBodyOnSameLineWithEquals_InfixOp(): Unit = doEditorStateTest(myFixture, (
    s"""class B:
       |  def foo = 1 + $CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText.Enter
  ), (
    s"""class B:
       |  def foo = 1 +
       |  $CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText("42")
  ), (
    s"""class B:
       |  def foo = 1 +
       |    42$CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText("def baz = 23")
  ))

  def testInNestedFunction(): Unit = doEnterTest(
    s"""class A {
       |  def f1 =
       |    1
       |    2
       |    def f2 =
       |      1
       |      2$CARET
       |}
       |""".stripMargin,
    s"""class A {
       |  def f1 =
       |    1
       |    2
       |    def f2 =
       |      1
       |      2
       |      $CARET
       |}
       |""".stripMargin
  )

  private def doEnterTestWithAndWithoutTabs(before: String, after: String): Unit = {
    doEnterTest(before, after)

    val tabSizeNew = 3

    val indentOptions = CodeStyle.getSettings(getProject).getIndentOptions(ScalaFileType.INSTANCE)
    val useTabsBefore = indentOptions.USE_TAB_CHARACTER
    val tabSizeBefore = indentOptions.TAB_SIZE

    try {
      indentOptions.TAB_SIZE = tabSizeNew
      indentOptions.USE_TAB_CHARACTER = true

      def useTabs(text: String): String = text.replaceAll(" " * tabSizeNew, "\t")

      val beforeWithTabs = useTabs(before)
      val afterWithTabs = useTabs(after)
      doEnterTest(beforeWithTabs, afterWithTabs)
    } finally {
      indentOptions.USE_TAB_CHARACTER = useTabsBefore
      indentOptions.TAB_SIZE = tabSizeBefore
    }
  }

  def testCaretIsIndentedToTheRightFromLastElementInIndentationContext_0(): Unit = doEditorStateTest(myFixture, (
    s"""class B:
       |  def foo =     $CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText("\nprintln(1)")
  ), (
    s"""class B:
       |  def foo =
       |    println(1)$CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText("   \n    ")
  ), (
    s"""class B:
       |  def foo =
       |    println(1)
       |        $CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText("\nprintln(2)")
  ), (
    s"""class B:
       |  def foo =
       |    println(1)
       |
       |    println(2)$CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText("   \n    ")
  ), (
    s"""class B:
       |  def foo =
       |    println(1)
       |
       |    println(2)
       |        $CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText("\nprintln(3)")
  ), (
    s"""class B:
       |  def foo =
       |    println(1)
       |
       |    println(2)
       |
       |    println(3)$CARET
       |
       |  def bar = 2
       |""".stripMargin,
    TypeText.Ignored
  ))

  def testCaretIsIndentedToTheRightFromLastElementInIndentationContext_1(): Unit = doEnterTestWithAndWithoutTabs(
    s"""class A:
       |    def foo =
       |        println("start")
       |        if 2 + 2 == 42 then
       |            println(1)
       |            println(2)
       |              $CARET
       |""".stripMargin,
    s"""class A:
       |    def foo =
       |        println("start")
       |        if 2 + 2 == 42 then
       |            println(1)
       |            println(2)
       |
       |            $CARET
       |""".stripMargin,
  )

  def testCaretIsUnindentedToTheLeftFromLastElementInIndentationContext_0(): Unit = doEnterTestWithAndWithoutTabs(
    s"""class A:
       |    def foo =
       |        println("start")
       |        if 2 + 2 == 42 then
       |            println(1)
       |            println(2)
       |          $CARET
       |""".stripMargin,
    s"""class A:
       |    def foo =
       |        println("start")
       |        if 2 + 2 == 42 then
       |            println(1)
       |            println(2)
       |
       |        $CARET
       |""".stripMargin,
  )

  def testCaretIsUnindentedToTheLeftFromLastElementInIndentationContext_1(): Unit = doEnterTestWithAndWithoutTabs(
    s"""class A:
       |    def foo =
       |        println("start")
       |        if 2 + 2 == 42 then
       |            println(1)
       |            println(2)
       |      $CARET
       |""".stripMargin,
    s"""class A:
       |    def foo =
       |        println("start")
       |        if 2 + 2 == 42 then
       |            println(1)
       |            println(2)
       |
       |    $CARET
       |""".stripMargin,
  )

  def testCaretIsUnindentedToTheLeftFromLastElementInIndentationContext_2(): Unit = doEnterTestWithAndWithoutTabs(
    s"""class A:
       |    def foo =
       |        println("start")
       |        if 2 + 2 == 42 then
       |            println(1)
       |            println(2)
       |  $CARET
       |""".stripMargin,
    s"""class A:
       |    def foo =
       |        println("start")
       |        if 2 + 2 == 42 then
       |            println(1)
       |            println(2)
       |
       |$CARET
       |""".stripMargin,
  )

  def test_UnindentedCaret_InClass_WithBraces(): Unit = doEnterTest(
    s"""class A {
       |  def f1 =
       |    1
       |    2
       |$CARET
       |}
       |""".stripMargin,
    s"""class A {
       |  def f1 =
       |    1
       |    2
       |
       |  $CARET
       |}
       |""".stripMargin
  )

  def test_UnindentedCaret_InClass_WithEndMarker(): Unit = doEnterTest(
    s"""class A:
       |  def f1 =
       |    1
       |    2
       |$CARET
       |end A
       |""".stripMargin,
    s"""class A:
       |  def f1 =
       |    1
       |    2
       |
       |  $CARET
       |end A
       |""".stripMargin
  )

  def test_UnindentedCaret_InClass_WithoutEndMarker(): Unit = doEnterTest(
    s"""class A:
       |  def f1 =
       |    1
       |    2
       |$CARET
       |""".stripMargin,
    s"""class A:
       |  def f1 =
       |    1
       |    2
       |
       |$CARET
       |""".stripMargin
  )

  def test_UnindentedCaret_InNestedClass_WithBraces(): Unit = doEnterTest(
    s"""class Outer {
       |  class A {
       |    def f1 =
       |      1
       |      2
       |  $CARET
       |  }
       |}""".stripMargin,
    s"""class Outer {
       |  class A {
       |    def f1 =
       |      1
       |      2
       |
       |    $CARET
       |  }
       |}""".stripMargin
  )

  def test_UnindentedCaret_InNestedClass_WithEndMarker(): Unit = doEnterTest(
    s"""class Outer {
       |  class A:
       |    def f1 =
       |      1
       |      2
       |  $CARET
       |  end A
       |}""".stripMargin,
    s"""class Outer {
       |  class A:
       |    def f1 =
       |      1
       |      2
       |
       |    $CARET
       |  end A
       |}""".stripMargin
  )

  def test_UnindentedCaret_InNestedClass_WithoutEndMarker(): Unit = doEnterTest(
    s"""class Outer {
       |  class A:
       |    def f1 =
       |      1
       |      2
       |  $CARET
       |}""".stripMargin,
    s"""class Outer {
       |  class A:
       |    def f1 =
       |      1
       |      2
       |
       |  $CARET
       |}""".stripMargin
  )

  def test_UnindentedCaret_InNestedClass_WithoutEndMarker_1(): Unit = doEnterTest(
    s"""class Outer {
       |  class A:
       |    def f1 =
       |      1
       |      2
       |$CARET
       |}""".stripMargin,
    s"""class Outer {
       |  class A:
       |    def f1 =
       |      1
       |      2
       |
       |  $CARET
       |}""".stripMargin
  )

  def test_Return_WithIndentationBasedBlock(): Unit = {
    val contextCode =
      s"""def foo123: String =
         |  return$CARET
         |""".stripMargin
    doTypingTestInWrapperContexts(contextCode, CodeToType.BlankLines, WrapperCodeContexts.AllContexts)
    doTypingTestInWrapperContexts(contextCode, CodeToType.BlockStatements, WrapperCodeContexts.AllContexts)
  }

  def testAfterCodeInCaseClause_EOF_1(): Unit = doEnterTest_NonStrippingTrailingSpaces(
    s"""Option(42) match
       |  case Some(1) =>
       |    111$CARET""".stripMargin,
    s"""Option(42) match
       |  case Some(1) =>
       |    111
       |    $CARET""".stripMargin
  )

  def testAfterCodeInCaseClause_EOF_2(): Unit = doEnterTest_NonStrippingTrailingSpaces(
    s"""Option(42) match
       |  case Some(1) =>
       |    111$CARET  ${""}""".stripMargin,
    s"""Option(42) match
       |  case Some(1) =>
       |    111
       |    $CARET""".stripMargin
  )

  def testAfterCodeInCaseClause_EOF_3(): Unit = doEnterTest_NonStrippingTrailingSpaces(
    s"""Option(42) match
       |  case Some(1) =>
       |    111   $CARET  ${""}""".stripMargin,
    s"""Option(42) match
       |  case Some(1) =>
       |    111   ${""}
       |    $CARET""".stripMargin
  )

  def testAfterCodeInCaseClause_EOF_4(): Unit = doEnterTest_NonStrippingTrailingSpaces(
    s"""Option(42) match
       |  case Some(1) =>
       |    111$CARET
       |""".stripMargin,
    s"""Option(42) match
       |  case Some(1) =>
       |    111
       |    $CARET
       |""".stripMargin
  )

  def testAfterCodeInMiddleCaseClause_SameLine(): Unit = {
    doEnterTest(
      s"""42 match
         |  case first => 1$CARET
         |  case last  => 1
         |""".stripMargin,
      s"""42 match
         |  case first => 1
         |  $CARET
         |  case last  => 1
         |""".stripMargin
    )

    doEnterTest(
      s"""42 match
         |  case first => 1
         |      $CARET
         |  case last  => 1
         |""".stripMargin,
      s"""42 match
         |  case first => 1
         |
         |  $CARET
         |  case last  => 1
         |""".stripMargin
    )
  }

  def testAfterCodeInLastCaseClause_SameLine(): Unit = {
    doEnterTest(
      s"""42 match
         |  case first => 1
         |  case last  => 1$CARET
         |""".stripMargin,
      s"""42 match
         |  case first => 1
         |  case last  => 1
         |  $CARET
         |""".stripMargin
    )

    doEnterTest(
      s"""42 match
         |  case first => 1
         |  case last  => 1
         |      $CARET
         |""".stripMargin,
      s"""42 match
         |  case first => 1
         |  case last  => 1
         |
         |  $CARET
         |""".stripMargin
    )
  }

  def testAfterCodeInLastCaseClause_EOF(): Unit =
    doEnterTest(
      s"""42 match
         |  case first => 1
         |  case last  =>$CARET""".stripMargin,
      s"""42 match
         |  case first => 1
         |  case last  =>
         |    $CARET""".stripMargin
    )

  def testAfterCodeInLastCaseClause_Unindented(): Unit = {
    doEnterTest(
      s"""42 match
         |  case last  =>
         |$CARET""".stripMargin,
      s"""42 match
         |  case last  =>
         |
         |$CARET""".stripMargin,
      s"""42 match
         |  case last  =>
         |
         |
         |$CARET""".stripMargin,
    )

    doEnterTest(
      s"""{
         |  {
         |    42 match
         |      case last =>
         |    $CARET
         |  }
         |}""".stripMargin,
      s"""{
         |  {
         |    42 match
         |      case last =>
         |
         |    $CARET
         |  }
         |}""".stripMargin,
      s"""{
         |  {
         |    42 match
         |      case last =>
         |
         |
         |    $CARET
         |  }
         |}""".stripMargin,
    )
  }

  // SCL-22247
  def testEnterBeforeBlockExpr(): Unit = doEnterTest(
    s"""val x = println {$CARET
       |  y => y
       |}""".stripMargin,
    s"""val x = println {
       |  $CARET
       |  y => y
       |}""".stripMargin,
  )

  def testEnterBeforeBlockExpr3(): Unit = doEnterTest(
    s"""println:$CARET
       |  y => y
       |""".stripMargin,
    s"""println:
       |  $CARET
       |  y => y
       |""".stripMargin,
  )

  // SCL-21885
  def testEnterAfterIndentedParams(): Unit = doEnterTest(
    s"""println:
       |  y =>$CARET
       |""".stripMargin,
    s"""println:
       |  y =>
       |    $CARET
       |""".stripMargin,
  )

  def testEnterAfterIndentedParamsInBraces(): Unit = doEnterTest(
    s"""Seq(1).map:
       |  y =>$CARET
       |""".stripMargin,
    s"""Seq(1).map:
       |  y =>
       |    $CARET
       |""".stripMargin,
  )

  def testEnterHandlerShouldRespectIndentOptions(): Unit = {
    val settings = CodeStyle.getSettings(getProject).getCommonSettings(ScalaLanguage.INSTANCE)
    val indentOptions = settings.getIndentOptions

    val USE_TAB_CHARACTER_BEFORE = indentOptions.USE_TAB_CHARACTER
    val TAB_SIZE_BEFORE = indentOptions.TAB_SIZE
    val INDENT_SIZE_BEFORE = indentOptions.INDENT_SIZE

    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.TAB_SIZE = 3
    indentOptions.INDENT_SIZE = 5

    try doEnterTest(
      s"""class A {
         |\t  class B {
         |\t\t\t 1 match
         |\t\t\t\tcase 11 =>$CARET
         |\t\t\t\tcase 22 =>
         |\t  }
         |}
         |""".stripMargin,
      s"""class A {
         |\t  class B {
         |\t\t\t 1 match
         |\t\t\t\tcase 11 =>
         |\t\t\t\t\t  $CARET
         |\t\t\t\tcase 22 =>
         |\t  }
         |}
         |""".stripMargin
    )
    finally {
      indentOptions.USE_TAB_CHARACTER = USE_TAB_CHARACTER_BEFORE
      indentOptions.TAB_SIZE = TAB_SIZE_BEFORE
      indentOptions.INDENT_SIZE = INDENT_SIZE_BEFORE
    }
  }

  def testEnterAfterEnumCase(): Unit = {
    val before =
      s"""enum ListEnum1[+A]:
         |  case Cons(h: A, t: ListEnum1[A])
         |  case Empty$CARET
         |""".stripMargin
    val after =
      s"""enum ListEnum1[+A]:
         |  case Cons(h: A, t: ListEnum1[A])
         |  case Empty
         |  $CARET
         |""".stripMargin

    doEnterTestWithAndWithoutTabs(before, after)
  }

  def testEnterAfterEnumCase_1(): Unit = {
    val before =
      s"""enum ListEnum1[+A]:
         |  case Cons(h: A, t: ListEnum1[A])
         |  case Empty,$CARET Empty42
         |""".stripMargin
    val after =
      s"""enum ListEnum1[+A]:
         |  case Cons(h: A, t: ListEnum1[A])
         |  case Empty,
         |  ${CARET}Empty42
         |""".stripMargin

    doEnterTestWithAndWithoutTabs(before, after)
  }

  def testInExtendsList_AfterLastWith_IndentationBasedSyntax(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""abstract class B(x: Int)
         |  extends A
         |    with G1[G2[G3[G4]]]$CARET :
         |  def foo1 = 1
         |  def foo2 = 1""".stripMargin,
      s"""abstract class B(x: Int)
         |  extends A
         |    with G1[G2[G3[G4]]]
         |    $CARET :
         |  def foo1 = 1
         |  def foo2 = 1""".stripMargin
    )
  }

  def testEnterAtWhitespaceAfterBlockComment(): Unit = {
    checkGeneratedTextAfterEnter(
      s"/* blub */ $CARET test",
      s"""/* blub */${" "}
         |${CARET}test
         |""".stripMargin.trim
    )
  }

  private def runEnterTestInContexts(
    bodyBefore: String,
    bodyAfter: String,
    indentedRegionContexts: Seq[String],
    wrapperContexts: Seq[CodeWithDebugName],
    additionalBodyIndentSize: Int = 2,
    additionalBodyPrefix: String = "\n",
  ): Unit = {
    for {
      wrapperContext <- wrapperContexts
      indentationContext <- indentedRegionContexts
    } {
      val contextFinal = Scala3TestDataBracelessCode.injectCodeWithIndentAdjust(wrapperContext.code, indentationContext)

      val beforeIndented = TestIndentUtils.addIndentToAllLines(bodyBefore, additionalBodyIndentSize)
      val afterIndented = TestIndentUtils.addIndentToAllLines(bodyAfter, additionalBodyIndentSize)

      val beforeFinal = TestIndentUtils.injectCodeWithIndentAdjust(additionalBodyPrefix + beforeIndented, contextFinal, CARET)
      val afterFinal = TestIndentUtils.injectCodeWithIndentAdjust(additionalBodyPrefix + afterIndented, contextFinal, CARET)
      checkGeneratedTextAfterEnter(beforeFinal, afterFinal)
    }
  }

  private def runEnterTestInAllWrapperContexts(
    bodyBefore: String,
    bodyAfter: String,
    indentedRegionContexts: Seq[String],
    additionalBodyIndentSize: Int = 2
  ): Unit = {
    runEnterTestInContexts(
      bodyBefore,
      bodyAfter,
      indentedRegionContexts,
      WrapperCodeContexts.AllContexts,
      additionalBodyIndentSize
    )
  }

  private def runEnterTestInAllIndentationBlockContexts(
    bodyBefore: String,
    bodyAfter: String,
    additionalBodyIndentSize: Int = 2
  ): Unit = {
    runEnterTestInContexts(
      bodyBefore,
      bodyAfter,
      Scala3TestDataBracelessCode.IndentedBlockContexts.AllContextsAcceptingStatements,
      Scala3TestDataBracelessCode.WrapperCodeContexts.Empty :: Nil,
      additionalBodyIndentSize
    )
  }


  def testAfterComment_InTheEndOfIndentedRegion_EmptyRegion(): Unit = {
    runEnterTestInAllWrapperContexts(
      s"""// line comment$CARET""",
      s"""// line comment
         |$CARET""".stripMargin,
      IndentedBlockContexts.AllContextsAcceptingStatements
    )

    checkGeneratedTextAfterEnter(
      s"""42 match
         |  case _ =>
         |    // line comment$CARET
         |
         |// separator
         |
         |42 match
         |  case _ =>
         |    // line comment$CARET""".stripMargin,
      s"""42 match
         |  case _ =>
         |    // line comment
         |    $CARET
         |
         |// separator
         |
         |42 match
         |  case _ =>
         |    // line comment
         |    $CARET""".stripMargin
    )
  }

  def testAfterComment_InTheEndOfIndentedRegion_NonEmptyRegion(): Unit = {
    runEnterTestInAllWrapperContexts(
      s"""println()
         |// line comment$CARET""".stripMargin,
      s"""println()
         |// line comment
         |$CARET""".stripMargin,
      IndentedBlockContexts.AllContextsAcceptingStatements
    )

    checkGeneratedTextAfterEnter(
      s"""42 match
         |  case _ =>
         |    println()
         |    // line comment$CARET
         |
         |// separator
         |
         |42 match
         |  case _ =>
         |    println()
         |    // line comment$CARET""".stripMargin,
      s"""42 match
         |  case _ =>
         |    println()
         |    // line comment
         |    $CARET
         |
         |// separator
         |
         |42 match
         |  case _ =>
         |    println()
         |    // line comment
         |    $CARET""".stripMargin
    )
  }

  def testAfterComment_InTheEndOfIndentedBlock_UnindentedComment(): Unit = {
    runEnterTestInAllWrapperContexts(
      s"""  println()
         |// line comment$CARET""".stripMargin,
      s"""  println()
         |// line comment
         |$CARET""".stripMargin,
      IndentedBlockContexts.AllContextsAcceptingStatements,
      additionalBodyIndentSize = 0
    )

    checkGeneratedTextAfterEnter(
      s"""42 match
         |  case _ =>
         |
         |// line comment$CARET
         |
         |// separator
         |
         |42 match
         |  case _ =>
         |
         |// line comment$CARET""".stripMargin,
      s"""42 match
         |  case _ =>
         |
         |// line comment
         |$CARET
         |
         |// separator
         |
         |42 match
         |  case _ =>
         |
         |// line comment
         |$CARET""".stripMargin
    )
  }

  def testAfterLineCommentAfterIndentedCodeOnSameLine(): Unit = {
    runEnterTestInAllWrapperContexts(
      s"""1 // line comment$CARET""".stripMargin,
      s"""1 // line comment
         |$CARET""".stripMargin,
      IndentedBlockContexts.AllContextsAcceptingStatements
    )

    runEnterTestInAllWrapperContexts(
      s"""1 // line comment 1
         |2 // line comment 2$CARET
         |""".stripMargin,
      s"""1 // line comment 1
         |2 // line comment 2
         |$CARET
         |""".stripMargin,
      IndentedBlockContexts.AllContextsAcceptingStatements
    )
  }

  // FIXME when we fix parsing // line comments (we should attach them to the element on the line)
  //  def testBeforeLineCommentAfterIndentedCodeOnSameLine(): Unit = {
  //    runEnterTestInContexts(
  //      s"""1 $CARET// line comment""".stripMargin,
  //      s"""1 ${""}
  //         |$CARET// line comment""".stripMargin,
  //      IndentedBlockContexts.AllAcceptingAnything
  //    )
  //
  //    runEnterTestInContexts(
  //      s"""1 $CARET// line comment 1
  //         |2 $CARET// line comment 2""".stripMargin,
  //      s"""1
  //         |$CARET// line comment 1
  //         |2
  //         |$CARET// line comment 2""".stripMargin,
  //      IndentedBlockContexts.AllAcceptingAnything
  //    )
  //
  //    runEnterTestInContexts(
  //      s"""1$CARET   // line comment 1
  //         |2$CARET   // line comment 2""".stripMargin,
  //      s"""1
  //         |$CARET// line comment 1
  //         |2
  //         |$CARET// line comment 2""".stripMargin,
  //      IndentedBlockContexts.AllAcceptingAnything
  //    )
  //  }


  def testAfterLastSemicolon(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1;$CARET
         |""".stripMargin,
      s"""1;
         |$CARET
         |""".stripMargin
    )

    runEnterTestInAllIndentationBlockContexts(
      s"""1 + 1;$CARET
         |""".stripMargin,
      s"""1 + 1;
         |$CARET
         |""".stripMargin
    )

    runEnterTestInAllIndentationBlockContexts(
      s"""1 + 1 +
         |    1 + 1;$CARET
         |""".stripMargin,
      s"""1 + 1 +
         |    1 + 1;
         |$CARET
         |""".stripMargin
    )
  }

  def testAfterLastSemicolon_MultipleSemicolons(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1;;; ;; ;$CARET
         |""".stripMargin,
      s"""1;;; ;; ;
         |$CARET
         |""".stripMargin
    )
  }

  def testAfterLastSemicolon_WithComment(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1; // comment$CARET
         |""".stripMargin,
      s"""1; // comment
         |$CARET
         |""".stripMargin
    )
  }

  def testAfterLastSemicolon_SeveralExpressions(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1; 2; 3;$CARET
         |""".stripMargin,
      s"""1; 2; 3;
         |$CARET
         |""".stripMargin
    )
  }

  def testAfterLastSemicolon_SeveralExpressions_WithComment(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1; 2; 3; // comment$CARET
         |""".stripMargin,
      s"""1; 2; 3; // comment
         |$CARET
         |""".stripMargin
    )

    runEnterTestInAllIndentationBlockContexts(
      s"""1 + 1; 2 + 2; 3 + 3; // comment$CARET
         |""".stripMargin,
      s"""1 + 1; 2 + 2; 3 + 3; // comment
         |$CARET
         |""".stripMargin
    )


    runEnterTestInAllIndentationBlockContexts(
      s"""1 + 1; 2 + 2; 3 + 3; // comment$CARET
         |""".stripMargin,
      s"""1 + 1; 2 + 2; 3 + 3; // comment
         |$CARET
         |""".stripMargin
    )
  }

  def testAfterLastSemicolon_SeveralExpressions_SpacesAroundSemicolons(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1  ;   2  ;   3  ;  $CARET
         |""".stripMargin,
      s"""1  ;   2  ;   3  ;  ${""}
         |$CARET
         |""".stripMargin
    )
  }

  def testAfterLastSemicolon_SeveralExpressions_NoTrailingSemicolon(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1; 2; 3$CARET
         |""".stripMargin,
      s"""1; 2; 3
         |$CARET
         |""".stripMargin
    )

    runEnterTestInAllIndentationBlockContexts(
      s"""1 + 1; 2 + 2; 3 + 3$CARET
         |""".stripMargin,
      s"""1 + 1; 2 + 2; 3 + 3
         |$CARET
         |""".stripMargin
    )
  }

  def testAfterLastSemicolon_SeveralExpressions_MultipleSemicolons(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1 ;; ; 2  ; ;; 3 ; ;; // comment$CARET
         |""".stripMargin,
      s"""1 ;; ; 2  ; ;; 3 ; ;; // comment
         |$CARET
         |""".stripMargin
    )
  }

  def testAfterLastSemicolon_SeveralExpressions_MultipleSemicolons_NoTrailingSemicolon(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1 ;; ; 2  ; ;; 3$CARET
         |""".stripMargin,
      s"""1 ;; ; 2  ; ;; 3
         |$CARET
         |""".stripMargin
    )

    runEnterTestInAllIndentationBlockContexts(
      s"""1 + 1; 2 + 2; 3 + 3$CARET
         |""".stripMargin,
      s"""1 + 1; 2 + 2; 3 + 3
         |$CARET
         |""".stripMargin
    )
  }

  def testAfterMiddleSemicolon_SeveralExpressions(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1; 2; ${CARET}3; 4;
         |""".stripMargin,
      s"""1; 2; ${""}
         |${CARET}3; 4;
         |""".stripMargin
    )
  }

  def testAfterMiddleSemicolon_SeveralExpressions_1(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1; 2;$CARET 3; 4;
         |""".stripMargin,
      s"""1; 2;
         |${CARET}3; 4;
         |""".stripMargin
    )
  }

  def testAfterMiddleSemicolon_SeveralExpressions_MultipleSemicolons(): Unit = {
    runEnterTestInAllIndentationBlockContexts(
      s"""1; ;; ; 2 ;  ;; ; $CARET 3; ;; 4;
         |""".stripMargin,
      s"""1; ;; ; 2 ;  ;; ; ${""}
         |${CARET}3; ;; 4;
         |""".stripMargin
    )
  }

  // This is a wired case, we at least shouldn't fail on it
  def testAfterSemicolons_WithEmptyLine(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""class A:
         |  def foo2 =
         |    ; ;; ;$CARET
         |""".stripMargin,
      s"""class A:
         |  def foo2 =
         |    ; ;; ;
         |    $CARET
         |""".stripMargin
    )

    checkGeneratedTextAfterEnter(
      s"""class A:
         |  def foo2 =
         |    ; ;; ; //comment$CARET
         |""".stripMargin,
      s"""class A:
         |  def foo2 =
         |    ; ;; ; //comment
         |    $CARET
         |""".stripMargin
    )

    checkGeneratedTextAfterEnter(
      s"""class A:
         |  def foo2 =
         |    42
         |    ; ;; ;$CARET
         |""".stripMargin,
      s"""class A:
         |  def foo2 =
         |    42
         |    ; ;; ;
         |    $CARET
         |""".stripMargin
    )
  }

  def testEnterAfterAllPossibleLines(): Unit = {
    val lines =
      """42
        |ref
        |
        |obj.id = ???
        |try 42 finally {}
        |while (2 * 2 == 5) {}
        |if true then {} else {}
        |for { x <- 1 to 3 } do {}
        |1 match { case _ => ??? }
        |
        |val value = ???
        |var variable = ???
        |def function = ???
        |type MyType = String
        |
        |class A
        |trait A
        |object A
        |
        |import a.b.c
        |export a.*
        |
        |extension (x: String) { def foo = ??? }
        |given intOrd: Ord[Int] with {}
        |""".stripMargin.linesIterator.filter(_.nonEmpty).toSeq

    val context1 =
      s"""class A:
         |  $CARET
         |""".stripMargin

    val context2 =
      s"""class A:
         |  println(42)
         |  $CARET
         |""".stripMargin

    lines.foreach { line =>
      val before1 = context1.replace(CARET, s"$line$CARET")
      val after1 = before1.replace(CARET, s"\n  $CARET")
      checkGeneratedTextAfterEnter(before1, after1)

      val before2 = context2.replace(CARET, s"$line$CARET")
      val after2 = before2.replace(CARET, s"\n  $CARET")
      checkGeneratedTextAfterEnter(before2, after2)
    }
  }

  def testBeforeDefinitionInOneLineExtension(): Unit = doEnterTest(
    s"""extension (s: String) ${CARET}def myExt1: String""",

    s"""extension (s: String)
       |  ${CARET}def myExt1: String""".stripMargin,

    s"""extension (s: String)
       |
       |  ${CARET}def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_with_comment(): Unit = doEnterTest(
    s"""extension (s: String) /*my comment*/${CARET}def myExt1: String""",
    s"""extension (s: String) /*my comment*/
       |  ${CARET}def myExt1: String""".stripMargin,
    s"""extension (s: String) /*my comment*/
       |
       |  ${CARET}def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_WithExtraSpaceAfterCaret(): Unit = doEnterTest(
    s"""extension (s: String)$CARET def myExt1: String""",
    s"""extension (s: String)
       |  ${CARET}def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_BeforeSingleModifier(): Unit = doEnterTest(
    s"""extension (s: String) ${CARET}override def myExt1: String""",
    s"""extension (s: String)
       |  ${CARET}override def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_BeforeSingleModifier_WithExtraSpaceAfterCaret(): Unit = doEnterTest(
    s"""extension (s: String)$CARET override def myExt1: String""",
    s"""extension (s: String)
       |  ${CARET}override def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_BeforeMultipleModifiers(): Unit = doEnterTest(
    s"""extension (s: String) ${CARET}final override def myExt1: String""",
    s"""extension (s: String)
       |  ${CARET}final override def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_BeforeMultipleModifiers_WithExtraSpaceAfterCaret(): Unit = doEnterTest(
    s"""extension (s: String)$CARET final override def myExt1: String""",
    s"""extension (s: String)
       |  ${CARET}final override def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_BeforeSingleSoftModifier(): Unit = doEnterTest(
    s"""extension (s: String) ${CARET}inline def myExt1: String""",
    s"""extension (s: String)
       |  ${CARET}inline def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_BeforeMultipleModifiersAndSoftModifiers_1(): Unit = doEnterTest(
    s"""extension (s: String) ${CARET}inline final def myExt1: String""",
    s"""extension (s: String)
       |  ${CARET}inline final def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_BeforeMultipleModifiersAndSoftModifiers_2(): Unit = doEnterTest(
    s"""extension (s: String) ${CARET}final inline def myExt1: String""",
    s"""extension (s: String)
       |  ${CARET}final inline def myExt1: String""".stripMargin
  )

  def testBeforeDefinitionInOneLineExtension_BeforeMultipleModifiers_WithCommentsBetween(): Unit = doEnterTest(
    s"""extension (s: String) ${CARET}final /*strange comment 1*/ inline /*strange comment 2*/ def myExt1: String""",
    s"""extension (s: String)
       |  ${CARET}final /*strange comment 1*/ inline /*strange comment 2*/ def myExt1: String""".stripMargin
  )

  def testAfterBlockCommentInFunction(): Unit = doEnterTest(
    s"""
       |class Foo {
       |  def bar: Int =
       |    /* */$CARET
       |}
       |""".stripMargin,

    s"""
       |class Foo {
       |  def bar: Int =
       |    /* */
       |    $CARET
       |}
       |""".stripMargin
  )

  def testAfterBlockCommentInFunction_2(): Unit = doEnterTest(
    s"""
       |class Foo:
       |  def bar: Int =
       |    /* */$CARET
       |""".stripMargin,

    s"""
       |class Foo:
       |  def bar: Int =
       |    /* */
       |    $CARET
       |""".stripMargin
  )

  def testEnterHandlerShouldWorkEvenWhenCodeStyleSettingIsDisabled(): Unit = {
    val before = getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false

    try {
      doEnterTestWithAndWithoutTabs(
        s"""class A:
           |    def foo =
           |        println("start")
           |        if 2 + 2 == 42 then
           |            println(1)
           |            println(2)
           |      $CARET
           |""".stripMargin,
        s"""class A:
           |    def foo =
           |        println("start")
           |        if 2 + 2 == 42 then
           |            println(1)
           |            println(2)
           |
           |    $CARET
           |""".stripMargin,
      )
    } finally  {
      getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = before
    }
  }

  def testEnterBeforeFirstStatementInPackaging(): Unit = doEnterTest(
    s"""package a:$CARET
       |  val x = 0
       |""".stripMargin,
    s"""package a:
       |  $CARET
       |  val x = 0
       |""".stripMargin
  )

  def testEnterAfterFirstStatementInPackaging(): Unit = doEnterTest(
    s"""package a:
       |  val x = 0$CARET
       |""".stripMargin,
    s"""package a:
       |  val x = 0
       |  $CARET
       |""".stripMargin
  )
}
