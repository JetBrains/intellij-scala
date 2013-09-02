package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.psi._
import com.intellij.openapi.editor.Editor
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import com.intellij.psi.search.SearchScope
import java.util
import com.intellij.openapi.util.{TextRange, Pair}
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.util.PairProcessor
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil

/**
 * Nikolay.Tropin
 * 6/20/13
 */
class ScalaInplaceRenamer(elementToRename: PsiNamedElement,
                                substituted: PsiNamedElement,
                                editor: Editor,
                                initialName: String,
                                oldName: String)
        extends MemberInplaceRenamer(elementToRename, substituted, editor, initialName, oldName) {

  private def this(t: (PsiNamedElement, PsiNamedElement, Editor, String, String)) = this(t._1, t._2, t._3, t._4, t._5)

  def this(elementToRename: PsiNamedElement, substituted: PsiNamedElement, editor: Editor) {
    this {
      val name = ScalaNamesUtil.scalaName(substituted)
      (elementToRename, substituted, editor, name, name)
    }
  }

  def this(elementToRename: PsiNamedElement, substituted: PsiNamedElement, editor: Editor, additionalToRename: Seq[PsiElement]) {
    this(elementToRename, substituted, editor)
  }

  protected override def getCommandName: String = {
    if (myInitialName != null) RefactoringBundle.message("renaming.command.name", myInitialName)
    else "Rename"
  }


  override def collectAdditionalElementsToRename(stringUsages: util.List[Pair[PsiElement, TextRange]]) {
    if (ScalaInplaceRenameUtil.isLocallyDefined(elementToRename)) {
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
    } else stringUsages.clear()
  }

  override def collectRefs(referencesSearchScope: SearchScope): util.Collection[PsiReference] =
    ScalaRenameUtil.filterAliasedReferences {
      ReferencesSearch.search(substituted, referencesSearchScope, true).findAll()
    }


  override def revertStateOnFinish() {
    if (ScalaInplaceRenameUtil.isLocallyDefined(elementToRename) &&
            (myInsertedName == null || !isIdentifier(myInsertedName, elementToRename.getLanguage))) {
      revertState()
    }
    else {
      super.revertStateOnFinish()
    }
  }
}
