package org.jetbrains.plugins.scala
package lang.refactoring.ui

import com.intellij.codeInsight.daemon.impl.JavaReferenceImporter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.ui.CodeFragmentTableCellEditorBase

class ScalaCodeFragmentTableCellEditor(project: Project)
  extends CodeFragmentTableCellEditorBase(project, ScalaFileType.INSTANCE) {

  override def stopCellEditing: Boolean = {
    val editor: Editor = myEditorTextField.getEditor
    if (editor != null) {
      JavaReferenceImporter.autoImportReferenceAtCursor(editor, myCodeFragment, true)
    }
    super.stopCellEditing
  }

}
