package org.jetbrains.plugins.scala.lang.actions.editor.enter

import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.DoEditorStateTestOps
import org.jetbrains.plugins.scala.util.RevertableChange

abstract class FormatEmptyTemplateBodyEnterHandlerTest extends DoEditorStateTestOps {
  def testEmptyOneLineBodyNoSpaces(): Unit = doEnterTest(
    s"""class A{$CARET}""".stripMargin,
    s"""class A {
       |  $CARET
       |}""".stripMargin,
  )

  def testEmptyOneLineBodyNoSpacesIfSmartIndentIsTurnedOff(): Unit =
    RevertableChange.withModifiedSetting(CodeInsightSettings.getInstance)(false)(_.SMART_INDENT_ON_ENTER, _.SMART_INDENT_ON_ENTER = _).run {
      doEnterTest(
        s"""class A{$CARET}""".stripMargin,
        s"""class A{
           |$CARET}""".stripMargin,
      )
    }

  def testEmptyOneLineBodyWithParamsNoSpaces(): Unit = doEnterTest(
    s"""class A(i: Int){$CARET}""".stripMargin,
    s"""class A(i: Int) {
       |  $CARET
       |}""".stripMargin,
  )

  def testEmptyOneLineBodyOneSpace(): Unit = doEnterTest(
    s"""class A {$CARET}""".stripMargin,
    s"""class A {
       |  $CARET
       |}""".stripMargin,
  )

  def testEmptyOneLineBodyKeepMultipleSpaces(): Unit = doEnterTest(
    s"""class A  {$CARET}""".stripMargin,
    s"""class A  {
       |  $CARET
       |}""".stripMargin,
  )

  def testEmptyMultilineBodyNoSpaces(): Unit = doEnterTest(
    s"""class A{
       |  $CARET
       |}""".stripMargin,
    s"""class A {
       |
       |  $CARET
       |}""".stripMargin,
  )

  def testEmptyMultilineBodyOneSpace(): Unit = doEnterTest(
    s"""class A {
       |  $CARET
       |}""".stripMargin,
    s"""class A {
       |
       |  $CARET
       |}""".stripMargin,
  )

  def testEmptyMultilineBodyKeepMultipleSpaces(): Unit = doEnterTest(
    s"""class A  {
       |  $CARET
       |}""".stripMargin,
    s"""class A  {
       |
       |  $CARET
       |}""".stripMargin,
  )

  def testNonEmptyOneLineBodyNoSpaces(): Unit = doEnterTest(
    s"""class A{${CARET}def foo(): Unit = {}}""".stripMargin,
    s"""class A{
       |  ${CARET}def foo(): Unit = {}}""".stripMargin,
  )

  def testNonEmptyMultilineBodyNoSpaces(): Unit = doEnterTest(
    s"""class A{
       |  $CARET
       |  def foo(): Unit = {}
       |}""".stripMargin,
    s"""class A{
       |
       |  $CARET
       |  def foo(): Unit = {}
       |}""".stripMargin,
  )

  def testNonEmptyMultilineBodyNoSpaces2(): Unit = doEnterTest(
    s"""class A{
       |  def foo(): Unit = {$CARET}
       |}""".stripMargin,
    s"""class A{
       |  def foo(): Unit = {
       |    $CARET
       |  }
       |}""".stripMargin,
  )
}

final class FormatEmptyTemplateBodyEnterHandlerTest_Scala2 extends FormatEmptyTemplateBodyEnterHandlerTest {
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2
}

final class FormatEmptyTemplateBodyEnterHandlerTest_Scala3 extends FormatEmptyTemplateBodyEnterHandlerTest {
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}
