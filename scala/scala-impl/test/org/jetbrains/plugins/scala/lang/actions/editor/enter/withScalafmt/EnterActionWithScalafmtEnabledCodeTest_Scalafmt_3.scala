package org.jetbrains.plugins.scala.lang.actions.editor.enter.withScalafmt

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.TypeText
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtForTestsSetupOps
import org.scalafmt.dynamic.ScalafmtVersion

class EnterActionWithScalafmtEnabledCodeTest_Scalafmt_3_8 extends EnterActionWithScalafmtEnabledCodeTest_Scalafmt_3_Base {
  override protected def scalafmtVersion: String = "3.8.1"
}

class EnterActionWithScalafmtEnabledCodeTest_Scalafmt_3_11 extends EnterActionWithScalafmtEnabledCodeTest_Scalafmt_3_Base {
  override protected def scalafmtVersion: String = "3.11.5"
}

abstract class EnterActionWithScalafmtEnabledCodeTest_Scalafmt_3_Base extends EnterActionWithScalafmtEnabledTestBase {

  protected def scalafmtVersion: String

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  override def setUp(): Unit = {
    super.setUp()
    ScalaFmtForTestsSetupOps.ensureDownloaded(ScalafmtVersion.parse(scalafmtVersion).get)
    setScalafmtConfig(s"$scalafmtVersion.conf")
  }

  def testAfterIncompleteFunctionDefinition(): Unit = {
    doEditorStateTest(myFixture, (
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

  def testInImportSelectors(): Unit = {
    checkGeneratedTextAfterEnter(
      s"""import scala.util.{$CARET}""".stripMargin,
      s"""import scala.util.{
         |   $CARET
         |}""".stripMargin,
    )  }
}
