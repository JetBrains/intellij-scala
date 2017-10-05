package org.jetbrains.plugins.hocon.ref

import java.awt.datatransfer.DataFlavor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.plugins.hocon.{HoconActionTest, TestSuiteCompanion}
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class HoconCopyReferenceTest extends HoconActionTest(IdeActions.ACTION_COPY_REFERENCE, "copyReference") {
  protected def resultAfterAction(editor: Editor) =
    CopyPasteManager.getInstance.getContents.getTransferData(DataFlavor.stringFlavor).asInstanceOf[String]
}
object HoconCopyReferenceTest extends TestSuiteCompanion[HoconCopyReferenceTest]
