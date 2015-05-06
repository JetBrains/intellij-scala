package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import java.util

import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.lang.Language
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.inplace.{MemberInplaceRenamer, VariableInplaceRenamer}
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScalaNamesUtil, ScalaRefactoringUtil}

/**
 * Nikolay.Tropin
 * 6/20/13
 */
class ScalaMemberInplaceRenamer(elementToRename: PsiNamedElement,
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
    editor.putUserData(ScalaMemberInplaceRenamer.REVERT_INFO, revertInfo)

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
        val revertInfo = editor.getUserData(ScalaMemberInplaceRenamer.REVERT_INFO)
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

  private val substitutorOffset = substituted.getTextRange.getStartOffset

  override def getSubstituted: PsiElement = {
    val subst = super.getSubstituted
    if (subst != null && subst.getText == substituted.getText) subst
    else {
      val psiFile: PsiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument)
      if (psiFile != null) PsiTreeUtil.getParentOfType(psiFile.findElementAt(substitutorOffset), classOf[PsiNameIdentifierOwner])
      else null
    }

  }

  override def isIdentifier(newName: String, language: Language): Boolean = ScalaNamesUtil.isIdentifier(newName)

  override def createInplaceRenamerToRestart(variable: PsiNamedElement, editor: Editor, initialName: String): VariableInplaceRenamer =
    new ScalaMemberInplaceRenamer(variable, getSubstituted, editor, initialName, oldName)

  override def performInplaceRename(): Boolean = {
    val names = new util.LinkedHashSet[String]()
    names.add(initialName)
    try performInplaceRefactoring(names)
    catch {
      case t: Throwable =>
        val element = getVariable
        val subst = getSubstituted
        val offset = editor.getCaretModel.getOffset
        val text = editor.getDocument.getText
        val aroundCaret = text.substring(offset - 50, offset) + "<caret>" + text.substring(offset, offset + 50)
        val message =
          s"""Could not perform inplace rename:
             |element to rename: $element ${element.getName}
             |substituted: $subst
             |around caret: $aroundCaret""".stripMargin
        throw new Throwable(message, t)
    }
  }

  override def getNameIdentifier: PsiElement = {
    if (!myElementToRename.isPhysical) null
    else super.getNameIdentifier
  }
}
object ScalaMemberInplaceRenamer {
  val REVERT_INFO: Key[ScalaRefactoringUtil.RevertInfo] = new Key("RevertInfo")
}