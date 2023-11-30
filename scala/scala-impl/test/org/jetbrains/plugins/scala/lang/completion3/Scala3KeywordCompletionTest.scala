package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class Scala3KeywordCompletionTest extends ScalaCompletionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  /// INFIX

  def testInfixTopLevel(): Unit = doCompletionTest(
    fileText = s"in$CARET",
    resultText = s"infix $CARET",
    item = "infix"
  )

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

  def testSoftModifierAfterInfix(): Unit = doCompletionTest(
    fileText = s"infix in$CARET",
    resultText = s"infix inline $CARET",
    item = "inline"
  )

  def testInfixAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private in$CARET",
    resultText = s"private infix $CARET",
    item = "infix"
  )

  def testHardModifierAfterInfix(): Unit = doCompletionTest(
    fileText = s"infix pr$CARET",
    resultText = s"infix private $CARET",
    item = "private"
  )

  def testInfixDef(): Unit = doCompletionTest(
    fileText = s"infix d$CARET",
    resultText = s"infix def $CARET",
    item = "def"
  )

  def testInfixType(): Unit = doCompletionTest(
    fileText = s"infix t$CARET",
    resultText = s"infix type $CARET",
    item = "type"
  )

  /// INLINE

  def testInlineTopLevel(): Unit = doCompletionTest(
    fileText = s"in$CARET",
    resultText = s"inline $CARET",
    item = "inline"
  )

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

  def testSoftModifierAfterInline(): Unit = doCompletionTest(
    fileText = s"inline tr$CARET",
    resultText = s"inline transparent $CARET",
    item = "transparent"
  )

  def testInlineAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private in$CARET",
    resultText = s"private inline $CARET",
    item = "inline"
  )

  def testHardModifierAfterInline(): Unit = doCompletionTest(
    fileText = s"inline pr$CARET",
    resultText = s"inline private $CARET",
    item = "private"
  )

  def testInlineDef(): Unit = doCompletionTest(
    fileText = s"infix d$CARET",
    resultText = s"infix def $CARET",
    item = "def"
  )

  def testInlineVal(): Unit = doCompletionTest(
    fileText = s"inline v$CARET",
    resultText = s"inline val $CARET",
    item = "val"
  )

  def testInlineParamOfInlineDef(): Unit = doCompletionTest(
    fileText = s"inline def foo($CARET)",
    resultText = s"inline def foo(inline $CARET)",
    item = "inline"
  )

  def testNoCompletionInlineParamOfDef(): Unit = checkNoBasicCompletion(
    fileText = s"def foo($CARET)",
    item = "inline"
  )

  def testInlineBodyOfInlineDef(): Unit = doCompletionTest(
    fileText = s"inline def foo() = $CARET",
    resultText = s"inline def foo() = inline $CARET",
    item = "inline"
  )

  def testNoCompletionInlineBodyOfDef(): Unit = checkNoBasicCompletion(
    fileText = s"def foo() = $CARET",
    item = "inline"
  )

  /// OPAQUE

  def testOpaqueTopLevel(): Unit = doCompletionTest(
    fileText = s"op$CARET",
    resultText = s"opaque $CARET",
    item = "opaque"
  )

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

  def testSoftModifierAfterOpaque(): Unit = doCompletionTest(
    fileText = s"opaque in$CARET",
    resultText = s"opaque infix $CARET",
    item = "infix"
  )

  def testOpaqueAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private op$CARET",
    resultText = s"private opaque $CARET",
    item = "opaque"
  )

  def testHardModifierAfterOpaque(): Unit = doCompletionTest(
    fileText = s"opaque pr$CARET",
    resultText = s"opaque private $CARET",
    item = "private"
  )

  def testOpaqueType(): Unit = doCompletionTest(
    fileText = s"opaque t$CARET",
    resultText = s"opaque type $CARET",
    item = "type"
  )

  /// OPEN

  def testOpenTopLevel(): Unit = doCompletionTest(
    fileText = s"op$CARET",
    resultText = s"open $CARET",
    item = "open"
  )

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

  def testSoftModifierAfterOpen(): Unit = doCompletionTest(
    fileText = s"open t$CARET",
    resultText = s"open transparent $CARET",
    item = "transparent"
  )

  def testOpenAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private op$CARET",
    resultText = s"private open $CARET",
    item = "open"
  )

  def testHardModifierAfterOpen(): Unit = doCompletionTest(
    fileText = s"open ab$CARET",
    resultText = s"open abstract $CARET",
    item = "abstract"
  )

  def testOpenClass(): Unit = doCompletionTest(
    fileText = s"open c$CARET",
    resultText = s"open class $CARET",
    item = "class"
  )

  def testOpenObject(): Unit = doCompletionTest(
    fileText = s"open o$CARET",
    resultText = s"open object $CARET",
    item = "object"
  )

  def testOpenTrait(): Unit = doCompletionTest(
    fileText = s"open t$CARET",
    resultText = s"open trait $CARET",
    item = "trait"
  )

  def testOpenCase(): Unit = doCompletionTest(
    fileText = s"open c$CARET",
    resultText = s"open case $CARET",
    item = "case"
  )

  def testOpenCaseClass(): Unit = doCompletionTest(
    fileText = s"open case c$CARET",
    resultText = s"open case class $CARET",
    item = "class"
  )

  /// TRANSPARENT

  def testTransparentTopLevel(): Unit = doCompletionTest(
    fileText = s"tr$CARET",
    resultText = s"transparent $CARET",
    item = "transparent"
  )

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

  def testSoftModifierAfterTransparent(): Unit = doCompletionTest(
    fileText = s"transparent in$CARET",
    resultText = s"transparent inline $CARET",
    item = "inline"
  )

  def testTransparentAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private tr$CARET",
    resultText = s"private transparent $CARET",
    item = "transparent"
  )

  def testHardModifierAfterTransparent(): Unit = doCompletionTest(
    fileText = s"transparent pr$CARET",
    resultText = s"transparent private $CARET",
    item = "private"
  )

  def testTransparentDef(): Unit = doCompletionTest(
    fileText = s"transparent d$CARET",
    resultText = s"transparent def $CARET",
    item = "def"
  )

  def testTransparentTrait(): Unit = doCompletionTest(
    fileText = s"transparent t$CARET",
    resultText = s"transparent trait $CARET",
    item = "trait"
  )

  /// ENUM

  def testEnumTopLevel(): Unit = doCompletionTest(
    fileText = s"en$CARET",
    resultText = s"enum $CARET",
    item = "enum"
  )

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

  def testEnumAfterAccessModifier(): Unit = doCompletionTest(
    fileText = s"private en$CARET",
    resultText = s"private enum $CARET",
    item = "enum"
  )

  def testNoCompletionEnumAfterSoftModifiers(): Unit =
    ScalaKeyword.SOFT_MODIFIERS.foreach { softModifier =>
      checkNoBasicCompletion(
        fileText = s"$softModifier en$CARET",
        item = "enum"
      )
    }

  /// EXTENSION

  def testExtensionTopLevel(): Unit = doCompletionTest(
    fileText = s"ex$CARET",
    resultText = s"extension $CARET",
    item = "extension"
  )

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

  def testNoCompletionSoftModifierAfterExtension(): Unit = checkNoBasicCompletion(
    fileText = s"extension in$CARET",
    item = "inline"
  )

  def testNoCompletionHardModifierAfterExtension(): Unit = checkNoBasicCompletion(
    fileText = s"extension pr$CARET",
    item = "private"
  )

  def testExtensionDefOneLine(): Unit = doCompletionTest(
    fileText = s"extension (i: Int) d$CARET",
    resultText = s"extension (i: Int) def $CARET",
    item = "def"
  )

  def testExtensionDef(): Unit = doCompletionTest(
    fileText =
      s"""extension (i: Int)
         |  d$CARET""".stripMargin,
    resultText =
      s"""extension (i: Int)
         |  def $CARET""".stripMargin,
    item = "def"
  )

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

  def testExtensionDefWithTypeParamAndUsing(): Unit = doCompletionTest(
    fileText =
      s"""extension [T](x: T)(using n: Numeric[T])
         |  d$CARET""".stripMargin,
    resultText =
      s"""extension [T](x: T)(using n: Numeric[T])
         |  def $CARET""".stripMargin,
    item = "def"
  )

  /// DERIVES

  def testDerivesClass(): Unit = doCompletionTest(
    fileText = s"class Test d$CARET",
    resultText = s"class Test derives $CARET",
    item = "derives"
  )

  def testDerivesTrait(): Unit = doCompletionTest(
    fileText = s"trait Test d$CARET",
    resultText = s"trait Test derives $CARET",
    item = "derives"
  )

  def testDerivesCaseClass(): Unit = doCompletionTest(
    fileText = s"case class Test d$CARET",
    resultText = s"case class Test derives $CARET",
    item = "derives"
  )

  def testDerivesObject(): Unit = doCompletionTest(
    fileText = s"object Test d$CARET",
    resultText = s"object Test derives $CARET",
    item = "derives"
  )

  def testDerivesEnum(): Unit = doCompletionTest(
    fileText = s"enum Test d$CARET",
    resultText = s"enum Test derives $CARET",
    item = "derives"
  )

  def testDerivesBeforeSemicolon(): Unit = doCompletionTest(
    fileText = s"class Test d$CARET;",
    resultText = s"class Test derives $CARET;",
    item = "derives"
  )

  def testDerivesBeforeId(): Unit = doCompletionTest(
    fileText = s"class Test d$CARET Show",
    resultText = s"class Test derives ${CARET}Show",
    item = "derives"
  )

  def testDerivesBetweenClasses(): Unit = doCompletionTest(
    fileText =
      s"""class Test d$CARET
         |class Test2""".stripMargin,
    resultText =
      s"""class Test derives $CARET
         |class Test2""".stripMargin,
    item = "derives"
  )

  def testDerivesBeforeBody(): Unit = doCompletionTest(
    fileText =
      s"""class Test d$CARET {
         |}""".stripMargin,
    resultText =
      s"""class Test derives $CARET{
         |}""".stripMargin,
    item = "derives"
  )

  def testDerivesBeforeColon(): Unit = doCompletionTest(
    fileText = s"class Test d$CARET:",
    resultText = s"class Test derives $CARET:",
    item = "derives"
  )

  def testDerivesBeforeObjectBody(): Unit = doCompletionTest(
    fileText =
      s"""object Test d$CARET {
         |}""".stripMargin,
    resultText =
      s"""object Test derives $CARET{
         |}""".stripMargin,
    item = "derives"
  )

  def testNoCompletionDerivesBeforeExtends(): Unit = checkNoBasicCompletion(
    fileText = s"object Obj d$CARET extends",
    item = "derives"
  )

  def testNoCompletionDerivesBeforeDerives(): Unit = checkNoBasicCompletion(
    fileText = s"object Obj d$CARET derives",
    item = "derives"
  )

  /// if - THEN

  def testThen(): Unit = doCompletionTest(
    fileText = s"if 1 == 2 t$CARET",
    resultText = s"if 1 == 2 then $CARET",
    item = "then"
  )

  def testThenAfterParens(): Unit = doCompletionTest(
    fileText = s"if (1 == 2) t$CARET",
    resultText = s"if (1 == 2) then $CARET",
    item = "then"
  )

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

  /// if - then - ELSE

  def testElse(): Unit = doCompletionTest(
    fileText = s"if 1 == 2 then 7 e$CARET",
    resultText = s"if 1 == 2 then 7 else $CARET",
    item = "else"
  )

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

  /// while - DO

  def testDoInWhileLoop(): Unit = doCompletionTest(
    fileText = s"while 1 == 2 d$CARET",
    resultText = s"while 1 == 2 do $CARET",
    item = "do"
  )

  def testDoInWhileLoopAfterParens(): Unit = doCompletionTest(
    fileText = s"while (1 == 2) d$CARET",
    resultText = s"while (1 == 2) do $CARET",
    item = "do"
  )

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

  /// for - DO

  def testDoInForLoop(): Unit = doCompletionTest(
    fileText = s"for x <- 1 to 3 d$CARET",
    resultText = s"for x <- 1 to 3 do $CARET",
    item = "do"
  )

  def testDoInForLoopAfterIf(): Unit = doCompletionTest(
    fileText = s"for x <- 1 to 3 if x % 2 == 0 d$CARET",
    resultText = s"for x <- 1 to 3 if x % 2 == 0 do $CARET",
    item = "do"
  )

  def testDoInForLoopAfterIfAndNewLine(): Unit = doCompletionTest(
    fileText =
      s"""for x <- 1 to 3 if x % 2 == 0
         |d$CARET""".stripMargin,
    resultText =
      s"""for x <- 1 to 3 if x % 2 == 0
         |do $CARET""".stripMargin,
    item = "do"
  )

  def testDoInForLoopAfterParens(): Unit = doCompletionTest(
    fileText = s"for (x <- 1 to 3) d$CARET",
    resultText = s"for (x <- 1 to 3) do $CARET",
    item = "do"
  )

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

  /// for - YIELD

  def testYieldInForLoop(): Unit = doCompletionTest(
    fileText = s"for x <- 1 to 3 y$CARET",
    resultText = s"for x <- 1 to 3 yield $CARET",
    item = "yield"
  )

  def testYieldInForLoopAfterIf(): Unit = doCompletionTest(
    fileText = s"for x <- 1 to 3 if x % 2 == 0 y$CARET",
    resultText = s"for x <- 1 to 3 if x % 2 == 0 yield $CARET",
    item = "yield"
  )

  def testYieldInForLoopAfterIfAndNewLine(): Unit = doCompletionTest(
    fileText =
      s"""for x <- 1 to 3 if x % 2 == 0
         |y$CARET""".stripMargin,
    resultText =
      s"""for x <- 1 to 3 if x % 2 == 0
         |yield $CARET""".stripMargin,
    item = "yield"
  )

  def testYieldInForLoopAfterParens(): Unit = doCompletionTest(
    fileText = s"for (x <- 1 to 3) y$CARET",
    resultText = s"for (x <- 1 to 3) yield $CARET",
    item = "yield"
  )

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

  /// CASE toplevel

  def testCaseTopLevel(): Unit = doCompletionTest(
    fileText =
      s"""c$CARET
         |""".stripMargin,
    resultText =
      s"""case $CARET
         |""".stripMargin,
    item = "case"
  )

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

  /// CASE in "quiet" try-catch

  private val throwingFunctionDefinition =
    s"""import java.io.*
       |
       |@throws[IOException]
       |def boom = throw new InterruptedIOException("boom")
       |""".stripMargin

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

  def testNoCompletionTryCatchNotIndentedBeforeIndentedCaseClause(): Unit = checkNoBasicCompletion(
    fileText =
      s"""$throwingFunctionDefinition
         |
         |try boom catch c$CARET
         |  case e: IOException => println("Got IO exception: " + e.getMessage)
         |""".stripMargin,
    item = "case"
  )

  /// USING

  def testNoCompletionUsingTopLevel(): Unit = checkNoBasicCompletion(
    fileText = s"u$CARET",
    item = "using"
  )

  def testNoCompletionUsingInsideObject(): Unit = checkNoBasicCompletion(
    fileText =
      s"""object O:
         |  u$CARET
         |""".stripMargin,
    item = "using"
  )

  def testUsingParamOfDef(): Unit = doCompletionTest(
    fileText = s"def foo($CARET)",
    resultText = s"def foo(using $CARET)",
    item = "using"
  )

  def testUsingParamOfClass(): Unit = doCompletionTest(
    fileText = s"class Foo($CARET)",
    resultText = s"class Foo(using $CARET)",
    item = "using"
  )

  def testUsingParamOfInlinePrivateDef(): Unit = doCompletionTest(
    fileText = s"inline private def foo($CARET)",
    resultText = s"inline private def foo(using $CARET)",
    item = "using"
  )

  def testUsingParamOfDefBeforeFirstParam(): Unit = doCompletionTest(
    fileText = s"def foo($CARET s: String, i: Int)",
    resultText = s"def foo(using ${CARET}s: String, i: Int)",
    item = "using"
  )

  def testUsingParamOfDefInSecondParamList(): Unit = doCompletionTest(
    fileText = s"def foo(s: String)($CARET)",
    resultText = s"def foo(s: String)(using $CARET)",
    item = "using"
  )

  def testNoCompletionUsingParamOfDefAfterFirstParam(): Unit = checkNoBasicCompletion(
    fileText = s"def foo(s: String, $CARET)",
    item = "using"
  )

  def testUsingArg(): Unit = doCompletionTest(
    fileText = s"foo($CARET)",
    resultText = s"foo(using $CARET)",
    item = "using"
  )

  def testUsingInGiven(): Unit = doCompletionTest(
    fileText = s"given foo(u$CARET)",
    resultText = s"given foo(using $CARET)",
    item = "using"
  )

  def testUsingInAnonymousGiven(): Unit = doCompletionTest(
    fileText = s"given (u$CARET)",
    resultText = s"given (using $CARET)",
    item = "using"
  )

  def testUsingInGenericGiven(): Unit = doCompletionTest(
    fileText = s"given foo[T](u$CARET)",
    resultText = s"given foo[T](using $CARET)",
    item = "using"
  )

  def testUsingInAnonymousGenericGiven(): Unit = doCompletionTest(
    fileText = s"given [T](u$CARET)",
    resultText = s"given [T](using $CARET)",
    item = "using"
  )

  /// GIVEN

  def testGivenTopLevel(): Unit = doCompletionTest(
    fileText = s"g$CARET",
    resultText = s"given $CARET",
    item = "given"
  )

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

  def testGivenAfterHardModifier(): Unit = doCompletionTest(
    fileText = s"private g$CARET",
    resultText = s"private given $CARET",
    item = "given"
  )

  def testGivenAfterSoftModifier(): Unit = doCompletionTest(
    fileText = s"transparent inline g$CARET",
    resultText = s"transparent inline given $CARET",
    item = "given"
  )

  def testNoCompletionHardModifierAfterGiven(): Unit = checkNoBasicCompletion(
    fileText = s"given pr$CARET",
    item = "private"
  )

  /// pattern-bound GIVEN

  def testPatternBoundGivenInForOneLine(): Unit = doCompletionTest(
    fileText = s"for g$CARET",
    resultText = s"for given $CARET",
    item = "given"
  )

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

  /// GIVEN in import

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

  def testGivenInImportWithoutQualifier(): Unit = checkNoBasicCompletion(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import g$CARET
         |""".stripMargin,
    item = "given"
  )

  def testGivenInImportWrongPlace(): Unit = checkNoBasicCompletion(
    fileText =
      s"""object Givens
         |
         |object Test:
         |  import Givens.{* g$CARET}
         |""".stripMargin,
    item = "given"
  )

  /// given - WITH

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

  /// EXPORT

  def testExportTopLevel(): Unit = doCompletionTest(
    fileText = s"ex$CARET",
    resultText = s"export $CARET",
    item = "export"
  )

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

  /// EXTENDS in enum cases

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

  /// members after annotation

  def testDefAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""class Test { @deprecated $CARET}""".stripMargin,
    resultText = s"""class Test { @deprecated def $CARET}""".stripMargin,
    item = "def"
  )

  def testTypeAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""class Test { @deprecated $CARET}""".stripMargin,
    resultText = s"""class Test { @deprecated type $CARET}""".stripMargin,
    item = "type"
  )

  def testClassAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""class Test { @deprecated $CARET}""".stripMargin,
    resultText = s"""class Test { @deprecated class $CARET}""".stripMargin,
    item = "class"
  )

  /// toplevel members

  def testToplevelDef(): Unit = doCompletionTest(
    fileText = s"""$CARET""".stripMargin,
    resultText = s"""def $CARET""".stripMargin,
    item = "def"
  )

  def testToplevelType(): Unit = doCompletionTest(
    fileText = s"""$CARET""".stripMargin,
    resultText = s"""type $CARET""".stripMargin,
    item = "type"
  )

  def testToplevelClass(): Unit = doCompletionTest(
    fileText = s"""$CARET""".stripMargin,
    resultText = s"""class $CARET""".stripMargin,
    item = "class"
  )

  /// toplevel members after annotation

  def testToplevelDefAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""@deprecated $CARET""".stripMargin,
    resultText = s"""@deprecated def $CARET""".stripMargin,
    item = "def"
  )

  def testToplevelTypeAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""@deprecated $CARET""".stripMargin,
    resultText = s"""@deprecated type $CARET""".stripMargin,
    item = "type"
  )

  def testToplevelClassAfterAnnotation(): Unit = doCompletionTest(
    fileText = s"""@deprecated $CARET""".stripMargin,
    resultText = s"""@deprecated class $CARET""".stripMargin,
    item = "class"
  )

  def testToplevelDefAfterFullyQualifiedAnnotation(): Unit = doCompletionTest(
    fileText = s"@scala.annotation.tailrec d$CARET",
    resultText = s"@scala.annotation.tailrec def $CARET",
    item = "def"
  )

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

  /// package

  def testPackage(): Unit = doCompletionTest(
    fileText =
      s"""pa$CARET
         |""".stripMargin,
    resultText =
      s"""package $CARET
         |""".stripMargin,
    item = "package"
  )

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

}
