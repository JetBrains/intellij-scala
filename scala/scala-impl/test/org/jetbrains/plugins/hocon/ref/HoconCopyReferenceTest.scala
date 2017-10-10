package org.jetbrains.plugins.hocon
package ref

import java.awt.datatransfer.DataFlavor.stringFlavor

import com.intellij.openapi.actionSystem.IdeActions.ACTION_COPY_REFERENCE
import com.intellij.openapi.actionSystem.{ActionManager, DataContext}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.TestActionEvent
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class HoconCopyReferenceTest extends HoconActionTest(ACTION_COPY_REFERENCE, "copyReference") {

  override protected def executeAction(dataContext: DataContext)
                                      (implicit editor: Editor): String = {
    val action = ActionManager.getInstance.getAction(actionId)
    val actionEvent = new TestActionEvent(dataContext, action)

    action.beforeActionPerformedUpdate(actionEvent)
    actionEvent.getPresentation match {
      case presentation if presentation.isEnabled && presentation.isVisible =>
        action.actionPerformed(actionEvent)
      case _ =>
    }

    CopyPasteManager.getInstance.getContents
      .getTransferData(stringFlavor)
      .asInstanceOf[String]
  }
}

object HoconCopyReferenceTest extends TestSuiteCompanion[HoconCopyReferenceTest]
