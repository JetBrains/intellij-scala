package org.jetbrains.plugins.scala.base

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.extensions.{inWriteCommandAction, invokeAndWait}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

trait HelperFixtureEditorOps {
  self: ScalaLightCodeInsightFixtureTestAdapter =>

  protected final def commitDocumentInEditor(): Unit = {
    val documentManager = PsiDocumentManager.getInstance(getProject)
    documentManager.commitDocument(getEditor.getDocument)
  }

  protected def changePsiAt(offset: Int): Unit = {
    val settings = ScalaApplicationSettings.getInstance()
    val oldAutoBraceSettings = settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY
    settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY = false
    try {
      typeAndRemoveChar(offset, 'a')
    } finally {
      settings.HANDLE_BLOCK_BRACES_INSERTION_AUTOMATICALLY = oldAutoBraceSettings
    }
  }

  protected def typeAndRemoveChar(offset: Int, charToTypeAndRemove: Char): Unit = invokeAndWait {
    val fixture = getFixture
    fixture.getEditor.getCaretModel.moveToOffset(offset)
    fixture.`type`(charToTypeAndRemove)
    commitDocumentInEditor()
    fixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    commitDocumentInEditor()
  }

  protected def insertAtOffset(offset: Int, text: String): Unit = {
    invokeAndWait {
      inWriteCommandAction {
        getFixture.getEditor.getDocument.insertString(offset, text)
        commitDocumentInEditor()
      }(getProject)
    }
  }
}