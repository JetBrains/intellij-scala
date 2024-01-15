package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaCompileServerSettings}

class Scala3IndentationBasedSyntaxClosingBraceRemoveTest extends ScalaBackspaceHandlerBaseTest {

  // copied from Scala3IndentationBasedSyntaxBackspaceTest
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  override def setUp(): Unit = {
    super.setUp()
    ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED = false
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
  }

  private def empty = ""

  private def withEnabledAndDisabled(before: String, afterWithEnabled: String, afterWithDisabled: String, onlyInIndentationBasedSyntax: Boolean = false): Unit = {
    val settingBefore = ScalaApplicationSettings.getInstance.DELETE_CLOSING_BRACE
    try {
      getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
      ScalaApplicationSettings.getInstance.DELETE_CLOSING_BRACE = true
      doTest(before, afterWithEnabled)

      // removing closing brace for single statements should only work with both settings for Scala 3 indentation based syntax
      getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
      ScalaApplicationSettings.getInstance.DELETE_CLOSING_BRACE = false
      doTest(before, afterWithDisabled)

      if (!onlyInIndentationBasedSyntax) {
        getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false
        ScalaApplicationSettings.getInstance.DELETE_CLOSING_BRACE = false
        doTest(before, afterWithDisabled)
      }
    }
    finally {
      ScalaApplicationSettings.getInstance.DELETE_CLOSING_BRACE = settingBefore
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
      s"""class a {
         |  def foo() = {${|}someMethod()}
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class a {
         |  def foo() = ${|}someMethod()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class a {
         |  def foo() = ${|}someMethod()}
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_SingleExpression_2(): Unit = {
    val before =
      s"""def foo()
         |  = {${|}someMethod1();
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo()
         |  = ${|}someMethod1();
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo()
         |  = ${|}someMethod1();
         |}
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

  def testRemove_FunctionBody_MultipleExpressions(): Unit = {
    val before =
      s"""class A{
         |  def foo() = {${|}
         |    someMethod1()
         |    someMethod2()}
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A{
         |  def foo() = ${|}
         |    someMethod1()
         |    someMethod2()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A{
         |  def foo() = ${|}
         |    someMethod1()
         |    someMethod2()}
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine(): Unit = {
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

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine_1(): Unit = {
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

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine_2(): Unit = {
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

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine_3(): Unit = {
    val before =
      s"""def foo() = {${|}someMethod1(); someMethod2()
         |// bla
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}someMethod1(); someMethod2()
         |// bla
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}someMethod1(); someMethod2()
         |// bla
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine_4(): Unit = {
    val before =
      s"""def foo() = {${|}someMethod1();
         |  someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}someMethod1();
         |  someMethod2()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}someMethod1();
         |  someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine_5(): Unit = {
    val before =
      s"""def foo() = {${|}someMethod1()
         |  someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}someMethod1()
         |  someMethod2()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}someMethod1()
         |  someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine_6(): Unit = {
    val before =
      s"""def foo() = {${|};
         |  someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|};
         |  someMethod2()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|};
         |  someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine_7(): Unit = {
    val before =
      s"""def foo() = {${|};
         |  ;someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|};
         |  ;someMethod2()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|};
         |  ;someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine_8(): Unit = {
    val before =
      s"""def foo()
         |  = {${|}someMethod1(); someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo()
         |  = ${|}someMethod1(); someMethod2()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo()
         |  = ${|}someMethod1(); someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MultipleExpressions_OneLine_9(): Unit = {
    val before =
      s"""def foo()
         |  = {${|}someMethod1(); someMethod2()}
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo()
         |  = ${|}someMethod1(); someMethod2()}
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo()
         |  = ${|}someMethod1(); someMethod2()}
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressions_OneLine(): Unit = {
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
  def testRemove_FunctionBody_MultipleExpressions_OneLine_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1(); someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1(); someMethod2()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1(); someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressions_OneLine_2(): Unit = {
    val before =
      s"""def foo() =
         |{${|}
         |  someMethod1(); someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() =
         |${|}
         |  someMethod1(); someMethod2()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() =
         |${|}
         |  someMethod1(); someMethod2()
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

  def testNotRemove_FunctionBody_MultipleExpressions_Indent_2(): Unit = {
    val before =
      s"""def foo = {${|}
         |  someMethod1()
         |{
         |    someMethod2()
         |  }
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo = ${|}
         |  someMethod1()
         |{
         |    someMethod2()
         |  }
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo = ${|}
         |  someMethod1()
         |{
         |    someMethod2()
         |  }
         |  someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressions_Indent(): Unit = {
    val before =
      s"""def foo = {${|}
         |  someMethod1()
         |  {
         |someMethod2()
         |  }
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo = ${|}
         |  someMethod1()
         |  {
         |someMethod2()
         |  }
         |  someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo = ${|}
         |  someMethod1()
         |  {
         |someMethod2()
         |  }
         |  someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MultipleExpressions_Indent_1(): Unit = {
    val before =
      s"""def foo = {${|}
         |  someMethod1()
         |  {
         |someMethod2()
         |}
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo = ${|}
         |  someMethod1()
         |  {
         |someMethod2()
         |}
         |  someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo = ${|}
         |  someMethod1()
         |  {
         |someMethod2()
         |}
         |  someMethod3()
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

  def testRemove_FunctionBody_MultipleExpressions_EmptyLine_Semicolon(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2();
         |
         |  someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2();
         |
         |  someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2();
         |
         |  someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsWithSameIndentation_After(): Unit = {
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

  def testRemove_FunctionBody_ExpressionsWithLesserIndentation_After(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |  // foo
         |    // bar
         |someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |  // foo
         |    // bar
         |someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |  // foo
         |    // bar
         |someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_Empty_ExpressionsWithSameIndentation_After(): Unit = {
    // removing the closing brace breaks semantics
    // for empty blocks this is usually intentional
    val before =
      s"""def foo() = {${|}
         |  // bar
         |}
         |  // foo
         |  someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  // bar
         |  // foo
         |  someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  // bar
         |}
         |  // foo
         |  someMethod3()
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

  def testRemove_EmptyFunctionBody_After(): Unit = {
    val before =
      s"""def foo(name: String) = {${|}
         |}
         |bla()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo(name: String) = ${|}
         |bla()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo(name: String) = ${|}
         |}
         |bla()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_EmptyFunctionBody_After_Indent(): Unit = {
    val before =
      s"""def foo(name: String) = {${|}
         |}
         |  bla()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo(name: String) = ${|}
         |  bla()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo(name: String) = ${|}
         |}
         |  bla()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_MethodInvocation_IndentedTooFarToTheLeft_After(): Unit = {
    val before =
      s"""object A:
         |  def foo() = {${|}
         |    someMethod1()
         |    someMethod2()
         |    // foo
         |  }
         |  // bar
         |//baz
         |.someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""object A:
         |  def foo() = ${|}
         |    someMethod1()
         |    someMethod2()
         |    // foo
         |
         |  // bar
         |//baz
         |.someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""object A:
         |  def foo() = ${|}
         |    someMethod1()
         |    someMethod2()
         |    // foo
         |  }
         |  // bar
         |//baz
         |.someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled, onlyInIndentationBasedSyntax = true)
  }

  def testNotRemove_FunctionBody_ExpressionsWithSameIndentation_Comment_After(): Unit = {
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

  def testNotRemove_FunctionBody_ExpressionsWithSameIndentation_After_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}
         |
         |
         |
         |
         | someMethod3()
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
         | someMethod3()
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
         | someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsWithGreaterIndentation_After(): Unit = {
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

  def testNotRemove_FunctionBody_Expressions_After(): Unit = {
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

  def testNotRemove_FunctionBody_ExpressionsIndent_After(): Unit = {
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

  def testNotRemove_FunctionBody_ExpressionsNoSpace_After(): Unit = {
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

  def testNotRemove_FunctionBody_ExpressionsSemicolon_After(): Unit = {
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
         |} ; someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |} ; someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsSemicolon_After_1(): Unit = {
    val before =
      s"""def foo() = {${|}
         |  someMethod1()
         |  someMethod2()
         |}   ;      someMethod3()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}   ;      someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}   ;      someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_ExpressionsSemicolon_After_2(): Unit = {
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
         |}; someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo() = ${|}
         |  someMethod1()
         |  someMethod2()
         |}; someMethod3()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_MethodInvocation_After(): Unit = {
    val before =
      s"""{
         |  def foo() = {${|}
         |    someMethod1()
         |    someMethod2()
         |    // foo
         |  }
         |  // bar
         |//baz
         |  .someMethod3()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""{
         |  def foo() = ${|}
         |    someMethod1()
         |    someMethod2()
         |    // foo
         |  }
         |  // bar
         |//baz
         |  .someMethod3()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""{
         |  def foo() = ${|}
         |    someMethod1()
         |    someMethod2()
         |    // foo
         |  }
         |  // bar
         |//baz
         |  .someMethod3()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_Infix_After(): Unit = {
    val before =
      s"""{
         |  def foo: Int = {${|}
         |    return 2
         |    1
         |  }
         |  + 2
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""{
         |  def foo: Int = ${|}
         |    return 2
         |    1
         |  }
         |  + 2
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""{
         |  def foo: Int = ${|}
         |    return 2
         |    1
         |  }
         |  + 2
         |}
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

  def testNotRemove_FunctionBody_MultipleExpressionsAndStatements_OneLine(): Unit = {
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

  def testNotRemove_FunctionBody_MultipleExpressionsAndStatements_OneLine_1(): Unit = {
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

  def testRemove_FunctionBody_Wrapped(): Unit = {
    val before =
      s"""{
         |  def foo() = {${|}
         |    someMethod()
         |  }
         |  }
         |""".stripMargin
    val afterWithEnabled =
      s"""{
         |  def foo() = ${|}
         |    someMethod()
         |  }
         |""".stripMargin
    val afterWithDisabled =
      s"""{
         |  def foo() = ${|}
         |    someMethod()
         |  }
         |  }
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_FunctionBody_Wrapped_If(): Unit = {
    val before =
      s"""if (true) { def foo() = {${|}
         |    someMethod()
         |  }
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) { def foo() = ${|}
         |    someMethod()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) { def foo() = ${|}
         |    someMethod()
         |  }
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_NotIntended(): Unit = {
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

  def testNotRemove_FunctionBody_NotIntended_1(): Unit = {
    val before =
      s"""class C {
         |    def bar = 1
         |    def foo() = {${|}
         |      someMethod2()
         |  1}
         |""".stripMargin
    val afterWithEnabled =
      s"""class C {
         |    def bar = 1
         |    def foo() = ${|}
         |      someMethod2()
         |  1}
         |""".stripMargin
    val afterWithDisabled =
      s"""class C {
         |    def bar = 1
         |    def foo() = ${|}
         |      someMethod2()
         |  1}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_Trailing_Then(): Unit = {
    val before =
      s"""{
         |  def foo = {${|}
         |    if false then
         |  }
         |someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""{
         |  def foo = ${|}
         |    if false then
         |  }
         |someMethod2()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""{
         |  def foo = ${|}
         |    if false then
         |  }
         |someMethod2()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_Trailing_Else(): Unit = {
    val before =
      s"""def foo = {${|}
         |  if (false)
         |    someMethod1()
         |  else
         |  // foo
         |
         |}
         |// bar
         |someMethod2()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo = ${|}
         |  if (false)
         |    someMethod1()
         |  else
         |  // foo
         |
         |}
         |// bar
         |someMethod2()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo = ${|}
         |  if (false)
         |    someMethod1()
         |  else
         |  // foo
         |
         |}
         |// bar
         |someMethod2()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_Trailing_Do(): Unit = {
    val before =
      s"""def foo = {${|}
         |  for x <- 0 to 1 do
         |}
         |someMethod2()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo = ${|}
         |  for x <- 0 to 1 do
         |}
         |someMethod2()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo = ${|}
         |  for x <- 0 to 1 do
         |}
         |someMethod2()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_Trailing_Catch(): Unit = {
    val before =
      s"""Exception() match
         |case e: IllegalArgumentException =>
         |  def foo = {${|}
         |    try {}
         |    catch
         |  }
         |case _: Exception => 2
         |""".stripMargin
    val afterWithEnabled =
      s"""Exception() match
         |case e: IllegalArgumentException =>
         |  def foo = ${|}
         |    try {}
         |    catch
         |  }
         |case _: Exception => 2
         |""".stripMargin
    val afterWithDisabled =
      s"""Exception() match
         |case e: IllegalArgumentException =>
         |  def foo = ${|}
         |    try {}
         |    catch
         |  }
         |case _: Exception => 2
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_Trailing_Finally(): Unit = {
    val before =
      s"""def foo = {${|}
         |  try {}
         |  finally
         |}
         |someMethod2()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo = ${|}
         |  try {}
         |  finally
         |}
         |someMethod2()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo = ${|}
         |  try {}
         |  finally
         |}
         |someMethod2()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_FunctionBody_Trailing_Yield(): Unit = {
    val before =
      s"""def foo = {${|}
         |  for x <- 0 to 1 yield
         |}
         |someMethod2()
         |""".stripMargin
    val afterWithEnabled =
      s"""def foo = ${|}
         |  for x <- 0 to 1 yield
         |}
         |someMethod2()
         |""".stripMargin
    val afterWithDisabled =
      s"""def foo = ${|}
         |  for x <- 0 to 1 yield
         |}
         |someMethod2()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_Trailing_Match(): Unit = {
    val before =
      s"""1 match
         |case 3 =>
         |  try {${|}
         |    2 match
         |  }
         |case x => x
         |""".stripMargin
    val afterWithEnabled =
      s"""1 match
         |case 3 =>
         |  try ${|}
         |    2 match
         |  }
         |case x => x
         |""".stripMargin
    val afterWithDisabled =
      s"""1 match
         |case 3 =>
         |  try ${|}
         |    2 match
         |  }
         |case x => x
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

  def testNotRemove_If_Then_MultipleExpressions_OneLine(): Unit = {
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

  def testNotRemove_If_Then_MultipleExpressions_OneLine_1(): Unit = {
    val before =
      s"""if (true
         |) {${|}someMethod1(); someMethod2()}
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true
         |) ${|}someMethod1(); someMethod2()}
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true
         |) ${|}someMethod1(); someMethod2()}
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

  def testNotRemove_IfElse_Then_SameLine(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |    someMethod1()
         |    someMethod2()
         |    if (true) 1}    else {
         |    42
         |  }
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |    if (true) 1}    else {
         |    42
         |  }
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |    someMethod1()
         |    someMethod2()
         |    if (true) 1}    else {
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

  def testNotRemove_IfElse_Else_SingleExpression_Indent(): Unit = {
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

  def testNotRemove_IfElse_Else_SingleExpression_After_1(): Unit = {
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

  def testNotRemove_IfElse_Nested_Oneline(): Unit = {
    val before =
      s"""if (true) {${|} if (false)
         |    print(1)
         |  }
         |else
         |  print(2)
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|} if (false)
         |    print(1)
         |  }
         |else
         |  print(2)
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|} if (false)
         |    print(1)
         |  }
         |else
         |  print(2)
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_Nested_Oneline_1(): Unit = {
    val before =
      s"""if (true) {${|}if (false)}
         |else 2
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|}if (false)}
         |else 2
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}if (false)}
         |else 2
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_Nested_Oneline_2(): Unit = {
    val before =
      s"""if (true) {${|}if (false)
         |}
         |else 2
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|}if (false)
         |}
         |else 2
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}if (false)
         |}
         |else 2
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_Nested_Oneline_3(): Unit = {
    val before =
      s"""if (true) {${|}if (false)}
         |
         |
         |
         |print(222)
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|}if (false)}
         |
         |
         |
         |print(222)
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}if (false)}
         |
         |
         |
         |print(222)
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_Nested_Oneline_4(): Unit = {
    val before =
      s"""if (true) {${|} if (false) {}
         |  }
         |else
         |  print(2)
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|} if (false) {}
         |  }
         |else
         |  print(2)
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|} if (false) {}
         |  }
         |else
         |  print(2)
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_Nested_Oneline_5(): Unit = {
    val before =
      s"""if (true) {${|}1}   else 2
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|}1 else 2
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}1}   else 2
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_IfElse_Nested_Oneline_6(): Unit = {
    val before =
      s"""if (true) {${|}1  }   else 2
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|}1  else 2
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}1  }   else 2
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_Nested_Oneline(): Unit = {
    val before =
      s"""if (true) {${|} if (false)
         |    print(1)
         |    else
         |      print(3)
         |  }
         |else
         |  print(2)
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|} if (false)
         |    print(1)
         |    else
         |      print(3)
         |else
         |  print(2)
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|} if (false)
         |    print(1)
         |    else
         |      print(3)
         |  }
         |else
         |  print(2)
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_Nested_Oneline_1(): Unit = {
    val before =
      s"""if (true) {${|}
         |  if (false)}
         |else 2
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|}
         |  if (false)
         |else 2
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}
         |  if (false)}
         |else 2
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_Nested_Oneline_2(): Unit = {
    val before =
      s"""class a {
         |  if (true) {${|}while (true) {1}}
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class a {
         |  if (true) ${|}while (true) {1}
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class a {
         |  if (true) ${|}while (true) {1}}
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_Nested_Oneline_3(): Unit = {
    val before =
      s"""class a {
         |  if (true) {${|}while (true) {}}
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class a {
         |  if (true) ${|}while (true) {}
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class a {
         |  if (true) ${|}while (true) {}}
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_Nested_Oneline_4(): Unit = {
    val before =
      s"""if (true) {${|} if (false) {}
         |  }
         |print(2)
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|} if (false) {}
         |print(2)
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|} if (false) {}
         |  }
         |print(2)
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_Nested_Oneline_5(): Unit = {
    val before =
      s"""if (true) {${|} if (false)
         |{}
         |  }
         |print(2)
         |""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|} if (false)
         |{}
         |print(2)
         |""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|} if (false)
         |{}
         |  }
         |print(2)
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

  def testRemove_IfElse_Else_MultipleExpressions_After(): Unit = {
    val before =
      s"""class A {
         |  if (true) {${|}
         |   42
         |  }    else {
         |       someMethod()
         |       someMethod1()
         |  }
         |    someMethod2()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  if (true) ${|}
         |   42
         |  else {
         |       someMethod()
         |       someMethod1()
         |  }
         |    someMethod2()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  if (true) ${|}
         |   42
         |  }    else {
         |       someMethod()
         |       someMethod1()
         |  }
         |    someMethod2()
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

  def testRemove_IfElse_Wrapped(): Unit = {
    val before =
      s"""{
         |  if (true) {
         |    someMethod()
         |  } else {${|}
         |    42
         |  }
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""{
         |  if (true) {
         |    someMethod()
         |  } else ${|}
         |    42
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""{
         |  if (true) {
         |    someMethod()
         |  } else ${|}
         |    42
         |  }
         |}
         |""".stripMargin
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

  def testRemove_IfElse_WithNestedIfWithoutElse(): Unit = {
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
         |else {
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

  def testNotRemove_IfElse_WithNestedIfWithoutElse_Indent(): Unit = {
    val before =
      s"""if (true) {${|}
         |  if (false)
         |    println("Smiling")
         |}   else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    val afterWithEnabled =
      s"""if (true) ${|}
         |  if (false)
         |    println("Smiling")
         |else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    val afterWithDisabled =
      s"""if (true) ${|}
         |  if (false)
         |    println("Smiling")
         |}   else {
         |  println("Launching the rocket!")
         |}""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_IfElse_WithNestedIfWithoutElse_1(): Unit = {
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
         |else {
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

  def testNotRemove_IfElse_Case(): Unit = {
    // https://github.com/lampepfl/dotty/issues/11444
    val before =
      s"""1 match {
         |  case a if {${|}
         |      val b = 11
         |      a < b}
         |   => a
         |}""".stripMargin
    val afterWithEnabled =
      s"""1 match {
         |  case a if ${|}
         |      val b = 11
         |      a < b}
         |   => a
         |}""".stripMargin
    val afterWithDisabled =
      s"""1 match {
         |  case a if ${|}
         |      val b = 11
         |      a < b}
         |   => a
         |}""".stripMargin
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

  def testRemove_While_MultipleExpressions(): Unit = {
    val before =
      s"""while (true
         |) {${|}
         |  42
         |  23
         |
         |}
         |
         |println()
         |""".stripMargin
    val afterWithEnabled =
      s"""while (true
         |) ${|}
         |  42
         |  23
         |
         |
         |println()
         |""".stripMargin
    val afterWithDisabled =
      s"""while (true
         |) ${|}
         |  42
         |  23
         |
         |}
         |
         |println()
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_While_MultipleExpressions_Do(): Unit = {
    // these are arbitrary block braces, ignored for now
    val before =
      s"""while true do {${|}
         |  42
         |  23
         |
         |}
         |
         |println()
         |""".stripMargin
    val afterWithEnabled =
      s"""while true do ${|}
         |  42
         |  23
         |
         |
         |println()
         |""".stripMargin
    val afterWithDisabled =
      s"""while true do ${|}
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

  def testNotRemove_ForStatement_Braces(): Unit = {
    val before =
      s"""for {${|} _ <- Seq() } {
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for ${|} _ <- Seq() } {
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""for ${|} _ <- Seq() } {
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_ForStatement_Braces_Do(): Unit = {
    val before =
      s"""for {${|} _ <- Seq() } do {
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for ${|} _ <- Seq()  do {
         |  obj.method()
         |     .method1()
         |""".stripMargin
    val afterWithDisabled =
      s"""for ${|} _ <- Seq() } do {
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_ForStatement_Enumerator(): Unit = {
    // don't remove braces here, because the scala parser does not recognize the indentation properly
    // https://github.com/lampepfl/dotty/issues/15039
    val before =
    s"""for {x <- {${|}
       |  val k = 3
       |  1 to k
       |}} println(x)
       |""".stripMargin
    val afterWithEnabled =
      s"""for {x <- ${|}
         |  val k = 3
         |  1 to k
         |}} println(x)
         |""".stripMargin
    val afterWithDisabled =
      s"""for {x <- ${|}
         |  val k = 3
         |  1 to k
         |}} println(x)
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

  // TODO ignored
  def _testRemove_ForStatement_Braces_Yields(): Unit = {
    val before =
      s"""for {${|} _ <- Seq() } yield {
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for ${|} _ <- Seq()  yield {
         |  obj.method()
         |     .method1()
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""for ${|} _ <- Seq() } yield {
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

  def testNotRemove_ForStatement_MultilineBraces(): Unit = {
    val before =
      s"""for {${|}
         |  _ <- Option(42)
         |} {
         |  println(42)
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for ${|}
         |  _ <- Option(42)
         |} {
         |  println(42)
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""for ${|}
         |  _ <- Option(42)
         |} {
         |  println(42)
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_ForStatement_MultilineBraces_Do(): Unit = {
    val before =
      s"""for {${|}
         |  _ <- Option(42)
         |} do {
         |  println(42)
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for ${|}
         |  _ <- Option(42)
         |do {
         |  println(42)
         |""".stripMargin
    val afterWithDisabled =
      s"""for ${|}
         |  _ <- Option(42)
         |} do {
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

  // TODO ignored
  def _testRemove_ForStatement_MultilineBraces_Yield(): Unit = {
    val before =
      s"""for {${|}
         |  _ <- Option(42)
         |} yield {
         |  println(42)
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""for ${|}
         |  _ <- Option(42)
         |yield {
         |  println(42)
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""for ${|}
         |  _ <- Option(42)
         |} yield {
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

  def testRemove_TryFinallyBlock_WithNestedTryWithoutFinallyBlock(): Unit = {
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
         |finally {
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

  def testRemove_TryFinallyBlock_WithNestedTryWithoutFinallyBlock_1(): Unit = {
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
         |finally {
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

  def testRemove_TryCatchBlock_WithNestedTryWithoutCatchBlock(): Unit = {
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
         |catch { case _: Exception42 => }
         |""".stripMargin
    val afterWithDisabled =
      s"""try ${|}
         |  try
         |    println("1")
         |} catch { case _: Exception42 => }
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testRemove_TryCatchBlock_WithNestedTryWithoutCatchBlock_1(): Unit = {
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
         |catch { case _: Exception42 => }
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

  def testRemove_IfTryIfMix(): Unit = {
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

  // TODO ignored
  def _testRemove_Match(): Unit = {
    val before =
      s"""x match {${|}
         |  case 42 => 42
         |  case _ => _
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""x match ${|}
         |  case 42 => 42
         |  case _ => _
         |""".stripMargin
    val afterWithDisabled =
      s"""x match ${|}
         |  case 42 => 42
         |  case _ => _
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_Match_Expression(): Unit = {
    val before =
      s"""{${|}
         |  x
         |} match {
         |  case 42 => 42
         |  case _ => _
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""${|}
         |  x
         |} match {
         |  case 42 => 42
         |  case _ => _
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""${|}
         |  x
         |} match {
         |  case 42 => 42
         |  case _ => _
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_Match_Indent(): Unit = {
    val before =
      s"""x match {${|}
         |    case 42 => 42
         |  case _ => _
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""x match ${|}
         |    case 42 => 42
         |  case _ => _
         |""".stripMargin
    val afterWithDisabled =
      s"""x match ${|}
         |    case 42 => 42
         |  case _ => _
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_Match_Indent_1(): Unit = {
    val before =
      s"""x match {${|}
         |case 42 => 42
         |case _ => _
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""x match ${|}
         |case 42 => 42
         |case _ => _
         |""".stripMargin
    val afterWithDisabled =
      s"""x match ${|}
         |case 42 => 42
         |case _ => _
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_Catch(): Unit = {
    val before =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch {${|}
         |  case _: IllegalArgumentException => {}
         |  case _: Exception => {}
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch ${|}
         |  case _: IllegalArgumentException => {}
         |  case _: Exception => {}
         |""".stripMargin
    val afterWithDisabled =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch ${|}
         |  case _: IllegalArgumentException => {}
         |  case _: Exception => {}
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_Catch_Indent(): Unit = {
    val before =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch {${|}
         |    case _: IllegalArgumentException => {}
         |  case _: Exception => {}
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch ${|}
         |    case _: IllegalArgumentException => {}
         |  case _: Exception => {}
         |""".stripMargin
    val afterWithDisabled =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch ${|}
         |    case _: IllegalArgumentException => {}
         |  case _: Exception => {}
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_Catch_Indent_1(): Unit = {
    val before =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch {${|}
         |case _: IllegalArgumentException => {}
         |case _: Exception => {}
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch ${|}
         |case _: IllegalArgumentException => {}
         |case _: Exception => {}
         |""".stripMargin
    val afterWithDisabled =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch ${|}
         |case _: IllegalArgumentException => {}
         |case _: Exception => {}
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_Catch_Oneline(): Unit = {
    val before =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch {${|}case _: IllegalArgumentException => {}}
         |""".stripMargin
    val afterWithEnabled =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch ${|}case _: IllegalArgumentException => {}
         |""".stripMargin
    val afterWithDisabled =
      s"""try {
         |  throw IllegalArgumentException()
         |}
         |catch ${|}case _: IllegalArgumentException => {}}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_Lambda(): Unit = {
    val before =
      s"""val f = (x: Int) => {${|}
         |  val y = x + 1
         |  y
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""val f = (x: Int) => ${|}
         |  val y = x + 1
         |  y
         |""".stripMargin
    val afterWithDisabled =
      s"""val f = (x: Int) => ${|}
         |  val y = x + 1
         |  y
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_Return(): Unit = {
    val before =
      s"""def f =
         |  someMethod1()
         |  return {${|}
         |    someMethod2()
         |    someMethod3()
         |  }
         |""".stripMargin
    val afterWithEnabled =
      s"""def f =
         |  someMethod1()
         |  return ${|}
         |    someMethod2()
         |    someMethod3()
         |""".stripMargin
    val afterWithDisabled =
      s"""def f =
         |  someMethod1()
         |  return ${|}
         |    someMethod2()
         |    someMethod3()
         |  }
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testRemove_Throw(): Unit = {
    val before =
      s"""def f =
         |  someMethod1()
         |  throw {${|}
         |    someMethod2()
         |    IllegalArgumentException()
         |  }
         |""".stripMargin
    val afterWithEnabled =
      s"""def f =
         |  someMethod1()
         |  throw ${|}
         |    someMethod2()
         |    IllegalArgumentException()
         |""".stripMargin
    val afterWithDisabled =
      s"""def f =
         |  someMethod1()
         |  throw ${|}
         |    someMethod2()
         |    IllegalArgumentException()
         |  }
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  // TODO ignored
  def _testNotRemove_Class(): Unit = {
    val before =
      s"""class A {${|}
         |  def x = 1
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A ${|}
         |  def x = 1
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A ${|}
         |  def x = 1
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }

  def testNotRemove_Arbitrary_Braces(): Unit = {
    val before =
      s"""class A {
         |  {${|}
         |    someMethod()
         |  }
         |  def x = 1
         |}
         |""".stripMargin
    val afterWithEnabled =
      s"""class A {
         |  ${|}
         |    someMethod()
         |  }
         |  def x = 1
         |}
         |""".stripMargin
    val afterWithDisabled =
      s"""class A {
         |  ${|}
         |    someMethod()
         |  }
         |  def x = 1
         |}
         |""".stripMargin
    withEnabledAndDisabled(before, afterWithEnabled, afterWithDisabled)
  }
}
