package org.jetbrains.plugins.scala.lang.actions.editor.enter

import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.DoEditorStateTestOps
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtForTestsSetupOps
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService
import org.jetbrains.plugins.scala.util.TestUtils

abstract class EnterActionWithScalafmtEnabledTestBase extends DoEditorStateTestOps with ScalaFmtForTestsSetupOps {

  override protected def scalafmtConfigsBasePath: String =
    TestUtils.getTestDataPath + "/actions/editor/enter/_scalafmt_configs/"

  override protected def configureByText(text: String, fileName: String, trimText: Boolean): Unit = {
    super.configureByText(text, fileName, trimText)

    //preload config fot a file, cause at the usage place it will be resolved in "fast" mode
    ScalafmtDynamicConfigService(getProject).configForFile(getFile)

    // resetting IntelliJ indent size to ensure that they do not play role in enter handler logic
    val indentOptions = getCommonCodeStyleSettings.getIndentOptions
    indentOptions.INDENT_SIZE = 0
    indentOptions.CONTINUATION_INDENT_SIZE = 0
    indentOptions.LABEL_INDENT_SIZE = 0
  }

  def testInParameterClauses(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""trait A {
         |  def foo($CARET
         |         param1: Int,$CARET
         |         param2: Int$CARET
         |  ): String
         |}
         |""".stripMargin,
      s"""trait A {
         |  def foo(
         |         $CARET
         |         param1: Int,
         |         $CARET
         |         param2: Int
         |         $CARET
         |  ): String
         |}
         |""".stripMargin
    )

    //poorly-formatted initially
    checkGeneratedTextAfterEnter(
      s"""trait A {
         |  def foo($CARET
         |  param1: Int,$CARET
         |  param2: Int$CARET
         |  ): String
         |}
         |""".stripMargin,
      s"""trait A {
         |  def foo(
         |         $CARET
         |  param1: Int,
         |         $CARET
         |  param2: Int
         |         $CARET
         |  ): String
         |}
         |""".stripMargin
    )
  }

  def testInArguments(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""trait A {
         |  String.format($CARET
         |       arg1,$CARET
         |       arg2$CARET
         |  )
         |}
         |""".stripMargin,
      s"""trait A {
         |  String.format(
         |       $CARET
         |       arg1,
         |       $CARET
         |       arg2
         |       $CARET
         |  )
         |}
         |""".stripMargin
    )

    //poorly-formatted initially
    checkGeneratedTextAfterEnter(
      s"""trait A {
         |  String.format($CARET
         |  arg1,$CARET
         |  arg2$CARET
         |  )
         |}
         |""".stripMargin,
      s"""trait A {
         |  String.format(
         |       $CARET
         |  arg1,
         |       $CARET
         |  arg2
         |       $CARET
         |  )
         |}
         |""".stripMargin
    )
  }
}

