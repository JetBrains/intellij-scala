package org.jetbrains.plugins.scala
package quickfixes
package addModifier

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
class AddModifierTest extends base.ScalaLightPlatformCodeInsightTestCaseAdapter {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testAbstractModifier(): Unit = {
    configureFromFileTextAdapter(
      "dummy.scala",
      s"@Deprecated class Foo$CARET extends Runnable"
    )

    import extensions._
    val place = getFileAdapter.findElementAt(getEditorAdapter.getCaretModel.getOffset)
    PsiTreeUtil.getParentOfType(place, classOf[ScModifierListOwner]) match {
      case null =>
      case owner => inWriteAction {
        owner.getModifierList.setModifierProperty(ScalaModifier.ABSTRACT, true)
      }
    }

    checkResultByText(s"@Deprecated abstract class Foo$CARET extends Runnable")
  }

}