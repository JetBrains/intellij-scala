package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.{ScrollType, Editor}
import com.intellij.psi.{PsiNamedElement, PsiDocumentManager, PsiElement}
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.rename.inplace.ScalaMemberInplaceRenamer
import org.jetbrains.plugins.scala.lang.refactoring.scopeSuggester.ScopeItem
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.util.ScalaUtils

import scala.collection.mutable.ArrayBuffer

/**
 * Created by Kate Ustyuzhanina on 8/10/15.
 */


object ScalaInplaceTypeAliasIntroducer {
   def apply(scNamedElement: ScNamedElement,
            substituted: PsiElement,
            editor: Editor,
            initialName: String,
            oldName: String,
            scopeItem: ScopeItem): ScalaInplaceTypeAliasIntroducer = {

    IntroduceTypeAliasData.addScopeElement(scopeItem)
    new ScalaInplaceTypeAliasIntroducer(scNamedElement, substituted, editor, initialName, oldName, scopeItem)
  }
}

class ScalaInplaceTypeAliasIntroducer(scNamedElement: ScNamedElement,
                                      substituted: PsiElement,
                                      editor: Editor,
                                      initialName: String,
                                      oldName: String,
                                      scopeItem: ScopeItem) extends ScalaMemberInplaceRenamer(scNamedElement, substituted, editor, initialName, oldName) {

  override def setAdvertisementText(text: String) = {
    myAdvertisementText = "Press ctrl + alt + v" + " to show dialog with more options"
  }

  override def startsOnTheSameElement(handler: RefactoringActionHandler, element: PsiElement): Boolean = {
    //    getVariable eq element
    //    elements.apply(0).usualOccurrences.apply(0) == element
    true
  }

  override def revertState(): Unit = {
    if (IntroduceTypeAliasData.revertToInitial) {
      val runnable = new Runnable() {
        def run() {
          scopeItem.fileEncloser.getNode.removeChild(myElementToRename.getNode)
        }
      }
      ScalaUtils.runWriteAction(runnable, editor.getProject, "Introduce Type Alias")
    }

    if (myOldName == null) return

    CommandProcessor.getInstance.executeCommand(myProject, new Runnable {
      def run() {
        val revertInfo = if (IntroduceTypeAliasData.revertToInitial) {
          ScalaRefactoringUtil.RevertInfo(IntroduceTypeAliasData.initialInfo._1, IntroduceTypeAliasData.initialInfo._2)
        }
        else {
          editor.getUserData(ScalaMemberInplaceRenamer.REVERT_INFO)
        }
        val document = editor.getDocument
        if (revertInfo != null) {
          extensions.inWriteAction {
            document.replaceString(0, document.getTextLength, revertInfo.fileText)
            PsiDocumentManager.getInstance(myProject).commitDocument(document)
          }
          val offset = revertInfo.caretOffset
          editor.getCaretModel.moveToOffset(offset)
          editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
          PsiDocumentManager.getInstance(editor.getProject).commitDocument(document)
          val clazz = myElementToRename.getClass
          val element = TargetElementUtilBase.findTargetElement(editor,
            TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase.ELEMENT_NAME_ACCEPTED)
          myElementToRename = element match {
            case null => null
            case named: PsiNamedElement
              if named.getClass == clazz => named
            case _ =>
              RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, editor) match {
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
}