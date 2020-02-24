package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import java.util

import com.intellij.lang.Language
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Pair, TextRange}
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiNamedElement}
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.util.PairProcessor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Nikolay.Tropin
 * 1/20/14
 */
class ScalaLocalInplaceRenamer(elementToRename: PsiNamedElement, editor: Editor, project: Project, initialName: String, oldName: String)
        extends VariableInplaceRenamer(elementToRename, editor, project, initialName, oldName) {

  private val elementRange = editor.getDocument.createRangeMarker(elementToRename.getTextRange)

  def this(@NotNull elementToRename: PsiNamedElement, editor: Editor) =
    this(elementToRename, editor, elementToRename.getProject,
      ScalaNamesUtil.scalaName(elementToRename), ScalaNamesUtil.scalaName(elementToRename))

  override def collectAdditionalElementsToRename(stringUsages: util.List[Pair[PsiElement, TextRange]]): Unit = {
    val stringToSearch: String = ScalaNamesUtil.scalaName(elementToRename)
    val currentFile: PsiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument)
    if (stringToSearch != null) {
      TextOccurrencesUtil.processUsagesInStringsAndComments(elementToRename, stringToSearch, true, (psiElement: PsiElement, textRange: TextRange) => {
        if (psiElement.getContainingFile == currentFile) {
          stringUsages.add(Pair.create(psiElement, textRange))
        }
        true
      })
    }
  }

  override def isIdentifier(newName: String, language: Language): Boolean =
    ScalaNamesValidator.isIdentifier(newName)

  override def startsOnTheSameElement(handler: RefactoringActionHandler, element: PsiElement): Boolean = {
    handler match {
      case _: ScalaLocalInplaceRenameHandler => ScalaRenameUtil.sameElement(elementRange, element)
      case _ => false
    }
  }

  override def checkLocalScope(): PsiElement = Option(super.checkLocalScope()).getOrElse {
    Option(myElementToRename.getContainingFile.getVirtualFile).filter(_.isInstanceOf[BackedVirtualFile]).map(
      _ => myElementToRename.getContainingFile).orNull
  }
}
