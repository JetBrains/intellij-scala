package org.jetbrains.plugins.scala.lang.actions

import com.intellij.openapi.actionSystem.{CommonDataKeys, CustomizedDataContext, DataContext}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.FileSetTestBase

trait ActionTestBase { self: FileSetTestBase =>

  protected final val CaretMarker = "<caret>"

  /**
   * Removes `CaretMarker` from the file text.
   */
  protected def removeMarker(text: String): String = {
    val index = text.indexOf(CaretMarker)
    text.substring(0, index) + text.substring(index + CaretMarker.length)
  }
}

object ActionTestBase {
  def getDataContext(file: PsiFile): DataContext =
    CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT, sink => {
      sink.set(CommonDataKeys.LANGUAGE, file.getLanguage)
      sink.set(CommonDataKeys.PROJECT, file.getProject)
    })
}
