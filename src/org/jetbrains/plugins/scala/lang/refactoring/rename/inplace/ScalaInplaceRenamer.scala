package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.refactoring.rename.inplace.{VariableInplaceRenamer, MemberInplaceRenamer}
import com.intellij.psi._
import com.intellij.openapi.editor.{ScrollType, Editor}
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScalaRefactoringUtil, ScalaNamesUtil}
import com.intellij.psi.search.SearchScope
import java.util
import com.intellij.openapi.util.{Key, TextRange, Pair}
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.util.PairProcessor
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil
import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.lang.Language
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.command.CommandProcessor

/**
 * Nikolay.Tropin
 * 6/20/13
 */
class ScalaInplaceRenamer(elementToRename: PsiNamedElement,
                                substituted: PsiElement,
                                editor: Editor,
                                initialName: String,
                                oldName: String)
        extends MemberInplaceRenamer(elementToRename, substituted, editor, initialName, oldName) {

  private def this(t: (PsiNamedElement, PsiElement, Editor, String, String)) = this(t._1, t._2, t._3, t._4, t._5)

  def this(elementToRename: PsiNamedElement, substituted: PsiElement, editor: Editor) {
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
      super.collectRefs(referencesSearchScope)
    }

  override def restoreCaretOffset(offset: Int): Int = {
    offset.max(myCaretRangeMarker.getStartOffset).min(myCaretRangeMarker.getEndOffset)
  }

  override def acceptReference(reference: PsiReference): Boolean = true

  override def beforeTemplateStart() {
    super.beforeTemplateStart()

    val revertInfo = ScalaRefactoringUtil.RevertInfo(editor.getDocument.getText, editor.getCaretModel.getOffset)
    editor.putUserData(ScalaInplaceRenamer.REVERT_INFO, revertInfo)

    val file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument)
    val offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument, editor.getCaretModel.getOffset)
    val range = file.findElementAt(offset).getTextRange
    myCaretRangeMarker = myEditor.getDocument.createRangeMarker(range)
    myCaretRangeMarker.setGreedyToLeft(true)
    myCaretRangeMarker.setGreedyToRight(true)
  }

  override def revertState() {
    if (myOldName == null) return

    CommandProcessor.getInstance.executeCommand(myProject, new Runnable {
      def run() {
        val revertInfo = editor.getUserData(ScalaInplaceRenamer.REVERT_INFO)
        val document = myEditor.getDocument
        if (revertInfo != null) {
          extensions.inWriteAction {
            document.replaceString(0, document.getTextLength, revertInfo.fileText)
            PsiDocumentManager.getInstance(myProject).commitDocument(document)
          }
          val offset = revertInfo.caretOffset
          myEditor.getCaretModel.moveToOffset(offset)
          myEditor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
          PsiDocumentManager.getInstance(myEditor.getProject).commitDocument(document)
          val clazz = myElementToRename.getClass
          val element = TargetElementUtilBase.findTargetElement(myEditor,
            TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase.ELEMENT_NAME_ACCEPTED)
          myElementToRename = element match {
            case null => null
            case named: PsiNamedElement if named.getClass == clazz => named
            case _ =>
              RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, myEditor) match {
                case named: PsiNamedElement if named.getClass == clazz => named
                case _ => null
              }
          }
        }
        if (!myProject.isDisposed && myProject.isOpen) {
          PsiDocumentManager.getInstance(myProject).commitDocument(document)
        }
      }
    }, getCommandName, null)

  }

  override def getVariable: PsiNamedElement = {
    Option(super.getVariable).getOrElse {
      if (myElementToRename.isValid && oldName == ScalaNamesUtil.scalaName(myElementToRename)) myElementToRename
      else null
    }
  }

  private val substitorOffset = substituted.getTextRange.getStartOffset

  override def getSubstituted: PsiElement = {
    val subst = super.getSubstituted
    if (subst.getText == substituted.getText) subst
    else {
      val psiFile: PsiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument)
      if (psiFile != null) PsiTreeUtil.getParentOfType(psiFile.findElementAt(substitorOffset), classOf[PsiNameIdentifierOwner])
      else null
    }

  }

  override def isIdentifier(newName: String, language: Language): Boolean = ScalaNamesUtil.isIdentifier(newName)

  override def createInplaceRenamerToRestart(variable: PsiNamedElement, editor: Editor, initialName: String): VariableInplaceRenamer =
    new ScalaInplaceRenamer(variable, getSubstituted, editor, initialName, oldName)

  override def performInplaceRename(): Boolean = {
    val names = new util.LinkedHashSet[String]()
    names.add(initialName)
    performInplaceRefactoring(names)
  }
}
object ScalaInplaceRenamer {
  val REVERT_INFO: Key[ScalaRefactoringUtil.RevertInfo] = new Key("RevertInfo")
}