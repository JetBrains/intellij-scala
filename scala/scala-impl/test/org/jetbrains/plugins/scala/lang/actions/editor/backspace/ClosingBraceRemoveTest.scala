package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaCompileServerSettings}

/** @see [[org.jetbrains.plugins.scala.lang.actions.editor.ClosingBraceInsertTest]]*/
class ClosingBraceRemoveTest extends ScalaBackspaceHandlerBaseTest {

  // copied from Scala3BracelessSyntaxEnterExhaustiveTest
  override def setUp(): Unit = {
    super.setUp()
    ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED = false
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false
  }

  private def empty = ""

  def testRemove_FunctionBody_SingleExpression(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod()
         |}
         |""".stripMargin
    val after =
      s"""def foo() = ${|}
         |  someMethod()
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_FunctionBody_SingleExpression_1(): Unit = {
    val before =
      s"""def foo() = {${|}someMethod()}
         |""".stripMargin
    val after =
      s"""def foo() = ${|}someMethod()
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_FunctionBody_SingleExpression_Wrapped(): Unit = {
    val before =
      s"""class A {
         |  def foo() = {${|}someMethod()}
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  def foo() = ${|}someMethod()
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ValInitializer_SingleExpression(): Unit = {
    val before =
      s"""val x = {${|}
         |  someMethod()
         |}
         |""".stripMargin
    val after =
      s"""val x = ${|}
         |  someMethod()
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_VarInitializer_SingleExpression(): Unit = {
    val before =
      s"""var x = {${|}
         |  someMethod()
         |}
         |""".stripMargin
    val after =
      s"""var x = ${|}
         |  someMethod()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_MultipleExpressions(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |""".stripMargin
    val after =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_Oneline(): Unit = {
    val before =
      s"""def foo() = {${|}someMethod1(); someMethod2()}
         |""".stripMargin
    val after =
      s"""def foo() = ${|}someMethod1(); someMethod2()}
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_MultipleExpressionsAndStatements(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  val x = 42
         |  someMethod()
         |}
         |""".stripMargin
    val after =
      s"""def foo() = ${|}
         |  val x = 42
         |  someMethod()
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_MultipleExpressionsAndStatements_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  try {
         |    someMethod1()
         |  }
         |  someMethod2()
         |}
         |""".stripMargin
    val after =
      s"""def foo() = ${|}
         |  try {
         |    someMethod1()
         |  }
         |  someMethod2()
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_NotIntended(): Unit = {
    // here there is an error - C closing brace is considered as foo closing brace, we do not want to remove it
    val before =
      s"""class C {
         |  def foo() = {${|}
         |    someMethod2()
         |}""".stripMargin
    val after =
      s"""class C {
         |  def foo() = ${|}
         |    someMethod2()
         |}""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_FunctionBody_NotIntended_1(): Unit = {
    val before =
      s"""class C {
         |    def foo() = {${|}
         |1}""".stripMargin
    val after =
      s"""class C {
         |    def foo() = ${|}
         |1}""".stripMargin
    doTest(before, after)
  }

  def testRemove_If_Then_FollowedAfterAnotherIfStatement(): Unit = {
    val before =
      s"""val x =
         |  if (false) 1 else 0
         |val y = if (false) {${|}
         |  0
         |}
         |""".stripMargin
    val after =
      s"""val x =
         |  if (false) 1 else 0
         |val y = if (false) ${|}
         |  0
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_If_Then_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_IfElse_Then_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod()
         |  }    else {
         |    42
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  if (true) ${|}
         |    someMethod()
         |  else {
         |    42
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_IfElse_Then_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |    someMethod2()
         |  }    else {
         |    42
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  }    else {
         |    42
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_IfElse_Else_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else {${|}
         |       someMethod()
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_IfElse_ElseWithoutLeadingSpace(): Unit = {
    val before =
      s"""if(false){${|}
         |  42
         |}else{
         |  23
         |}
         |""".stripMargin
    val after =
      s"""if(false)${|}
         |  42
         |else{
         |  23
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_IfElse_ElseWithoutLeadingSpace_1(): Unit = {
    val before =
      s"""if(false){${|}
         |  42    $empty
         |}else{
         |  23
         |}
         |""".stripMargin
    val after =
      s"""if(false)${|}
         |  42    $empty
         |else{
         |  23
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_IfElse_Else_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else {${|}
         |       someMethod()
         |       someMethod1()
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |       someMethod1()
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_IfElse_NonIndented(): Unit = {
    val before =
      s"""class A {
         |  if (true) 42 else if(false) 23 else {${|}
         |    42
         |}""".stripMargin
    val after =
      s"""class A {
         |  if (true) 42 else if(false) 23 else ${|}
         |    42
         |}""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_IfElse_NonIndented_1(): Unit = {
    val before =
      s"""class A {
         |  {
         |    if (true) 42 else if(false) 23 else {${|}
         |      42
         |  }
         |}""".stripMargin
    val after =
      s"""class A {
         |  {
         |    if (true) 42 else if(false) 23 else ${|}
         |      42
         |  }
         |}""".stripMargin
    doTest(before, after)
  }

  def testRemove_TryBlock_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  try {${|}
         |    42
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try ${|}
         |    42
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_TryBlock_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  try {${|}
         |    42
         |    422
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try ${|}
         |    42
         |    422
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_TryCatchBlock_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  try {${|}
         |    42
         |  } catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try ${|}
         |    42
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_TryCatchBlock_SingleExpression_1(): Unit = {
    getCommonCodeStyleSettings.CATCH_ON_NEW_LINE = true
    val before =
      s"""class A {
         |  try {${|}
         |    42
         |  }
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try ${|}
         |    42
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_TryCatchBlock_SingleExpression_2(): Unit = {
    getCommonCodeStyleSettings.CATCH_ON_NEW_LINE = true
    val before =
      s"""class A {
         |  try {${|}
         |    42
         |
         |  }
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try ${|}
         |    42
         |
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_TryCatchBlock_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  try {${|}
         |    42
         |    23
         |  } catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try ${|}
         |    42
         |    23
         |  } catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_FinallyBlock_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  try {
         |    42
         |  } finally {${|}
         |    42
         |  }
         |
         |
         |  someUnrelatedCode()
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try {
         |    42
         |  } finally ${|}
         |    42
         |
         |
         |  someUnrelatedCode()
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_FinallyBlock_SingleExpression_1(): Unit = {
    val before =
      s"""class A {
         |  try {
         |    42
         |  } finally {${|}
         |    42
         |
         |
         |  }
         |  someUnrelatedCode()
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try {
         |    42
         |  } finally ${|}
         |    42
         |
         |
         |  someUnrelatedCode()
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_FinallyBlock_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  try {
         |    42
         |  } finally {${|}
         |    42
         |    23
         |  }
         |
         |
         |  someUnrelatedCode()
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try {
         |    42
         |  } finally ${|}
         |    42
         |    23
         |  }
         |
         |
         |  someUnrelatedCode()
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_DoWhile_MultipleExpressions(): Unit = {
    val before =
      s"""do {${|}
         |  someMethod1()
         |
         |} while (true)
         |""".stripMargin
    val after =
      s"""do ${|}
         |  someMethod1()
         |
         |while (true)
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_DoWhile_MultipleExpressions(): Unit = {
    val before =
      s"""do {${|}
         |  someMethod1()
         |  someMethod2()
         |} while (true)
         |""".stripMargin
    val after =
      s"""do ${|}
         |  someMethod1()
         |  someMethod2()
         |} while (true)
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_While_MultipleExpressions(): Unit = {
    val before =
      s"""while (true) {${|}
         |  42
         |
         |}
         |
         |println()
         |""".stripMargin
    val after =
      s"""while (true) ${|}
         |  42
         |
         |
         |println()
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_While_MultipleExpressions(): Unit = {
    val before =
      s"""while (true) {${|}
         |  42
         |  23
         |
         |}
         |
         |println()
         |""".stripMargin
    val after =
      s"""while (true) ${|}
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
    val before = s"for (_ <- Seq()) {${|}}"
    val after = s"for (_ <- Seq()) ${|}"
    doTest(before, after)
  }

  def testRemove_ForStatement_WithParen_SingleExpression(): Unit = {
    val before =
      s"""for (_ <- Seq()) {${|}
         |  obj.method()
         |     .method1()
         |
         |}
         |""".stripMargin
    val after =
      s"""for (_ <- Seq()) ${|}
         |  obj.method()
         |     .method1()
         |
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithBraces_SingleExpression(): Unit = {
    val before =
      s"""for { _ <- Seq() } {${|}
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val after =
      s"""for { _ <- Seq() } ${|}
         |  obj.method()
         |     .method1()
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithYield_WithParen_SingleExpression(): Unit = {
    val before =
      s"""for (_ <- Seq()) yield {${|}
         |  obj.method()
         |     .method1()
         |}
         |
         |""".stripMargin
    val after =
      s"""for (_ <- Seq()) yield ${|}
         |  obj.method()
         |     .method1()
         |
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithYield_WithBraces_SingleExpression(): Unit = {
    val before =
      s"""for { _ <- Seq() } yield {${|}
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val after =
      s"""for { _ <- Seq() } yield ${|}
         |  obj.method()
         |     .method1()
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithMultilineBraces_SingleExpression(): Unit = {
    val before =
      s"""for {
         |  _ <- Option(42)
         |} {${|}
         |  println(42)
         |}
         |""".stripMargin
    val after =
      s"""for {
         |  _ <- Option(42)
         |} ${|}
         |  println(42)
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_ForStatement_WithMultilineBraces_WithYield_SingleExpression(): Unit = {
    val before =
      s"""for {
         |  _ <- Option(42)
         |} yield {${|}
         |  println(42)
         |}
         |""".stripMargin
    val after =
      s"""for {
         |  _ <- Option(42)
         |} yield ${|}
         |  println(42)
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_ForStatement_WithYield_WithBraces_MultipleExpressions(): Unit = {
    val before =
      s"""for { _ <- Seq() } yield {${|}
         |  obj.method()
         |  obj.method1()
         |}
         |""".stripMargin
    val after =
      s"""for { _ <- Seq() } yield ${|}
         |  obj.method()
         |  obj.method1()
         |}
         |""".stripMargin
    doTest(before, after)
  }

  // Empty body
  def testRemove_EmptyFunctionBody_WithType(): Unit = {
    val before =
      s"""def foo(name: String): Unit = {${|}
         |
         |
         |}
         |""".stripMargin
    val after =
      s"""def foo(name: String): Unit = ${|}
         |
         |
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_EmptyFunctionBody_WithType_WithEmptySpaces(): Unit = {
    val before =
      s"""def foo(name: String): Unit = {${|}
         |
         |    $empty
         |}
         |""".stripMargin
    val after =
      s"""def foo(name: String): Unit = ${|}
         |
         |    $empty
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_EmptyFunctionBody_WithoutType(): Unit = {
    val before =
      s"""def foo(name: String) = {${|}
         |}
         |""".stripMargin
    val after =
      s"""def foo(name: String) = ${|}
         |""".stripMargin
    doTest(before, after)
  }

  def testRemove_TryCatchBlock_EmptyBody(): Unit = {
    val before =
      s"""class A {
         |  try {${|}
         |  } catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  try ${|}
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testApplicationSettingShouldDisableUnwrapping(): Unit = {
    val before =
      s"""def foo = {${|}
         |  42
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo = ${|}
         |  42
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo = ${|}
         |  42
         |}
         |""".stripMargin

    val settings = ScalaApplicationSettings.getInstance
    val settingBefore = settings.DELETE_CLOSING_BRACE
    try {
      doTest(before, afterWithEnabled)
      settings.DELETE_CLOSING_BRACE = false
      doTest(before, afterWithDisabled)
    } finally {
      settings.DELETE_CLOSING_BRACE = settingBefore
    }
  }

  //
  // if-else with nested if-else
  //

  def testNotRemove_IfElse_WithNestedIfWithoutElse(): Unit = {
    val before =
      s"""if (true) {${|}
         |  if (false)
         |    println("Smiling")
         |} else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    val after =
      s"""if (true) ${|}
         |  if (false)
         |    println("Smiling")
         |} else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_IfElse_WithNestedIfWithoutElse_1(): Unit = {
    val before =
      s"""if (false) {
         |  println(1)
         |} else if (false) {${|}
         |  if (true)
         |    println(2)
         |} else {
         |  println(3)
         |}
         |""".stripMargin
    val after =
      s"""if (false) {
         |  println(1)
         |} else if (false) ${|}
         |  if (true)
         |    println(2)
         |} else {
         |  println(3)
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testNotRemove_IfElse_WithNestedIfWithoutElse_2(): Unit = {
    val before =
      s"""if (true) {${|}
         |  if (false)
         |    println("Smiling")
         |}
         |// foo
         |else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    val after =
      s"""if (true) ${|}
         |  if (false)
         |    println("Smiling")
         |}
         |// foo
         |else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    doTest(before, after)
  }

  def testRemove_IfElse_WithNestedIfWithElse(): Unit = {
    val before =
      s"""if (true) {${|}
         |  if (false)
         |    println("Smiling")
         |  else {}
         |} else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    val after =
      s"""if (true) ${|}
         |  if (false)
         |    println("Smiling")
         |  else {}
         |else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    doTest(before, after)
  }

  //
  // try-finally-catch with nested try-finally-catch
  //

  def testNotRemove_TryFinallyBlock_WithNestedTryWithoutFinallyBlock(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    val after =
      s"""try ${|}
         |  try
         |    println("1")
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_TryFinallyBlock_WithNestedTryWithoutFinallyBlock_1(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |  catch { case _ => }
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    val after =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _ => }
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_TryFinallyBlock_WithNestedTryWithFinallyBlock(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |  finally
         |    println("in inner finally")
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    val after =
      s"""try ${|}
         |  try
         |    println("1")
         |  finally
         |    println("in inner finally")
         |finally {
         |  println("in finally")
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_TryFinallyBlock_WithNestedTryWithFinallyBlock_1(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |  catch { case _ => }
         |  finally
         |    println("in inner finally")
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    val after =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _ => }
         |  finally
         |    println("in inner finally")
         |finally {
         |  println("in finally")
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_TryCatchBlock_WithNestedTryWithoutCatchBlock(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |} catch { case _: Exception42 => }
         |""".stripMargin
    val after =
      s"""try ${|}
         |  try
         |    println("1")
         |} catch { case _: Exception42 => }
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_TryCatchBlock_WithNestedTryWithoutCatchBlock_1(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |  finally {}
         |} catch { case _: Exception42 => }
         |""".stripMargin
    val after =
      s"""try ${|}
         |  try
         |    println("1")
         |  finally {}
         |} catch { case _: Exception42 => }
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_TryCatchBlock_WithNestedTryWithCatchBlock(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |  catch { case _: Exception23: => }
         |} catch { case _: Exception42 => }
         |""".stripMargin
    val after =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _: Exception23: => }
         |catch { case _: Exception42 => }
         |""".stripMargin

    doTest(before, after)
  }

  def testRemove_TryCatchBlock_WithNestedTryWithCatchBlock_1(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |  catch { case _: Exception23: => }
         |  finally {}
         |} catch { case _: Exception42 => }
         |""".stripMargin
    val after =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _: Exception23: => }
         |  finally {}
         |catch { case _: Exception42 => }
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_IfTryIfMix(): Unit = {
    val before =
      s"""if (false)
         |  try {${|}
         |    if (true)
         |      println(42)
         |  }
         |else
         |  println(23)
         |""".stripMargin
    val after =
      s"""if (false)
         |  try ${|}
         |    if (true)
         |      println(42)
         |  }
         |else
         |  println(23)
         |""".stripMargin

    doTest(before, after)
  }

  def testNotRemove_NotActivated(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod()
         |}
         |""".stripMargin
    val after =
      s"""def foo() = ${|}
         |  someMethod()
         |}
         |""".stripMargin

    val settingBefore = ScalaApplicationSettings.getInstance.DELETE_CLOSING_BRACE
    ScalaApplicationSettings.getInstance.DELETE_CLOSING_BRACE = false
    doTest(before, after)
    ScalaApplicationSettings.getInstance.DELETE_CLOSING_BRACE = settingBefore
  }
}
