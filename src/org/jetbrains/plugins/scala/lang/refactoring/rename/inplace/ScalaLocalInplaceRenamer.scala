package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import com.intellij.psi.{PsiDocumentManager, PsiFile, PsiElement, PsiNamedElement}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import java.util
import com.intellij.openapi.util.{TextRange, Pair}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.util.PairProcessor
import com.intellij.lang.Language
import org.jetbrains.annotations.NotNull

/**
 * Nikolay.Tropin
 * 1/20/14
 */
class ScalaLocalInplaceRenamer(elementToRename: PsiNamedElement, editor: Editor, project: Project, initialName: String, oldName: String)
        extends VariableInplaceRenamer(elementToRename, editor, project, initialName, oldName) {

  def this(@NotNull elementToRename: PsiNamedElement, editor: Editor) =
    this(elementToRename, editor, elementToRename.getProject,
      ScalaNamesUtil.scalaName(elementToRename), ScalaNamesUtil.scalaName(elementToRename))

  override def collectAdditionalElementsToRename(stringUsages: util.List[Pair[PsiElement, TextRange]]): Unit = {
    val stringToSearch: String = ScalaNamesUtil.scalaName(elementToRename)
    val currentFile: PsiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument)
    if (stringToSearch != null) {
      TextOccurrencesUtil.processUsagesInStringsAndComments(elementToRename, stringToSearch, true, new PairProcessor[PsiElement, TextRange] {
        def process(psiElement: PsiElement, textRange: TextRange): Boolean = {
          if (psiElement.getContainingFile == currentFile) {
            stringUsages.add(Pair.create(psiElement, textRange))
          }
          true
        }
      })
    }
  }

  override def isIdentifier(newName: String, language: Language): Boolean = ScalaNamesUtil.isIdentifier(newName)
}
