package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

class Scala3IndentationBasedSyntaxClosingBraceRemoveTest extends ScalaBackspaceHandlerBaseTest {

  // copied from Scala3IndentationBasedSyntaxBackspaceTest
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  override def setUp(): Unit = {
    super.setUp()
    // indirect way of disabling compiler-based highlighting which is triggered on each editor changes
    // see org.jetbrains.plugins.scala.externalHighlighters.TriggerCompilerHighlightingService.condition
    ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED = false
    getScalaSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
  }

  private def empty = ""

  private def withEnabledAndDisabled(before: String, afterWithEnabled: String, afterWithDisabled: String): Unit = {
    val settings = ScalaApplicationSettings.getInstance
    val settingBefore = settings.WRAP_SINGLE_EXPRESSION_BODY
    try {
      getScalaSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
      settings.WRAP_SINGLE_EXPRESSION_BODY = true
      doTest(before, afterWithEnabled)

      // removing closing brace for single statements should work without WRAP_SINGLE_EXPRESSION_BODY
      getScalaSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
      settings.WRAP_SINGLE_EXPRESSION_BODY = false
      doTest(before, afterWithEnabled)

      getScalaSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false
      settings.WRAP_SINGLE_EXPRESSION_BODY = false
      doTest(before, afterWithDisabled)
    }
    finally {
      settings.WRAP_SINGLE_EXPRESSION_BODY = settingBefore
    }
  }

