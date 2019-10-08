package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/** @see [[org.jetbrains.plugins.scala.lang.actions.editor.ClosingBraceInsertTest]]*/
class ClosingBraceRemoveTest extends ScalaBackspaceHandlerBaseTest {

  def testRemove_FunctionBody_SingleExpression(): Unit = {
    val before =
      s"""def foo() = {$CARET
         |  someMethod()
         |}
      """.stripMargin
    val after =
      s"""def foo() = $CARET
         |  someMethod()
      """.stripMargin
    doTest(before, after)
  }

  def testRemove_FunctionBody_SingleExpression_1(): Unit = {
    val before =
      s"""def foo() = {${CARET}someMethod()}
      """.stripMargin
    val after =
      s"""def foo() = ${CARET}someMethod()
      """.stripMargin
    doTest(before, after)
  }

  def testRemove_ValInitializer_SingleExpression(): Unit = {
    val before =
      s"""val x = {$CARET
         |  someMethod()
         |}
      """.stripMargin
    val after =
      s"""val x = $CARET
         |  someMethod()
      """.stripMargin
    doTest(before, after)
  }

  def testRemove_VarInitializer_SingleExpression(): Unit = {
    val before =
      s"""var x = {$CARET
         |  someMethod()
         |}
      """.stripMargin
    val after =
      s"""var x = $CARET
         |  someMethod()
      """.stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_MultipleExpressions(): Unit = {
    val before =
      s"""def foo() = {$CARET
         |  someMethod1()
         |  someMethod2()
         |}
      """.stripMargin
    val after =
      s"""def foo() = $CARET
         |  someMethod1()
         |  someMethod2()
         |}
      """.stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_MultipleExpressionsAndStatements(): Unit = {
    val before =
      s"""def foo() = {$CARET
         |  val x = 42
         |  someMethod()
         |}
      """.stripMargin
    val after =
      s"""def foo() = $CARET
         |  val x = 42
         |  someMethod()
         |}
      """.stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_MultipleExpressionsAndStatements_1(): Unit = {
    val before =
      s"""def foo() = {$CARET
         |  try {
         |    someMethod1()
         |  }
         |  someMethod2()
         |}
      """.stripMargin
    val after =
      s"""def foo() = $CARET
         |  try {
         |    someMethod1()
         |  }
         |  someMethod2()
         |}
      """.stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_NotIndented(): Unit = {
    // here there is an error - C closing brace is considered as foo closing brace, we do not want to remove it
    val before =
      s"""class C {
         |  def foo() = {$CARET
         |    someMethod2()
         |}""".stripMargin
    val after =
      s"""class C {
         |  def foo() = $CARET
         |    someMethod2()
         |}""".stripMargin
    doTest(before, after)
  }

  def testRemove_If_Then_FollowedAfterAnotherIfStatement(): Unit = {
    val before =
      s"""val x =
         |  if (false) 1 else 0
         |val y = if (false) {$CARET
         |  0
         |}
         |""".stripMargin
    val after =
      s"""val x =
         |  if (false) 1 else 0
         |val y = if (false) $CARET
         |  0
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_If_Then_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  if (true) {$CARET
         |    someMethod1()
         |    someMethod2()
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  if (true) $CARET
         |    someMethod1()
         |    someMethod2()
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testRemove_IfElse_Then_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  if (true) {$CARET
         |    someMethod()
         |  }    else {
         |    42
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  if (true) $CARET
         |    someMethod()
         |  else {
         |    42
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testNotRemove_IfElse_Then_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  if (true) {$CARET
         |    someMethod1()
         |    someMethod2()
         |  }    else {
         |    42
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  if (true) $CARET
         |    someMethod1()
         |    someMethod2()
         |  }    else {
         |    42
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testRemove_IfElse_Else_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else {$CARET
         |       someMethod()
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else $CARET
         |       someMethod()
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testNotRemove_IfElse_Else_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else {$CARET
         |       someMethod()
         |       someMethod1()
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else $CARET
         |       someMethod()
         |       someMethod1()
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testNotRemove_IfElse_NonIndented(): Unit = {
    val before =
      s"""class A {
         |  if (true) 42 else if(false) 23 else {$CARET
         |    42
         |}""".stripMargin
    val after =
      s"""class A {
         |  if (true) 42 else if(false) 23 else $CARET
         |    42
         |}""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_IfElse_NonIndented_1(): Unit = {
    val before =
      s"""class A {
         |  {
         |    if (true) 42 else if(false) 23 else {$CARET
         |      42
         |  }
         |}""".stripMargin
    val after =
      s"""class A {
         |  {
         |    if (true) 42 else if(false) 23 else $CARET
         |      42
         |  }
         |}""".stripMargin
    doTest(before, after)
  }

  def testRemove_TryBlock_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  try {$CARET
         |    42
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try $CARET
         |    42
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testNotRemove_TryBlock_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  try {$CARET
         |    42
         |    422
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try $CARET
         |    42
         |    422
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testRemove_TryCathBlock_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  try {$CARET
         |    42
         |  } catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try $CARET
         |    42
         |  catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testRemove_TryCathBlock_SingleExpression_1(): Unit = {
    getCommonSettings.CATCH_ON_NEW_LINE = true
    val before =
      s"""class A {
         |  try {$CARET
         |    42
         |  }
         |  catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try $CARET
         |    42
         |  catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testRemove_TryCathBlock_SingleExpression_2(): Unit = {
    getCommonSettings.CATCH_ON_NEW_LINE = true
    val before =
      s"""class A {
         |  try {$CARET
         |    42
         |
         |  }
         |  catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try $CARET
         |    42
         |
         |  catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testNotRemove_TryCatchBlock_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  try {$CARET
         |    42
         |    23
         |  } catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try $CARET
         |    42
         |    23
         |  } catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testRemove_FinallyBlock_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  try {
         |    42
         |  } finally {$CARET
         |    42
         |  }
         |
         |
         |  someUnrelatedCode()
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try {
         |    42
         |  } finally $CARET
         |    42
         |
         |
         |  someUnrelatedCode()
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testRemove_FinallyBlock_SingleExpression_1(): Unit = {
    val before =
      s"""class A {
         |  try {
         |    42
         |  } finally {$CARET
         |    42
         |
         |
         |  }
         |  someUnrelatedCode()
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try {
         |    42
         |  } finally $CARET
         |    42
         |
         |
         |  someUnrelatedCode()
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testNotRemove_FinallyBlock_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  try {
         |    42
         |  } finally {$CARET
         |    42
         |    23
         |  }
         |
         |
         |  someUnrelatedCode()
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try {
         |    42
         |  } finally $CARET
         |    42
         |    23
         |  }
         |
         |
         |  someUnrelatedCode()
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testRemove_DoWhile_MultipleExpressions(): Unit = {
    val before =
      s"""do {$CARET
         |  someMethod1()
         |
         |} while (true)
      """.stripMargin
    val after =
      s"""do $CARET
         |  someMethod1()
         |
         |while (true)
      """.stripMargin
    doTest(before, after)
  }

  def testNotRemove_DoWhile_MultipleExpressions(): Unit = {
    val before =
      s"""do {$CARET
         |  someMethod1()
         |  someMethod2()
         |} while (true)
      """.stripMargin
    val after =
      s"""do $CARET
         |  someMethod1()
         |  someMethod2()
         |} while (true)
      """.stripMargin
    doTest(before, after)
  }

  def testRemove_While_MultipleExpressions(): Unit = {
    val before =
      s"""while (true) {$CARET
         |  42
         |
         |}
         |
         |println()
         |""".stripMargin
    val after =
      s"""while (true) $CARET
         |  42
         |
         |
         |println()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_While_MultipleExpressions(): Unit = {
    val before =
      s"""while (true) {$CARET
         |  42
         |  23
         |
         |}
         |
         |println()
         |""".stripMargin
    val after =
      s"""while (true) $CARET
         |  42
         |  23
         |
         |}
         |
         |println()
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_Empty(): Unit = {
    val before = s"for (_ <- Seq()) {$CARET}"
    val after = s"for (_ <- Seq()) $CARET"
    doTest(before, after)
  }

  def testRemove_ForStatement_WithParen_SingleExpression(): Unit = {
    val before =
      s"""for (_ <- Seq()) {$CARET
         |  obj.method()
         |     .method1()
         |
         |}
         |""".stripMargin
    val after =
      s"""for (_ <- Seq()) $CARET
         |  obj.method()
         |     .method1()
         |
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithBraces_SingleExpression(): Unit = {
    val before =
      s"""for { _ <- Seq() } {$CARET
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val after =
      s"""for { _ <- Seq() } $CARET
         |  obj.method()
         |     .method1()
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithYield_WithParen_SingleExpression(): Unit = {
    val before =
      s"""for (_ <- Seq()) yield {$CARET
         |  obj.method()
         |     .method1()
         |}
         |
         |""".stripMargin
    val after =
      s"""for (_ <- Seq()) yield $CARET
         |  obj.method()
         |     .method1()
         |
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithYield_WithBraces_SingleExpression(): Unit = {
    val before =
      s"""for { _ <- Seq() } yield {$CARET
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val after =
      s"""for { _ <- Seq() } yield $CARET
         |  obj.method()
         |     .method1()
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithMultilineBraces_SingleExpression(): Unit = {
    val before =
      s"""for {
         |  _ <- Option(42)
         |} {$CARET
         |  println(42)
         |}
         |""".stripMargin
    val after =
      s"""for {
         |  _ <- Option(42)
         |} $CARET
         |  println(42)
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithMultilineBraces_WithYield_SingleExpression(): Unit = {
    val before =
      s"""for {
         |  _ <- Option(42)
         |} yield {$CARET
         |  println(42)
         |}
         |""".stripMargin
    val after =
      s"""for {
         |  _ <- Option(42)
         |} yield $CARET
         |  println(42)
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_ForStatement_WithYield_WithBraces_MultipleExpressions(): Unit = {
    val before =
      s"""for { _ <- Seq() } yield {$CARET
         |  obj.method()
         |  obj.method1()
         |}
         |""".stripMargin
    val after =
      s"""for { _ <- Seq() } yield $CARET
         |  obj.method()
         |  obj.method1()
         |}
         |""".stripMargin
    doTest(before, after)
  }

  // Empty body
  def testRemove_EmptyFunctionBody_WithType(): Unit = {
    val before =
      s"""def foo(name: String): Unit = {$CARET
         |
         |}
         |""".stripMargin
    val after =
      s"""def foo(name: String): Unit = $CARET
         |
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_EmptyFunctionBody_WithoutType(): Unit = {
    val before =
      s"""def foo(name: String) = {$CARET
         |}
         |""".stripMargin
    val after =
      s"""def foo(name: String) = $CARET
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_TryCathBlock_EmptyBody(): Unit = {
    val before =
      s"""class A {
         |  try {$CARET
         |  } catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin
    val after =
      s"""class A {
         |  try $CARET
         |  catch {
         |    case _ =>
         |  }
         |}
      """.stripMargin

    doTest(before, after)
  }

  def testApplicationSettingShouldDisableUnwrapping(): Unit = {
    val before =
      s"""def foo = {$CARET
         |  42
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo = $CARET
         |  42
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo = $CARET
         |  42
         |}
         |""".stripMargin

    val settings = ScalaApplicationSettings.getInstance
    val settingBefore = settings.WRAP_SINGLE_EXPRESSION_BODY
    try {
      doTest(before, afterWithEnabled)
      settings.WRAP_SINGLE_EXPRESSION_BODY = false
      doTest(before, afterWithDisabled)
    } finally {
      settings.WRAP_SINGLE_EXPRESSION_BODY = settingBefore
    }
  }


}
