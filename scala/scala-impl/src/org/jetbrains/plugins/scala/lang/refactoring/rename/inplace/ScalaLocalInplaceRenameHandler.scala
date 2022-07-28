package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import com.intellij.refactoring.rename.inplace.{InplaceRefactoring, VariableInplaceRenameHandler, VariableInplaceRenamer}
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

class ScalaLocalInplaceRenameHandler extends VariableInplaceRenameHandler with ScalaInplaceRenameHandler {

  override def isAvailable(element: PsiElement, editor: Editor, file: PsiFile): Boolean = {
    val processor = renameProcessor(element)
    editor.getSettings.isVariableInplaceRenameEnabled &&
      processor != null &&
      processor.canProcessElement(element) &&
      isLocal(element)
  }

  override def createRenamer(elementToRename: PsiElement, editor: Editor): VariableInplaceRenamer = {
    elementToRename match {
      case named: PsiNamedElement => new ScalaLocalInplaceRenamer(named, editor)
      case _ => throw new IllegalArgumentException(s"Cannot rename element: \n${elementToRename.getText}")
    }
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext): Unit = {
    Stats.trigger(FeatureKey.renameLocal)
    super.invoke(project, editor, file, dataContext)
  }

  override def doRename(elementToRename: PsiElement, editor: Editor, dataContext: DataContext): InplaceRefactoring = {
    afterElementSubstitution(elementToRename, editor) {
      super.doRename(_, editor, dataContext)
    }
  }

}