  def testRemove_FunctionBody_SingleExpression(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_SingleExpression_1(): Unit = {
    val before =
      s"""def foo() = {${|}someMethod()}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}someMethod()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}someMethod()}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_SingleExpression_2(): Unit = {
    val before =
      s"""def foo() = {${|}someMethod();}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}someMethod();
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}someMethod();}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_SingleExpression_Comment(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  // bla
         |  someMethod()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  // bla
         |  someMethod()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  // bla
         |  someMethod()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_SingleExpression_Comment_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod()
         |  // bla
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod()
         |  // bla
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod()
         |  // bla
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_SingleExpression_Comment_2(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod()
         |} // bla
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod()
         |// bla
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod()
         |} // bla
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_SingleExpression_Comment_3(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod()
         |}// bla
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod()
         |// bla
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod()
         |}// bla
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_ValInitializer_SingleExpression(): Unit = {
    val before =
      s"""val x = {${|}
         |  someMethod()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""val x = ${|}
         |  someMethod()
         |""".stripMargin
    val afterWithDisabled =
      s"""val x = ${|}
         |  someMethod()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_VarInitializer_SingleExpression(): Unit = {
    val before =
      s"""var x = {${|}
         |  someMethod()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""var x = ${|}
         |  someMethod()
         |""".stripMargin
    val afterWithDisabled =
      s"""var x = ${|}
         |  someMethod()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressions(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_Oneline(): Unit = {
    val before =
      s"""def foo() = {${|}someMethod1(); someMethod2()}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}someMethod1(); someMethod2()}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}someMethod1(); someMethod2()}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_Oneline_1(): Unit = {
    val before =
      s"""def foo() = {${|}  someMethod1();someMethod2()    }
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}  someMethod1();someMethod2()    }
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}  someMethod1();someMethod2()    }
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_Oneline_2(): Unit = {
    val before =
      s"""def foo() = {${|}someMethod1(); someMethod2(); someMethod3(); someMethod4()}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}someMethod1(); someMethod2(); someMethod3(); someMethod4()}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}someMethod1(); someMethod2(); someMethod3(); someMethod4()}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressions_Oneline(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2(); someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2(); someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2(); someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_Indent(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |someMethod2()
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |someMethod2()
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |someMethod2()
         |  someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressions_IndentComment(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |// bla
         |  someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |// bla
         |  someMethod2()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |// bla
         |  someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_Indent_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressions_IndentComment_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |// bla
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |// bla
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |// bla
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressions_EmptyLine(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |
         |  someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |
         |  someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsWithSameIndentation(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |  someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |  someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |  someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsWithSameIndentation_Comment(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |// bla
         |  someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |// bla
         |  someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |// bla
         |  someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsWithSameIndentation_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |
         |
         |
         |  someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |
         |
         |
         |  someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |
         |
         |
         |  someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsWithGreaterIndentation(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |
         |    someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |
         |    someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |
         |    someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsAfter(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |} someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |} someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |} someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsAfterIndent(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}   someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}   someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}   someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsAfterNoSpace(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_ExpressionsAfterSemicolon(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}; someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |; someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}; someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_ExpressionsAfterSemicolon_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |} ; someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |; someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |} ; someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_ExpressionsAfterSemicolon_2(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |};someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |;someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |};someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressionsAndStatements(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  val x = 42
         |  someMethod()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  val x = 42
         |  someMethod()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  val x = 42
         |  someMethod()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressionsAndStatements_Oneline(): Unit = {
    val before =
      s"""def foo() = {${|}val x = 42; someMethod()}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}val x = 42; someMethod()}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}val x = 42; someMethod()}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressionsAndStatements_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  try {
         |    someMethod1()
         |  }
         |  someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  try {
         |    someMethod1()
         |  }
         |  someMethod2()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  try {
         |    someMethod1()
         |  }
         |  someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressionsAndStatements_Oneline_1(): Unit = {
    val before =
      s"""def foo() = {${|}try {someMethod1()}; someMethod2()}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}try {someMethod1()}; someMethod2()}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}try {someMethod1()}; someMethod2()}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_NotIndented(): Unit = {
    // C closing brace is considered as foo closing brace, we do not want to remove it
    val before =
      s"""class C {
         |  def bar = 1
         |  def foo() = {${|}
         |    someMethod2()
         |}""".stripMargin
    val afterWithEnabled =
      s"""class C {
         |  def bar = 1
         |  def foo() = ${|}
         |    someMethod2()
         |}""".stripMargin
    val afterWithDisabled =
      s"""class C {
         |  def bar = 1
         |  def foo() = ${|}
         |    someMethod2()
         |}""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_If_Then_FollowedAfterAnotherIfStatement(): Unit = {
    val before =
      s"""val x =
         |  if (false) 1 else 0
         |val y = if (false) {${|}
         |  0
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""val x =
         |  if (false) 1 else 0
         |val y = if (false) ${|}
         |  0
         |""".stripMargin
    val afterWithDisabled =
      s"""val x =
         |  if (false) 1 else 0
         |val y = if (false) ${|}
         |  0
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_If_Then_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_If_Then_MultipleExpressions_1(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |  someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_If_Then_MultipleExpressions_Indent(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |    someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |    someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |    someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_If_Then_MultipleExpressions_Indent_1(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |      someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |      someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  }
         |      someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_If_Then_MultipleExpressions_Indent_2(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |  someMethod2()
         |  }
         |    someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |  someMethod2()
         |  }
         |    someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |  someMethod2()
         |  }
         |    someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_If_Then_MultipleExpressions_Indent_3(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |      someMethod2()
         |  }
         |    someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |      someMethod2()
         |  }
         |    someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |      someMethod2()
         |  }
         |    someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_If_Then_MultipleExpressions_After(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |    someMethod2()
         |  } someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  } someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  } someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_If_Then_MultipleExpressions_Oneline(): Unit = {
    val before =
      s"""if (true) {${|}someMethod1(); someMethod2()}
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|}someMethod1(); someMethod2()}
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}someMethod1(); someMethod2()}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod()
         |  else {
         |    42
         |  }
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod()
         |  }    else {
         |    42
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_Then_MultipleExpressions(): Unit = {
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
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  else {
         |    42
         |  }
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |  }    else {
         |    42
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_Else_SingleExpression_After(): Unit = {
    val before =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else {${|}
         |       someMethod()
         |  }
         |       someMethod()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |  }
         |       someMethod()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |  }
         |       someMethod()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testnNotRemove_IfElse_Else_SingleExpression_After_1(): Unit = {
    val before =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else {${|}
         |       someMethod()
         |  }
         |     someMethod()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |  }
         |     someMethod()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |  }
         |     someMethod()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_ElseWithoutLeadingSpace(): Unit = {
    val before =
      s"""if(false){${|}
         |  42
         |}else{
         |  23
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""if(false)${|}
         |  42
         |else{
         |  23
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""if(false)${|}
         |  42
         |}else{
         |  23
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_ElseWithoutLeadingSpace_1(): Unit = {
    val before =
      s"""if(false){${|}
         |  42    $empty
         |}else{
         |  23
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""if(false)${|}
         |  42    $empty
         |else{
         |  23
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""if(false)${|}
         |  42    $empty
         |}else{
         |  23
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_Else_MultipleExpressions(): Unit = {
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
    val afterWithEnabled =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |       someMethod1()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) {
         |   42
         |  }    else ${|}
         |       someMethod()
         |       someMethod1()
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_NonIndented(): Unit = {
    val before =
      s"""class A {
         |  if (true) 42 else if(false) 23 else {${|}
         |    42
         |  }
         |}""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) 42 else if(false) 23 else ${|}
         |    42
         |}""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) 42 else if(false) 23 else ${|}
         |    42
         |  }
         |}""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_NonIndented(): Unit = {
    val before =
      s"""class A {
         |  if (true) 42 else if(false) 23 else {${|}
         |    42
         |}""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) 42 else if(false) 23 else ${|}
         |    42
         |}""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) 42 else if(false) 23 else ${|}
         |    42
         |}""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_NonIndented_1(): Unit = {
    val before =
      s"""class A {
         |  {
         |    if (true) 42 else if(false) 23 else {${|}
         |      42
         |  }
         |}""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  {
         |    if (true) 42 else if(false) 23 else ${|}
         |      42
         |  }
         |}""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  {
         |    if (true) 42 else if(false) 23 else ${|}
         |      42
         |  }
         |}""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_NonIndented_2(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}42} else if(false) 23 else 42
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}42} else if(false) 23 else 42
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}42} else if(false) 23 else 42
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_TryBlock_SingleExpression(): Unit = {
    val before =
      s"""class A {
         |  try {${|}
         |    42
         |  }
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  try ${|}
         |    42
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  try ${|}
         |    42
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_TryBlock_MultipleExpressions(): Unit = {
    val before =
      s"""class A {
         |  try {${|}
         |    42
         |    422
         |  }
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  try ${|}
         |    42
         |    422
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  try ${|}
         |    42
         |    422
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""class A {
         |  try ${|}
         |    42
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  try ${|}
         |    42
         |  } catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_TryCatchBlock_SingleExpression_1(): Unit = {
    getCommonSettings.CATCH_ON_NEW_LINE = true
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
    val afterWithEnabled =
      s"""class A {
         |  try ${|}
         |    42
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  try ${|}
         |    42
         |  }
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_TryCatchBlock_SingleExpression_2(): Unit = {
    getCommonSettings.CATCH_ON_NEW_LINE = true
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
    val afterWithEnabled =
      s"""class A {
         |  try ${|}
         |    42
         |
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  try ${|}
         |    42
         |
         |  }
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_TryCatchBlock_MultipleExpressions(): Unit = {
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
    val afterWithEnabled =
      s"""class A {
         |  try ${|}
         |    42
         |    23
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  try ${|}
         |    42
         |    23
         |  } catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
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
    val afterWithDisabled =
      s"""class A {
         |  try {
         |    42
         |  } finally ${|}
         |    42
         |  }
         |
         |
         |  someUnrelatedCode()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
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
    val afterWithDisabled =
      s"""class A {
         |  try {
         |    42
         |  } finally ${|}
         |    42
         |
         |
         |  }
         |  someUnrelatedCode()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FinallyBlock_MultipleExpressions(): Unit = {
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
    val afterWithEnabled =
      s"""class A {
         |  try {
         |    42
         |  } finally ${|}
         |    42
         |    23
         |
         |
         |  someUnrelatedCode()
         |}
         |""".stripMargin
    val afterWithDisabled =
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
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_DoWhile_MultipleExpressions(): Unit = {
    val before =
      s"""do {${|}
         |  someMethod1()
         |
         |} while (true)
         |""".stripMargin
    val afterWithEnabled =
      s"""do ${|}
         |  someMethod1()
         |
         |while (true)
         |""".stripMargin
    val afterWithDisabled =
      s"""do ${|}
         |  someMethod1()
         |
         |} while (true)
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_DoWhile_MultipleExpressions_1(): Unit = {
    val before =
      s"""do {${|}
         |  someMethod1()
         |  someMethod2()
         |} while (true)
         |""".stripMargin
    val afterWithEnabled =
      s"""do ${|}
         |  someMethod1()
         |  someMethod2()
         |while (true)
         |""".stripMargin
    val afterWithDisabled =
      s"""do ${|}
         |  someMethod1()
         |  someMethod2()
         |} while (true)
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""while (true) ${|}
         |  42
         |
         |
         |println()
         |""".stripMargin
    val afterWithDisabled =
      s"""while (true) ${|}
         |  42
         |
         |}
         |
         |println()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_While_MultipleExpressions_1(): Unit = {
    val before =
      s"""while (true) {${|}
         |  42
         |  23
         |
         |}
         |
         |println()
         |""".stripMargin
    val afterWithEnabled =
      s"""while (true) ${|}
         |  42
         |  23
         |
         |
         |println()
         |""".stripMargin
    val afterWithDisabled =
      s"""while (true) ${|}
         |  42
         |  23
         |
         |}
         |
         |println()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_ForStatement_Empty(): Unit = {
    val before = s"for (_ <- Seq()) {${|}}"
    val afterWithEnabled = s"for (_ <- Seq()) ${|}"
    val afterWithDisabled = s"for (_ <- Seq()) ${|}"
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_ForStatement_WithParen_SingleExpression(): Unit = {
    val before =
      s"""for (_ <- Seq()) {${|}
         |  obj.method()
         |     .method1()
         |
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for (_ <- Seq()) ${|}
         |  obj.method()
         |     .method1()
         |
         |""".stripMargin
    val afterWithDisabled =
      s"""for (_ <- Seq()) ${|}
         |  obj.method()
         |     .method1()
         |
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_ForStatement_WithBraces_SingleExpression(): Unit = {
    val before =
      s"""for { _ <- Seq() } {${|}
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for { _ <- Seq() } ${|}
         |  obj.method()
         |     .method1()
         |""".stripMargin
    val afterWithDisabled =
      s"""for { _ <- Seq() } ${|}
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_ForStatement_WithYield_WithParen_SingleExpression(): Unit = {
    val before =
      s"""for (_ <- Seq()) yield {${|}
         |  obj.method()
         |     .method1()
         |}
         |
         |""".stripMargin
    val afterWithEnabled =
      s"""for (_ <- Seq()) yield ${|}
         |  obj.method()
         |     .method1()
         |
         |""".stripMargin
    val afterWithDisabled =
      s"""for (_ <- Seq()) yield ${|}
         |  obj.method()
         |     .method1()
         |}
         |
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_ForStatement_WithYield_WithBraces_SingleExpression(): Unit = {
    val before =
      s"""for { _ <- Seq() } yield {${|}
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for { _ <- Seq() } yield ${|}
         |  obj.method()
         |     .method1()
         |""".stripMargin
    val afterWithDisabled =
      s"""for { _ <- Seq() } yield ${|}
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_ForStatement_WithMultilineBraces_SingleExpression(): Unit = {
    val before =
      s"""for {
         |  _ <- Option(42)
         |} {${|}
         |  println(42)
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for {
         |  _ <- Option(42)
         |} ${|}
         |  println(42)
         |""".stripMargin
    val afterWithDisabled =
      s"""for {
         |  _ <- Option(42)
         |} ${|}
         |  println(42)
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_ForStatement_WithMultilineBraces_WithYield_SingleExpression(): Unit = {
    val before =
      s"""for {
         |  _ <- Option(42)
         |} yield {${|}
         |  println(42)
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for {
         |  _ <- Option(42)
         |} yield ${|}
         |  println(42)
         |""".stripMargin
    val afterWithDisabled =
      s"""for {
         |  _ <- Option(42)
         |} yield ${|}
         |  println(42)
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_ForStatement_WithYield_WithBraces_MultipleExpressions(): Unit = {
    val before =
      s"""for { _ <- Seq() } yield {${|}
         |  obj.method()
         |  obj.method1()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for { _ <- Seq() } yield ${|}
         |  obj.method()
         |  obj.method1()
         |""".stripMargin
    val afterWithDisabled =
      s"""for { _ <- Seq() } yield ${|}
         |  obj.method()
         |  obj.method1()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_EmptyFunctionBody_WithType(): Unit = {
    val before =
      s"""def foo(name: String): Unit = {${|}
         |
         |
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo(name: String): Unit = ${|}
         |
         |
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo(name: String): Unit = ${|}
         |
         |
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_EmptyFunctionBody_WithType_WithEmptySpaces(): Unit = {
    val before =
      s"""def foo(name: String): Unit = {${|}
         |
         |    $empty
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo(name: String): Unit = ${|}
         |
         |    $empty
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo(name: String): Unit = ${|}
         |
         |    $empty
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_EmptyFunctionBody_WithoutType(): Unit = {
    val before =
      s"""def foo(name: String) = {${|}
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo(name: String) = ${|}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo(name: String) = ${|}
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""class A {
         |  try ${|}
         |  catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  try ${|}
         |  } catch {
         |    case _ =>
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_WithNestedIfWithoutElse(): Unit = {
    val before =
      s"""if (true) {${|}
         |  if (false)
         |    println("Smiling")
         |} else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|}
         |  if (false)
         |    println("Smiling")
         |} else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}
         |  if (false)
         |    println("Smiling")
         |} else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""if (false) {
         |  println(1)
         |} else if (false) ${|}
         |  if (true)
         |    println(2)
         |} else {
         |  println(3)
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""if (false) {
         |  println(1)
         |} else if (false) ${|}
         |  if (true)
         |    println(2)
         |} else {
         |  println(3)
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""if (true) ${|}
         |  if (false)
         |    println("Smiling")
         |  else {}
         |else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}
         |  if (false)
         |    println("Smiling")
         |  else {}
         |} else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_TryFinallyBlock_WithNestedTryWithoutFinallyBlock(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""try ${|}
         |  try
         |    println("1")
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""try ${|}
         |  try
         |    println("1")
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _ => }
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _ => }
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  finally
         |    println("in inner finally")
         |finally {
         |  println("in finally")
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  finally
         |    println("in inner finally")
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
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
    val afterWithDisabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _ => }
         |  finally
         |    println("in inner finally")
         |} finally {
         |  println("in finally")
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_TryCatchBlock_WithNestedTryWithoutCatchBlock(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |} catch { case _: Exception42 => }
         |""".stripMargin
    val afterWithEnabled =
      s"""try ${|}
         |  try
         |    println("1")
         |} catch { case _: Exception42 => }
         |""".stripMargin
    val afterWithDisabled =
      s"""try ${|}
         |  try
         |    println("1")
         |} catch { case _: Exception42 => }
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_TryCatchBlock_WithNestedTryWithoutCatchBlock_1(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |  finally {}
         |} catch { case _: Exception42 => }
         |""".stripMargin
    val afterWithEnabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  finally {}
         |} catch { case _: Exception42 => }
         |""".stripMargin
    val afterWithDisabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  finally {}
         |} catch { case _: Exception42 => }
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_TryCatchBlock_WithNestedTryWithCatchBlock(): Unit = {
    val before =
      s"""try {${|}
         |  try
         |    println("1")
         |  catch { case _: Exception23: => }
         |} catch { case _: Exception42 => }
         |""".stripMargin
    val afterWithEnabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _: Exception23: => }
         |catch { case _: Exception42 => }
         |""".stripMargin
    val afterWithDisabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _: Exception23: => }
         |} catch { case _: Exception42 => }
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _: Exception23: => }
         |  finally {}
         |catch { case _: Exception42 => }
         |""".stripMargin
    val afterWithDisabled =
      s"""try ${|}
         |  try
         |    println("1")
         |  catch { case _: Exception23: => }
         |  finally {}
         |} catch { case _: Exception42 => }
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
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
    val afterWithEnabled =
      s"""if (false)
         |  try ${|}
         |    if (true)
         |      println(42)
         |  }
         |else
         |  println(23)
         |""".stripMargin
    val afterWithDisabled =
      s"""if (false)
         |  try ${|}
         |    if (true)
         |      println(42)
         |  }
         |else
         |  println(23)
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_Match(): Unit = {
    val before =
      s"""x match {${|}
         |  case 42 => 42
         |  case _ => _
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""x match ${|}
         |case 42 => 42
         |case _ => _
         |""".stripMargin
    val afterWithDisabled =
      s"""x match ${|}
         |  case 42 => 42
         |  case _ => _
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }
}
