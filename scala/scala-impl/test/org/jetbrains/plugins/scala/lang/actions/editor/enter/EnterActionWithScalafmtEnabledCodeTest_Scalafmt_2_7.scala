package org.jetbrains.plugins.scala.lang.actions.editor.enter

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.TypeText
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtForTestsSetupOps
import org.scalafmt.dynamic.ScalafmtVersion

class EnterActionWithScalafmtEnabledCodeTest_Scalafmt_2_7 extends EnterActionWithScalafmtEnabledTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = true

  override def setUp(): Unit = {
    super.setUp()
    val version = "2.7.5"
    ScalaFmtForTestsSetupOps.ensureDownloaded(ScalafmtVersion.parse(version).get)
    setScalafmtConfig(s"$version.conf")
  }

  def testAfterIncompleteFunctionDefinition(): Unit = {
    doEditorStateTest(myFixture, (
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
