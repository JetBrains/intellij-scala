package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Pair, TextRange}
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiNamedElement, PsiReference}
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import com.intellij.refactoring.util.TextOccurrencesUtil.processUsagesInStringsAndComments
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.chaining._

class ScalaLocalInplaceRenamer(elementToRename: PsiNamedElement, editor: Editor, project: Project, initialName: String, oldName: String)
        extends VariableInplaceRenamer(elementToRename, editor, project, initialName, oldName) {

  private val elementRange = editor.getDocument.createRangeMarker(elementToRename.getTextRange)

  def this(@NotNull elementToRename: PsiNamedElement, editor: Editor) =
    this(elementToRename, editor, elementToRename.getProject,
      ScalaNamesUtil.scalaName(elementToRename), ScalaNamesUtil.scalaName(elementToRename))

  override def collectAdditionalElementsToRename(stringUsages: util.List[Pair[PsiElement, TextRange]]): Unit = {
    val stringToSearch: String = ScalaNamesUtil.scalaName(elementToRename)
    if (stringToSearch == null)
      return

    val localScope =
      currentFile.map(new LocalSearchScope(_))
        .getOrElse(LocalSearchScope.EMPTY)

    processUsagesInStringsAndComments(elementToRename, localScope, stringToSearch, true,
      (psiElement: PsiElement, textRange: TextRange) => {
        stringUsages.add(Pair.create(psiElement, textRange))
        true
      }
    )
  }

  override def collectRefs(referencesSearchScope: SearchScope): util.Collection[PsiReference] =
    super.collectRefs(referencesSearchScope).tap { refs =>
      elementToRename
        .withParentsInFile
        .findByType[ScBegin]
        .filter(_.tag == elementToRename)
        .flatMap(_.end)
        .foreach { end =>
          refs.addAll(end.getReferences.toSeq.asJava)
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

  override def checkLocalScope(): PsiElement =
    currentFile.getOrElse(super.checkLocalScope())

  private def currentFile: Option[PsiFile] =
    PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument).toOption
}
