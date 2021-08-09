package org.jetbrains.plugins.scala.lang.actions.editor.enter

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.TypeText
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.DoEditorStateTestOps
import org.jetbrains.plugins.scala.lang.formatter.tests.scalafmt.ScalaFmtForTestsSetupOps
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService
import org.jetbrains.plugins.scala.util.TestUtils

abstract class EnterActionWithScalafmtEnabledTestBase
  extends EditorActionTestBase
    with ScalaFmtForTestsSetupOps
    with DoEditorStateTestOps {

  override protected def scalafmtConfigsBasePath: String =
    TestUtils.getTestDataPath + "/actions/editor/enter/_scalafmt_configs/"

  override def setUp(): Unit = {
    super.setUp()
    ScalaFmtForTestsSetupOps.ensureDownloaded(
      "2.7.5",
      "3.0.0-RC6", // TODO: update to 3.0.0 when available
    )
  }

  override protected def configureByText(text: String, fileName: String, trimText: Boolean): Unit = {
    super.configureByText(text, fileName, trimText)

    //preload config fot a file, cause at the usage place it will be resolved in "fast" mode
    ScalafmtDynamicConfigService(getProject).configForFile(getFile)

    // resetting IntelliJ indent size to ensure that they do not play role in enter handler logic
    val indentOptions = getCommonSettings.getIndentOptions
    indentOptions.INDENT_SIZE = 0
    indentOptions.CONTINUATION_INDENT_SIZE = 0
    indentOptions.LABEL_INDENT_SIZE = 0
  }
}

class EnterActionWithScalafmtEnabledCodeTest_Scalafmt_2_7 extends EnterActionWithScalafmtEnabledTestBase {

  override def setUp(): Unit = {
    super.setUp()
    setScalafmtConfig("2.7.5.conf")
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

  def testAfterIncompleteFunctionDefinition(): Unit = {
    doEditorStateTest((
      s"""def foo = $CARET
         |""".stripMargin,
      TypeText.Enter
    ), (
      s"""def foo =
         |  $CARET
         |""".stripMargin,
      TypeText("1\n")
    ), (
      s"""def foo =
         |  1
         |  $CARET
         |""".stripMargin,
      TypeText("2\n")
    ), (
      s"""def foo = {
         |  1
         |  2
         |  $CARET
         |}
         |""".stripMargin,
      TypeText("3\n")
    ), (
      s"""def foo = {
         |  1
         |  2
         |  3
         |  $CARET
         |}
         |""".stripMargin,
      TypeText.Ignored
    ))
  }
}

class EnterActionWithScalafmtEnabledCodeTest_Scalafmt_3_0 extends EnterActionWithScalafmtEnabledTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  override def setUp(): Unit = {
    super.setUp()
    setScalafmtConfig("3.0.0-RC6.conf")
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

  def testAfterIncompleteFunctionDefinition(): Unit = {
    doEditorStateTest((
      s"""def foo = $CARET""",
      TypeText.Enter
    ), (
      s"""def foo =
         |   $CARET""".stripMargin,
      TypeText("1\n")
    ), (
      s"""def foo =
         |   1
         |   $CARET""".stripMargin,
      TypeText("2\n")
    ), (
      s"""def foo =
         |   1
         |   2
         |   $CARET""".stripMargin,
      TypeText("3\n")
    ), (
      s"""def foo =
         |   1
         |   2
         |   3
         |   $CARET""".stripMargin,
      TypeText.Ignored
    ))
  }
}
