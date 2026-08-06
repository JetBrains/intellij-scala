package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile

private class ModificationCount(key: String) {
  private val CountKey = Key.create[Long](key)

  def apply(file: PsiFile): Long = file.getManager.getModificationTracker.getModificationCount

  def apply(editor: Editor): Long = CountKey.get(editor, 0L)

  def update(editor: Editor, count: Long): Unit = {
    editor.putUserData(CountKey, count)
  }
}
