package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.ScalaVersion

abstract class DoEditorStateTestOps extends CheckIndentAfterTypingCodeOps {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  protected def doEnterTest(before: String, after: String, afterOther: String*): Unit = {
    (before +: after +: afterOther).sliding(2).foreach { case Seq(b, a) =>
      performTest(b, a, stripTrailingSpacesAfterAction = true) { () =>
        performEnterAction()
      }
    }
  }

  protected def doEnterTest_NonStrippingTrailingSpaces(before: String, after: String, afterOther: String*): Unit = {
    (before +: after +: afterOther).sliding(2).foreach { case Seq(b, a) =>
      performTest(b, a) { () =>
        performEnterAction()
      }
    }
  }

  protected def doEditorStateTest(fixture: JavaCodeInsightTestFixture, states: (String, TypeText)*): Unit =
    doEditorStateTest(fixture, EditorStates(states: _*))

  protected def doEditorStateTest(fixture: JavaCodeInsightTestFixture, editorStates: EditorStates): Unit = {
    val states = editorStates.states
    states.sliding(2).foreach { case Seq(before, after) =>
      val textBefore = before.text
      val textToType = before.textToType
      val textAfter = after.text

      performTest(textBefore, textAfter, stripTrailingSpacesAfterAction = true) { () =>
        val lines = linesToType(textToType)
        for {
          (line, lineIdx) <- lines.zipWithIndex
        } {
          if (lineIdx > 0) {
            performEnterAction()
          }
          if (line.nonEmpty) {
            performTypingAction(line)
            if (StringUtils.isNotBlank(line)) {
              adjustLineIndentAtCaretPosition(fixture)
            }
          }
        }
      }
    }
  }
}
