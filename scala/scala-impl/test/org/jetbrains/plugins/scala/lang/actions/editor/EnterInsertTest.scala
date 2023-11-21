package org.jetbrains.plugins.scala.lang.actions.editor

import com.intellij.application.options.CodeStyle
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class EnterInsertTest extends EditorTypeActionTestBase {
  override protected def typedChar: Char = '\n'

  // SCL-18488
  def testEnterInAlignedIfElse(): Unit = {
    CodeStyle.getSettings(getProject).getCustomSettings(classOf[ScalaCodeStyleSettings]).ALIGN_IF_ELSE = true
    doTest(
      s"""class Test {
         |  if (true) "yay"
         |  else if (false)$CARET "YAY"
         |}
         |""".stripMargin,
      s"""class Test {
         |  if (true) "yay"
         |  else if (false)
         |         $CARET"YAY"
         |}
         |""".stripMargin
    )
  }
}
