package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * Nikolay.Tropin
 * 8/17/13
 */
class ScalaGenerateCompanionObjectAction extends ScalaBaseGenerateAction(new ScalaGenerateCompanionObjectHandler) {

  override protected def isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean =
    handler.isValidFor(editor, file) && super.isValidForFile(project, editor, file)


}
