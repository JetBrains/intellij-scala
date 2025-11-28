package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion, WithIndexingMode}
import org.junit.Test

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class Scala3KeywordCompletionTest extends ScalaCompletionTestBase {

  //region INFIX
  @Test
  def testInfixTopLevel(): Unit = doCompletionTest(
    fileText = s"in$CARET",
    resultText = s"infix $CARET",
    item = "infix"
  )

  @Test
  def testInfixInsideObject(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  in$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  infix $CARET
         |""".stripMargin,
    item = "infix"
  )

  @Test
  def testSoftModifierAfterInfix(): Unit = doCompletionTest(
    fileText = s"infix in$CARET",
    resultText = s"infix inline $CARET",
    item = "inline"
  )

  @Test
  def testInfixAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private in$CARET",
    resultText = s"private infix $CARET",
    item = "infix"
  )

  @Test
  def testHardModifierAfterInfix(): Unit = doCompletionTest(
    fileText = s"infix pr$CARET",
    resultText = s"infix private $CARET",
    item = "private"
  )

  @Test
  def testInfixDef(): Unit = doCompletionTest(
    fileText = s"infix d$CARET",
    resultText = s"infix def $CARET",
    item = "def"
  )

  @Test
  def testInfixType(): Unit = doCompletionTest(
    fileText = s"infix t$CARET",
    resultText = s"infix type $CARET",
    item = "type"
  )
  //endregion

  //region INLINE
  @Test
  def testInlineTopLevel(): Unit = doCompletionTest(
    fileText = s"in$CARET",
    resultText = s"inline $CARET",
    item = "inline"
  )

  @Test
  def testInlineInsideObject(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  in$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  inline $CARET
         |""".stripMargin,
    item = "inline"
  )

  @Test
  def testSoftModifierAfterInline(): Unit = doCompletionTest(
    fileText = s"inline tr$CARET",
    resultText = s"inline transparent $CARET",
    item = "transparent"
  )

  @Test
  def testInlineAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private in$CARET",
    resultText = s"private inline $CARET",
    item = "inline"
  )

  @Test
  def testHardModifierAfterInline(): Unit = doCompletionTest(
    fileText = s"inline pr$CARET",
    resultText = s"inline private $CARET",
    item = "private"
  )

  @Test
  def testInlineDef(): Unit = doCompletionTest(
    fileText = s"infix d$CARET",
    resultText = s"infix def $CARET",
    item = "def"
  )

  @Test
  def testInlineVal(): Unit = doCompletionTest(
    fileText = s"inline v$CARET",
    resultText = s"inline val $CARET",
    item = "val"
  )

  @Test
  def testInlineParamOfInlineDef(): Unit = doCompletionTest(
    fileText = s"inline def foo($CARET)",
    resultText = s"inline def foo(inline $CARET)",
    item = "inline"
  )

  @Test
  def testNoCompletionInlineParamOfDef(): Unit = checkNoBasicCompletion(
    fileText = s"def foo($CARET)",
    item = "inline"
  )

  @Test
  def testInlineBodyOfInlineDef(): Unit = doCompletionTest(
    fileText = s"inline def foo() = $CARET",
    resultText = s"inline def foo() = inline $CARET",
    item = "inline"
  )

  @Test
  def testNoCompletionInlineBodyOfDef(): Unit = checkNoBasicCompletion(
    fileText = s"def foo() = $CARET",
    item = "inline"
  )
  //endregion

  //region OPAQUE
  @Test
  def testOpaqueTopLevel(): Unit = doCompletionTest(
    fileText = s"op$CARET",
    resultText = s"opaque $CARET",
    item = "opaque"
  )

  @Test
  def testOpaqueInsideObject(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  op$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  opaque $CARET
         |""".stripMargin,
    item = "opaque"
  )

  @Test
  def testSoftModifierAfterOpaque(): Unit = doCompletionTest(
    fileText = s"opaque in$CARET",
    resultText = s"opaque infix $CARET",
    item = "infix"
  )

  @Test
  def testOpaqueAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private op$CARET",
    resultText = s"private opaque $CARET",
    item = "opaque"
  )

  @Test
  def testHardModifierAfterOpaque(): Unit = doCompletionTest(
    fileText = s"opaque pr$CARET",
    resultText = s"opaque private $CARET",
    item = "private"
  )

  @Test
  def testOpaqueType(): Unit = doCompletionTest(
    fileText = s"opaque t$CARET",
    resultText = s"opaque type $CARET",
    item = "type"
  )
  //endregion

  //region OPEN
  @Test
  def testOpenTopLevel(): Unit = doCompletionTest(
    fileText = s"op$CARET",
    resultText = s"open $CARET",
    item = "open"
  )

  @Test
  def testOpenInsideObject(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  op$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  open $CARET
         |""".stripMargin,
    item = "open"
  )

  @Test
  def testSoftModifierAfterOpen(): Unit = doCompletionTest(
    fileText = s"open t$CARET",
    resultText = s"open transparent $CARET",
    item = "transparent"
  )

  @Test
  def testOpenAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private op$CARET",
    resultText = s"private open $CARET",
    item = "open"
  )

  @Test
  def testHardModifierAfterOpen(): Unit = doCompletionTest(
    fileText = s"open ab$CARET",
    resultText = s"open abstract $CARET",
    item = "abstract"
  )

  @Test
  def testOpenClass(): Unit = doCompletionTest(
    fileText = s"open c$CARET",
    resultText = s"open class $CARET",
    item = "class"
  )

  @Test
  def testOpenObject(): Unit = doCompletionTest(
    fileText = s"open o$CARET",
    resultText = s"open object $CARET",
    item = "object"
  )

  @Test
  def testOpenTrait(): Unit = doCompletionTest(
    fileText = s"open t$CARET",
    resultText = s"open trait $CARET",
    item = "trait"
  )

  @Test
  def testOpenCase(): Unit = doCompletionTest(
    fileText = s"open c$CARET",
    resultText = s"open case $CARET",
    item = "case"
  )

  @Test
  def testOpenCaseClass(): Unit = doCompletionTest(
    fileText = s"open case c$CARET",
    resultText = s"open case class $CARET",
    item = "class"
  )
  //endregion

  //region TRANSPARENT
  @Test
  def testTransparentTopLevel(): Unit = doCompletionTest(
    fileText = s"tr$CARET",
    resultText = s"transparent $CARET",
    item = "transparent"
  )

  @Test
  def testTransparentInsideObject(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  tr$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  transparent $CARET
         |""".stripMargin,
    item = "transparent"
  )

  @Test
  def testSoftModifierAfterTransparent(): Unit = doCompletionTest(
    fileText = s"transparent in$CARET",
    resultText = s"transparent inline $CARET",
    item = "inline"
  )

  @Test
  def testTransparentAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private tr$CARET",
    resultText = s"private transparent $CARET",
    item = "transparent"
  )

  @Test
  def testHardModifierAfterTransparent(): Unit = doCompletionTest(
    fileText = s"transparent pr$CARET",
    resultText = s"transparent private $CARET",
    item = "private"
  )

  @Test
  def testTransparentDef(): Unit = doCompletionTest(
    fileText = s"transparent d$CARET",
    resultText = s"transparent def $CARET",
    item = "def"
  )

  @Test
  def testTransparentTrait(): Unit = doCompletionTest(
    fileText = s"transparent t$CARET",
    resultText = s"transparent trait $CARET",
    item = "trait"
  )
  //endregion

  //region ENUM
  @Test
  def testEnumTopLevel(): Unit = doCompletionTest(
    fileText = s"en$CARET",
    resultText = s"enum $CARET",
    item = "enum"
  )

  @Test
  def testEnumInsideObject(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  en$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  enum $CARET
         |""".stripMargin,
    item = "enum"
  )

  @Test
  def testEnumAfterAccessModifier(): Unit = doCompletionTest(
    fileText = s"private en$CARET",
    resultText = s"private enum $CARET",
    item = "enum"
  )

  @Test
  def testNoCompletionEnumAfterSoftModifiers(): Unit =
    ScalaKeyword.SOFT_MODIFIERS.foreach { softModifier =>
      checkNoBasicCompletion(
        fileText = s"$softModifier en$CARET",
        item = "enum"
      )
    }
  //endregion

  //region EXTENSION
  @Test
  def testExtensionTopLevel(): Unit = doCompletionTest(
    fileText = s"ex$CARET",
    resultText = s"extension $CARET",
    item = "extension"
  )

  @Test
  def testExtensionInsideObject(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  ex$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  extension $CARET
         |""".stripMargin,
    item = "extension"
  )

  @Test
  def testNoCompletionSoftModifierAfterExtension(): Unit = checkNoBasicCompletion(
    fileText = s"extension in$CARET",
    item = "inline"
  )

  @Test
  def testNoCompletionHardModifierAfterExtension(): Unit = checkNoBasicCompletion(
    fileText = s"extension pr$CARET",
    item = "private"
  )

  @Test
  def testExtensionDefOneLine(): Unit = doCompletionTest(
    fileText = s"extension (i: Int) d$CARET",
    resultText = s"extension (i: Int) def $CARET",
    item = "def"
  )

  @Test
  def testExtensionDef(): Unit = doCompletionTest(
    fileText =
      s"""extension (i: Int)
         |  d$CARET""".stripMargin,
    resultText =
      s"""extension (i: Int)
         |  def $CARET""".stripMargin,
    item = "def"
  )

  @Test
  def testExtensionDef2(): Unit = doCompletionTest(
    fileText =
      s"""extension (i: Int)
         |  def square = i * i
         |  d$CARET""".stripMargin,
    resultText =
      s"""extension (i: Int)
         |  def square = i * i
         |  def $CARET""".stripMargin,
    item = "def"
  )

  @Test
  def testExtensionDef3(): Unit = doCompletionTest(
    fileText =
      s"""extension (i: Int)
         |  d$CARET
         |  def square = i * i""".stripMargin,
    resultText =
      s"""extension (i: Int)
         |  def $CARET
         |  def square = i * i""".stripMargin,
    item = "def"
  )

  @Test
  def testExtensionDefInsideBraces(): Unit = doCompletionTest(
    fileText =
      s"""extension (i: Int) {
         |  d$CARET
         |}""".stripMargin,
    resultText =
      s"""extension (i: Int) {
         |  def $CARET
         |}""".stripMargin,
    item = "def"
  )

  @Test
  def testExtensionDefWithTypeParamAndUsing(): Unit = doCompletionTest(
    fileText =
      s"""extension [T](x: T)(using n: Numeric[T])
         |  d$CARET""".stripMargin,
    resultText =
      s"""extension [T](x: T)(using n: Numeric[T])
         |  def $CARET""".stripMargin,
    item = "def"
  )
  //endregion

  //region DERIVES
  @Test
  def testDerivesClass(): Unit = doCompletionTest(
    fileText = s"class Test d$CARET",
    resultText = s"class Test derives $CARET",
    item = "derives"
  )

  @Test
  def testDerivesTrait(): Unit = doCompletionTest(
    fileText = s"trait Test d$CARET",
    resultText = s"trait Test derives $CARET",
    item = "derives"
  )

  @Test
  def testDerivesCaseClass(): Unit = doCompletionTest(
    fileText = s"case class Test d$CARET",
    resultText = s"case class Test derives $CARET",
    item = "derives"
  )

  @Test
  def testDerivesObject(): Unit = doCompletionTest(
    fileText = s"object Test d$CARET",
    resultText = s"object Test derives $CARET",
    item = "derives"
  )

  @Test
  def testDerivesEnum(): Unit = doCompletionTest(
    fileText = s"enum Test d$CARET",
    resultText = s"enum Test derives $CARET",
    item = "derives"
  )

  @Test
  def testDerivesBeforeSemicolon(): Unit = doCompletionTest(
    fileText = s"class Test d$CARET;",
    resultText = s"class Test derives $CARET;",
    item = "derives"
  )

  @Test
  def testDerivesBeforeId(): Unit = doCompletionTest(
    fileText = s"class Test d$CARET Show",
    resultText = s"class Test derives ${CARET}Show",
    item = "derives"
  )

  @Test
  def testDerivesBetweenClasses(): Unit = doCompletionTest(
    fileText =
      s"""class Test d$CARET
         |class Test2""".stripMargin,
    resultText =
      s"""class Test derives $CARET
         |class Test2""".stripMargin,
    item = "derives"
  )

  @Test
  def testDerivesBeforeBody(): Unit = doCompletionTest(
    fileText =
      s"""class Test d$CARET {
         |}""".stripMargin,
    resultText =
      s"""class Test derives $CARET{
         |}""".stripMargin,
    item = "derives"
  )

  @Test
  def testDerivesBeforeColon(): Unit = doCompletionTest(
    fileText = s"class Test d$CARET:",
    resultText = s"class Test derives $CARET:",
    item = "derives"
  )

  @Test
  def testDerivesBeforeObjectBody(): Unit = doCompletionTest(
    fileText =
      s"""object Test d$CARET {
         |}""".stripMargin,
    resultText =
      s"""object Test derives $CARET{
         |}""".stripMargin,
    item = "derives"
  )

  @Test
  def testNoCompletionDerivesBeforeExtends(): Unit = checkNoBasicCompletion(
    fileText = s"object Obj d$CARET extends",
    item = "derives"
  )

  @Test
  def testNoCompletionDerivesBeforeDerives(): Unit = checkNoBasicCompletion(
    fileText = s"object Obj d$CARET derives",
    item = "derives"
  )
  //endregion

  //region if - THEN
  @Test
  def testThen(): Unit = doCompletionTest(
    fileText = s"if 1 == 2 t$CARET",
    resultText = s"if 1 == 2 then $CARET",
    item = "then"
  )

  @Test
  def testThenAfterParens(): Unit = doCompletionTest(
    fileText = s"if (1 == 2) t$CARET",
    resultText = s"if (1 == 2) then $CARET",
    item = "then"
  )

  @Test
  def testThenAfterElseIf(): Unit = doCompletionTest(
    fileText =
      s"""val x = 0
         |if x < 0 then
         |  "negative"
         |else if x == 0 t$CARET
         |""".stripMargin,
    resultText =
      s"""val x = 0
         |if x < 0 then
         |  "negative"
         |else if x == 0 then $CARET
         |""".stripMargin,
    item = "then"
  )

  @Test
  def testThenAfterBlockCondition(): Unit = doCompletionTest(
    fileText =
      s"""if {
         |    val xs = Seq(1, 2, 3)
         |    val ys = Seq(2, 4, 6)
         |    xs(1) == ys.head
         |  }
         |    t$CARET
         |""".stripMargin,
    resultText =
      s"""if {
         |    val xs = Seq(1, 2, 3)
         |    val ys = Seq(2, 4, 6)
         |    xs(1) == ys.head
         |  }
         |    then $CARET
         |""".stripMargin,
    item = "then"
  )

  @Test
  def testThenAfterBlockConditionInParens(): Unit = doCompletionTest(
    fileText =
      s"""if ({
         |    val xs = Seq(1, 2, 3)
         |    val ys = Seq(2, 4, 6)
         |    xs(1) == ys.head
         |  })
         |    t$CARET
         |""".stripMargin,
    resultText =
      s"""if ({
         |    val xs = Seq(1, 2, 3)
         |    val ys = Seq(2, 4, 6)
         |    xs(1) == ys.head
         |  })
         |    then $CARET
         |""".stripMargin,
    item = "then"
  )

  @Test
  def testThenAfterBlockConditionAndComments(): Unit = doCompletionTest(
    fileText =
      s"""if {
         |    val xs = Seq(1, 2, 3)
         |    val ys = Seq(2, 4, 6)
         |    xs(1) == ys.head
         |  }
         |    // some comment
         |    /* another
         |       comment */
         |    t$CARET
         |""".stripMargin,
    resultText =
      s"""if {
         |    val xs = Seq(1, 2, 3)
         |    val ys = Seq(2, 4, 6)
         |    xs(1) == ys.head
         |  }
         |    // some comment
         |    /* another
         |       comment */
         |    then $CARET
         |""".stripMargin,
    item = "then"
  )
  //endregion

  //region if - then - ELSE
  @Test
  def testElse(): Unit = doCompletionTest(
    fileText = s"if 1 == 2 then 7 e$CARET",
    resultText = s"if 1 == 2 then 7 else $CARET",
    item = "else"
  )

  @Test
  def testElseMultiline(): Unit = doCompletionTest(
    fileText =
      s"""object Wrapper {
         |val x = 0
         |if x < 0 then
         |  "negative"
         |e$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""object Wrapper {
         |val x = 0
         |if x < 0 then
         |  "negative"
         |else $CARET
         |}
         |""".stripMargin,
    item = "else"
  )

  @Test
  def testElseMultiline2(): Unit = doCompletionTest(
    fileText =
      s"""object Wrapper {
         |val x = 0
         |if x < 0 then
         |  "negative"
         |else if x == 0 then
         |  "zero"
         |e$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""object Wrapper {
         |val x = 0
         |if x < 0 then
         |  "negative"
         |else if x == 0 then
         |  "zero"
         |else $CARET
         |}
         |""".stripMargin,
    item = "else"
  )
  //endregion

  //region while - DO
  @Test
  def testDoInWhileLoop(): Unit = doCompletionTest(
    fileText = s"while 1 == 2 d$CARET",
    resultText = s"while 1 == 2 do $CARET",
    item = "do"
  )

  @Test
  def testDoInWhileLoopAfterParens(): Unit = doCompletionTest(
    fileText = s"while (1 == 2) d$CARET",
    resultText = s"while (1 == 2) do $CARET",
    item = "do"
  )

  @Test
  def testDoInWhileLoopAfterBlockCondition(): Unit = doCompletionTest(
    fileText =
      s"""var x = 5
         |while {
         |  x -= 2
         |  x > 0
         |} d$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 5
         |while {
         |  x -= 2
         |  x > 0
         |} do $CARET
         |""".stripMargin,
    item = "do"
  )

  @Test
  def testDoInWhileLoopAfterBlockConditionInParens(): Unit = doCompletionTest(
    fileText =
      s"""var x = 5
         |while ({
         |  x -= 2
         |  x > 0
         |}) d$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 5
         |while ({
         |  x -= 2
         |  x > 0
         |}) do $CARET
         |""".stripMargin,
    item = "do"
  )

  @Test
  def testDoInWhileLoopAfterBlockConditionAndComments(): Unit = doCompletionTest(
    fileText =
      s"""var x = 5
         |while {
         |  x -= 2
         |  x > 0
         |}
         |  // some comment
         |  /* another
         |     comment */
         |  d$CARET
         |""".stripMargin,
    resultText =
      s"""var x = 5
         |while {
         |  x -= 2
         |  x > 0
         |}
         |  // some comment
         |  /* another
         |     comment */
         |  do $CARET
         |""".stripMargin,
    item = "do"
  )
  //endregion

  //region for - DO
  @Test
  def testDoInForLoop(): Unit = doCompletionTest(
    fileText = s"for x <- 1 to 3 d$CARET",
    resultText = s"for x <- 1 to 3 do $CARET",
    item = "do"
  )

  @Test
  def testDoInForLoopAfterIf(): Unit = doCompletionTest(
    fileText = s"for x <- 1 to 3 if x % 2 == 0 d$CARET",
    resultText = s"for x <- 1 to 3 if x % 2 == 0 do $CARET",
    item = "do"
  )

  @Test
  def testDoInForLoopAfterIfAndNewLine(): Unit = doCompletionTest(
    fileText =
      s"""for x <- 1 to 3 if x % 2 == 0
         |d$CARET""".stripMargin,
    resultText =
      s"""for x <- 1 to 3 if x % 2 == 0
         |do $CARET""".stripMargin,
    item = "do"
  )

  @Test
  def testDoInForLoopAfterParens(): Unit = doCompletionTest(
    fileText = s"for (x <- 1 to 3) d$CARET",
    resultText = s"for (x <- 1 to 3) do $CARET",
    item = "do"
  )

  @Test
  def testDoInForLoopMultiline(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 3
         |  y <- 2 to 3
         |d$CARET
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 3
         |  y <- 2 to 3
         |do $CARET
         |""".stripMargin,
    item = "do"
  )

  @Test
  def testDoInForLoopAfterBlock(): Unit = doCompletionTest(
    fileText =
      s"""for {
         |  x <- 1 to 3
         |  y <- 2 to 3
         |} d$CARET
         |""".stripMargin,
    resultText =
      s"""for {
         |  x <- 1 to 3
         |  y <- 2 to 3
         |} do $CARET
         |""".stripMargin,
    item = "do"
  )

  @Test
  def testDoInForLoopMultilineAfterComments(): Unit = doCompletionTest(
    fileText =
      s"""for {
         |  x <- 1 to 3
         |  y <- 2 to 3
         |}
         |  // some comment
         |  /* another
         |     comment */
         |  d$CARET
         |""".stripMargin,
    resultText =
      s"""for {
         |  x <- 1 to 3
         |  y <- 2 to 3
         |}
         |  // some comment
         |  /* another
         |     comment */
         |  do $CARET
         |""".stripMargin,
    item = "do"
  )
  //endregion

  //region for - YIELD
  @Test
  def testYieldInForLoop(): Unit = doCompletionTest(
    fileText = s"for x <- 1 to 3 y$CARET",
    resultText = s"for x <- 1 to 3 yield $CARET",
    item = "yield"
  )

  @Test
  def testYieldInForLoopAfterIf(): Unit = doCompletionTest(
    fileText = s"for x <- 1 to 3 if x % 2 == 0 y$CARET",
    resultText = s"for x <- 1 to 3 if x % 2 == 0 yield $CARET",
    item = "yield"
  )

  @Test
  def testYieldInForLoopAfterIfAndNewLine(): Unit = doCompletionTest(
    fileText =
      s"""for x <- 1 to 3 if x % 2 == 0
         |y$CARET""".stripMargin,
    resultText =
      s"""for x <- 1 to 3 if x % 2 == 0
         |yield $CARET""".stripMargin,
    item = "yield"
  )

  @Test
  def testYieldInForLoopAfterParens(): Unit = doCompletionTest(
    fileText = s"for (x <- 1 to 3) y$CARET",
    resultText = s"for (x <- 1 to 3) yield $CARET",
    item = "yield"
  )

  @Test
  def testYieldInForLoopMultiline(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 3
         |  y <- 2 to 3
         |y$CARET
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 3
         |  y <- 2 to 3
         |yield $CARET
         |""".stripMargin,
    item = "yield"
  )

  @Test
  def testYieldInForLoopAfterBlock(): Unit = doCompletionTest(
    fileText =
      s"""for {
         |  x <- 1 to 3
         |  y <- 2 to 3
         |} y$CARET
         |""".stripMargin,
    resultText =
      s"""for {
         |  x <- 1 to 3
         |  y <- 2 to 3
         |} yield $CARET
         |""".stripMargin,
    item = "yield"
  )

  @Test
  def testYieldInForLoopMultilineAfterComments(): Unit = doCompletionTest(
    fileText =
      s"""for {
         |  x <- 1 to 3
         |  y <- 2 to 3
         |}
         |  // some comment
         |  /* another
         |     comment */
         |  y$CARET
         |""".stripMargin,
    resultText =
      s"""for {
         |  x <- 1 to 3
         |  y <- 2 to 3
         |}
         |  // some comment
         |  /* another
         |     comment */
         |  yield $CARET
         |""".stripMargin,
    item = "yield"
  )
  //endregion

  //region CASE toplevel
  @Test
  def testCaseTopLevel(): Unit = doCompletionTest(
    fileText =
      s"""c$CARET
         |""".stripMargin,
    resultText =
      s"""case $CARET
         |""".stripMargin,
    item = "case"
  )

  @Test
  def testCaseTopLevelWithPackage(): Unit = doCompletionTest(
    fileText =
      s"""package com.example
         |
         |c$CARET
         |""".stripMargin,
    resultText =
      s"""package com.example
         |
         |case $CARET
         |""".stripMargin,
    item = "case"
  )
  //endregion

  //region CASE in "quiet" try-catch
  private val throwingFunctionDefinition =
    s"""import java.io.*
       |
       |@throws[IOException]
       |def boom = throw new InterruptedIOException("boom")
       |""".stripMargin

  @Test
  def testTryCatch(): Unit = doCompletionTest(
    fileText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch c$CARET
         |""".stripMargin,
    resultText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch case $CARET
         |""".stripMargin,
    item = "case"
  )

  @Test
  def testTryCatchIndented(): Unit = doCompletionTest(
    fileText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch
         |  c$CARET
         |""".stripMargin,
    resultText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch
         |  case $CARET
         |""".stripMargin,
    item = "case"
  )

  @Test
  def testTryCatchIndentedBeforeCaseClause(): Unit = doCompletionTest(
    fileText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch
         |  c$CARET
         |  case e: IOException => println("Got IO exception: " + e.getMessage)
         |""".stripMargin,
    resultText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch
         |  case $CARET
         |  case e: IOException => println("Got IO exception: " + e.getMessage)
         |""".stripMargin,
    item = "case"
  )

  @Test
  def testTryCatchIndentedAfterCaseClause(): Unit = doCompletionTest(
    fileText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch
         |  case e: InterruptedIOException => println("Got interrupted IO exception: " + e.getMessage)
         |  c$CARET
         |""".stripMargin,
    resultText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch
         |  case e: InterruptedIOException => println("Got interrupted IO exception: " + e.getMessage)
         |  case $CARET
         |""".stripMargin,
    item = "case"
  )

  @Test
  def testTryCatchIndentedBetweenCaseClauses(): Unit = doCompletionTest(
    fileText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch
         |  case e: InterruptedIOException => println("Got interrupted IO exception: " + e.getMessage)
         |  c$CARET
         |  case e: Exception => println("Got exception: " + e.getMessage)
         |""".stripMargin,
    resultText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch
         |  case e: InterruptedIOException => println("Got interrupted IO exception: " + e.getMessage)
         |  case $CARET
         |  case e: Exception => println("Got exception: " + e.getMessage)
         |""".stripMargin,
    item = "case"
  )

  @Test
  def testNoCompletionTryCatchNotIndentedBeforeIndentedCaseClause(): Unit = checkNoBasicCompletion(
    fileText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch c$CARET
         |  case e: IOException => println("Got IO exception: " + e.getMessage)
         |""".stripMargin,
    item = "case"
  )
  //endregion

  //region USING
  @Test
  def testNoCompletionUsingTopLevel(): Unit = checkNoBasicCompletion(
    fileText = s"u$CARET",
    item = "using"
  )

  @Test
  def testNoCompletionUsingInsideObject(): Unit = checkNoBasicCompletion(
    fileText =
      s"""object O:
         |  u$CARET
         |""".stripMargin,
    item = "using"
  )

  @Test
  def testUsingParamOfDef(): Unit = doCompletionTest(
    fileText = s"def foo($CARET)",
    resultText = s"def foo(using $CARET)",
    item = "using"
  )

  @Test
  def testUsingParamOfClass(): Unit = doCompletionTest(
    fileText = s"class Foo($CARET)",
    resultText = s"class Foo(using $CARET)",
    item = "using"
  )

  @Test
  def testUsingParamOfInlinePrivateDef(): Unit = doCompletionTest(
    fileText = s"inline private def foo($CARET)",
    resultText = s"inline private def foo(using $CARET)",
    item = "using"
  )

  @Test
  def testUsingParamOfDefBeforeFirstParam(): Unit = doCompletionTest(
    fileText = s"def foo($CARET s: String, i: Int)",
    resultText = s"def foo(using ${CARET}s: String, i: Int)",
    item = "using"
  )

  @Test
  def testUsingParamOfDefInSecondParamList(): Unit = doCompletionTest(
    fileText = s"def foo(s: String)($CARET)",
    resultText = s"def foo(s: String)(using $CARET)",
    item = "using"
  )

  @Test
  def testNoCompletionUsingParamOfDefAfterFirstParam(): Unit = checkNoBasicCompletion(
    fileText = s"def foo(s: String, $CARET)",
    item = "using"
  )

  @Test
  def testUsingArg(): Unit = doCompletionTest(
    fileText = s"foo($CARET)",
    resultText = s"foo(using $CARET)",
    item = "using"
  )

  @Test
  def testUsingInGiven(): Unit = doCompletionTest(
    fileText = s"given foo(u$CARET)",
    resultText = s"given foo(using $CARET)",
    item = "using"
  )

  @Test
  def testUsingInAnonymousGiven(): Unit = doCompletionTest(
    fileText = s"given (u$CARET)",
    resultText = s"given (using $CARET)",
    item = "using"
  )

  @Test
  def testUsingInGenericGiven(): Unit = doCompletionTest(
    fileText = s"given foo[T](u$CARET)",
    resultText = s"given foo[T](using $CARET)",
    item = "using"
  )

  @Test
  def testUsingInAnonymousGenericGiven(): Unit = doCompletionTest(
    fileText = s"given [T](u$CARET)",
    resultText = s"given [T](using $CARET)",
    item = "using"
  )
  //endregion

  //region GIVEN
  @Test
  def testGivenTopLevel(): Unit = doCompletionTest(
    fileText = s"g$CARET",
    resultText = s"given $CARET",
    item = "given"
  )

  @Test
  def testGivenInsideObject(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  g$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testGivenAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private g$CARET",
    resultText = s"private given $CARET",
    item = "given"
  )

  @Test
  def testGivenAfterSoftModifier(): Unit = doCompletionTest(
    fileText = s"transparent inline g$CARET",
    resultText = s"transparent inline given $CARET",
    item = "given"
  )

  @Test
  def testNoCompletionHardModifierAfterGiven(): Unit = checkNoBasicCompletion(
    fileText = s"given pr$CARET",
    item = "private"
  )
  //endregion

  //region pattern-bound GIVEN
  @Test
  def testPatternBoundGivenInForOneLine(): Unit = doCompletionTest(
    fileText = s"for g$CARET",
    resultText = s"for given $CARET",
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForBeforeAnotherStatement(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  g$CARET
         |
         |println()""".stripMargin,
    resultText =
      s"""for
         |  given $CARET
         |
         |println()""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForMultiline(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  g$CARET
         |""".stripMargin,
    resultText =
      s"""for
         |  given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForAfterGenerator(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 2
         |  g$CARET
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 2
         |  given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForBetweenGeneratorAndBinding(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 2
         |  g$CARET
         |  y = 5
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 2
         |  given $CARET
         |  y = 5
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForBetweenGenerators(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 2
         |  g$CARET
         |  y <- 5 to 7
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 2
         |  given $CARET
         |  y <- 5 to 7
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForBeforeBinding(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  g$CARET
         |  y = 5
         |""".stripMargin,
    resultText =
      s"""for
         |  given $CARET
         |  y = 5
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForBeforeGenerator(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  g$CARET
         |  y <- 5 to 7
         |""".stripMargin,
    resultText =
      s"""for
         |  given $CARET
         |  y <- 5 to 7
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForInSimpleNamedPattern(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  foo @ g$CARET
         |""".stripMargin,
    resultText =
      s"""for
         |  foo @ given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForInSimpleNamedPatternAfterGenerator(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 5
         |  foo @ g$CARET
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 5
         |  foo @ given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForInSimpleNamedPatternAfterGeneratorBeforeAnotherStatement(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 5
         |  foo @ g$CARET
         |
         |println()
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 5
         |  foo @ given $CARET
         |
         |println()
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForInSimpleNamedPatternBeforeGenerator(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  foo @ g$CARET
         |  x <- 1 to 5
         |""".stripMargin,
    resultText =
      s"""for
         |  foo @ given $CARET
         |  x <- 1 to 5
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForInSimpleNamedPatternBeforeBinding(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  foo @ g$CARET
         |  x = 5
         |""".stripMargin,
    resultText =
      s"""for
         |  foo @ given $CARET
         |  x = 5
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForInSimpleNamedPatternBetweenGenerators(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 5
         |  foo @ g$CARET
         |  y <- 1 to 5
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 5
         |  foo @ given $CARET
         |  y <- 1 to 5
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForInSimpleNamedPatternBetweenGeneratorAndBinding(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 5
         |  foo @ g$CARET
         |  y = 5
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 5
         |  foo @ given $CARET
         |  y = 5
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForTuple(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  (42, g$CARET
         |""".stripMargin,
    resultText =
      s"""for
         |  (42, given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForTupleAfterGenerator(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 5
         |  (42, g$CARET
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 5
         |  (42, given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForTupleBeforeGenerator(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  (42, g$CARET
         |  x <- 1 to 5
         |""".stripMargin,
    resultText =
      s"""for
         |  (42, given $CARET
         |  x <- 1 to 5
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForTupleBeforeBinding(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  (42, g$CARET
         |  x = 5
         |""".stripMargin,
    resultText =
      s"""for
         |  (42, given $CARET
         |  x = 5
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForNamedPatternInTupleAfterGenerator(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 5
         |  (42, foo @ g$CARET
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 5
         |  (42, foo @ given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInForNamedPatternInTupleAfterGeneratorBeforeAnotherStatement(): Unit = doCompletionTest(
    fileText =
      s"""for
         |  x <- 1 to 5
         |  (42, foo @ g$CARET
         |
         |println()
         |""".stripMargin,
    resultText =
      s"""for
         |  x <- 1 to 5
         |  (42, foo @ given $CARET
         |
         |println()
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInMatch(): Unit = doCompletionTest(
    fileText =
      s""""foo" match
         |  case g$CARET
         |""".stripMargin,
    resultText =
      s""""foo" match
         |  case given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInMatchWithBraces(): Unit = doCompletionTest(
    fileText =
      s""""foo" match {
         |  case g$CARET
         |}
         |""".stripMargin,
    resultText =
      s""""foo" match {
         |  case given $CARET
         |}
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInMatchSimpleNamedPattern(): Unit = doCompletionTest(
    fileText =
      s""""foo" match
         |  case foo @ g$CARET
         |""".stripMargin,
    resultText =
      s""""foo" match
         |  case foo @ given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInMatchInTuple(): Unit = doCompletionTest(
    fileText =
      s"""(7, "foo") match
         |  case (_, g$CARET
         |""".stripMargin,
    resultText =
      s"""(7, "foo") match
         |  case (_, given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInMatchNamedPatternInTuple(): Unit = doCompletionTest(
    fileText =
      s"""(7, "foo") match
         |  case (_, foo @ g$CARET
         |""".stripMargin,
    resultText =
      s"""(7, "foo") match
         |  case (_, foo @ given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInMap(): Unit = doCompletionTest(
    fileText =
      s"""(1 to 5) map {
         |  case g$CARET
         |}""".stripMargin,
    resultText =
      s"""(1 to 5) map {
         |  case given $CARET
         |}""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInMapSimpleNamedPattern(): Unit = doCompletionTest(
    fileText =
      s"""(1 to 5) map {
         |  case foo @ g$CARET
         |}""".stripMargin,
    resultText =
      s"""(1 to 5) map {
         |  case foo @ given $CARET
         |}""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInMapInTuple(): Unit = doCompletionTest(
    fileText =
      s"""List((1, "foo"), (2, "bar")) map {
         |  case (i, g$CARET
         |}""".stripMargin,
    resultText =
      s"""List((1, "foo"), (2, "bar")) map {
         |  case (i, given $CARET
         |}""".stripMargin,
    item = "given"
  )

  @Test
  def testPatternBoundGivenInMapNamedPatternInTuple(): Unit = doCompletionTest(
    fileText =
      s"""List((1, "foo"), (2, "bar")) map {
         |  case (i, str @ g$CARET
         |}""".stripMargin,
    resultText =
      s"""List((1, "foo"), (2, "bar")) map {
         |  case (i, str @ given $CARET
         |}""".stripMargin,
    item = "given"
  )
  //endregion

  //region GIVEN in import
  @Test
  def testGivenInSimpleImport(): Unit = doCompletionTest(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.g$CARET
         |""".stripMargin,
    resultText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testGivenInImportWithSelectors(): Unit = doCompletionTest(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{g$CARET}
         |""".stripMargin,
    resultText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{given $CARET}
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testGivenInImportWithSelectorsWithoutClosingBrace(): Unit = doCompletionTest(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{g$CARET
         |""".stripMargin,
    resultText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{given $CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testGivenInImportWithSelectorsAfterComma(): Unit = doCompletionTest(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{*, g$CARET}
         |""".stripMargin,
    resultText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{*, given $CARET}
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testGivenInImportWithSelectorsBeforeComma(): Unit = doCompletionTest(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{g$CARET, *}
         |""".stripMargin,
    resultText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{given $CARET, *}
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testGivenInImportAlreadyHavingGivenSelector(): Unit = doCompletionTest(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{given, g$CARET}
         |""".stripMargin,
    resultText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{given, given $CARET}
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testGivenInImportAlreadyHavingGivenSelector2(): Unit = doCompletionTest(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{given Int, g$CARET}
         |""".stripMargin,
    resultText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{given Int, given $CARET}
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testGivenTypeInSimpleImport(): Unit = doCompletionTest(
    fileText =
      s"""object Givens { given Int = 1 }
         |
         |object Test:
         |  import Givens.given I$CARET
         |""".stripMargin,
    resultText =
      s"""object Givens { given Int = 1 }
         |
         |object Test:
         |  import Givens.given Int$CARET
         |""".stripMargin,
    item = "Int"
  )

  @Test
  def testGivenTypeInImportSelector(): Unit = doCompletionTest(
    fileText =
      s"""object Givens { given Int = 1 }
         |
         |object Test:
         |  import Givens.{given I$CARET}
         |""".stripMargin,
    resultText =
      s"""object Givens { given Int = 1 }
         |
         |object Test:
         |  import Givens.{given Int$CARET}
         |""".stripMargin,
    item = "Int"
  )

  @Test
  def testGivenInImportWithoutQualifier(): Unit = checkNoBasicCompletion(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import g$CARET
         |""".stripMargin,
    item = "given"
  )

  @Test
  def testGivenInImportWrongPlace(): Unit = checkNoBasicCompletion(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{* g$CARET}
         |""".stripMargin,
    item = "given"
  )
  //endregion

  //region given - WITH
  @Test
  def testWithOnGiven(): Unit = doCompletionTest(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |
         |given intOrd: Ord[Int] w$CARET
         |""".stripMargin,
    resultText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |
         |given intOrd: Ord[Int] with $CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithOnAnonymousGiven(): Unit = doCompletionTest(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |
         |given Ord[Int] w$CARET
         |""".stripMargin,
    resultText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |
         |given Ord[Int] with $CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithOnGenericAnonymousGivenWithDependencies(): Unit = doCompletionTest(
    fileText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |
         |given [T](using Ord[T]): Ord[List[T]] w$CARET
         |""".stripMargin,
    resultText =
      s"""trait Ord[T]:
         |  def compare(x: T, y: T): Int
         |
         |given [T](using Ord[T]): Ord[List[T]] with $CARET
         |""".stripMargin,
    item = "with"
  )
  //endregion

  //region EXPORT
  @Test
  def testExportTopLevel(): Unit = doCompletionTest(
    fileText = s"ex$CARET",
    resultText = s"export $CARET",
    item = "export"
  )

  @Test
  def testExportInsideObject(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  ex$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  export $CARET
         |""".stripMargin,
    item = "export"
  )
  //endregion

  //region EXTENDS in enum cases
  @Test
  def testExtendsAfterEnumCase(): Unit = doCompletionTest(
    fileText =
      s"""enum Color(val rgb: Int):
         |  case Red ex$CARET
         |""".stripMargin,
    resultText =
      s"""enum Color(val rgb: Int):
         |  case Red extends $CARET
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsAfterEnumCaseWithConstructor(): Unit = doCompletionTest(
    fileText =
      s"""enum Tree[T]:
         |  case True extends Tree[Boolean]
         |  case False extends Tree[Boolean]
         |  case Zero extends Tree[Int]
         |  case Succ(n: Tree[Int]) extends Tree[Int]
         |  case Pred(n: Tree[Int]) ex$CARET
         |  case IsZero(n: Tree[Int]) extends Tree[Boolean]
         |  case If[X](cond: Tree[Boolean], thenp: Tree[X], elsep: Tree[X]) extends Tree[X]
         |""".stripMargin,
    resultText =
      s"""enum Tree[T]:
         |  case True extends Tree[Boolean]
         |  case False extends Tree[Boolean]
         |  case Zero extends Tree[Int]
         |  case Succ(n: Tree[Int]) extends Tree[Int]
         |  case Pred(n: Tree[Int]) extends $CARET
         |  case IsZero(n: Tree[Int]) extends Tree[Boolean]
         |  case If[X](cond: Tree[Boolean], thenp: Tree[X], elsep: Tree[X]) extends Tree[X]
         |""".stripMargin,
    item = "extends"
  )
  //endregion

  //region members after annotation
  @Test
  def testDefAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""class Test { @deprecated $CARET}""".stripMargin,
    resultText = s"""class Test { @deprecated def $CARET}""".stripMargin,
    item = "def"
  )

  @Test
  def testTypeAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""class Test { @deprecated $CARET}""".stripMargin,
    resultText = s"""class Test { @deprecated type $CARET}""".stripMargin,
    item = "type"
  )

  @Test
  def testClassAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""class Test { @deprecated $CARET}""".stripMargin,
    resultText = s"""class Test { @deprecated class $CARET}""".stripMargin,
    item = "class"
  )
  //endregion

  //region toplevel members
  @Test
  def testToplevelDef(): Unit = doCompletionTest(
    fileText = s"""$CARET""".stripMargin,
    resultText = s"""def $CARET""".stripMargin,
    item = "def"
  )

  @Test
  def testToplevelType(): Unit = doCompletionTest(
    fileText = s"""$CARET""".stripMargin,
    resultText = s"""type $CARET""".stripMargin,
    item = "type"
  )

  @Test
  def testToplevelClass(): Unit = doCompletionTest(
    fileText = s"""$CARET""".stripMargin,
    resultText = s"""class $CARET""".stripMargin,
    item = "class"
  )
  //endregion

  //region toplevel members after annotation
  @Test
  def testToplevelDefAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""@deprecated $CARET""".stripMargin,
    resultText = s"""@deprecated def $CARET""".stripMargin,
    item = "def"
  )

  @Test
  def testToplevelTypeAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""@deprecated $CARET""".stripMargin,
    resultText = s"""@deprecated type $CARET""".stripMargin,
    item = "type"
  )

  @Test
  def testToplevelClassAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""@deprecated $CARET""".stripMargin,
    resultText = s"""@deprecated class $CARET""".stripMargin,
    item = "class"
  )

  @Test
  def testToplevelDefAfterFullyQualifiedAnnotation(): Unit = doCompletionTest(
    fileText = s"@scala.annotation.tailrec d$CARET",
    resultText = s"@scala.annotation.tailrec def $CARET",
    item = "def"
  )

  @Test
  def testToplevelDefAfterAnnotationWithPackage(): Unit = doCompletionTest(
    fileText =
      s"""package org.example
         |
         |@deprecated d$CARET""".stripMargin,
    resultText =
      s"""package org.example
         |
         |@deprecated def $CARET""".stripMargin,
    item = "def"
  )
  //endregion

  //region package
  @Test
  def testPackage(): Unit = doCompletionTest(
    fileText =
      s"""pa$CARET
         |""".stripMargin,
    resultText =
      s"""package $CARET
         |""".stripMargin,
    item = "package"
  )

  @Test
  def testPackage2(): Unit = doCompletionTest(
    fileText =
      s"""package one
         |
         |pac$CARET
         |""".stripMargin,
    resultText =
      s"""package one
         |
         |package $CARET
         |""".stripMargin,
    item = "package"
  )

  @Test
  def testPackage3(): Unit = doCompletionTest(
    fileText =
      s"""package one
         |
         |package two:
         |  p$CARET
         |""".stripMargin,
    resultText =
      s"""package one
         |
         |package two:
         |  package $CARET
         |""".stripMargin,
    item = "package"
  )
  //endregion

}
