package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.psi._
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.rename.inplace.ScalaMemberInplaceRenamer
import org.jetbrains.plugins.scala.lang.refactoring.scopeSuggester.ScopeItem
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.util.ScalaUtils

/**
 * Created by Kate Ustyuzhanina
 * on 8/10/15
 */
object ScalaInplaceTypeAliasIntroducer {
  def apply(scNamedElement: ScNamedElement,
            substituted: PsiElement,
            editor: Editor,
            initialName: String,
            oldName: String,
            scopeItem: ScopeItem): ScalaInplaceTypeAliasIntroducer = {

    IntroduceTypeAliasData.addScopeElement(scopeItem)
    new ScalaInplaceTypeAliasIntroducer(scNamedElement, substituted, editor, initialName, oldName)
  }

  def revertState(myEditor: Editor, scopeItem: ScopeItem, namedElement: ScNamedElement): Unit = {
    val myProject = myEditor.getProject
    if (scopeItem.isPackage) {
      val runnable = new Runnable() {
        def run() {
          scopeItem.fileEncloser.getNode.removeChild(namedElement.getNode)
        }
      }
      ScalaUtils.runWriteAction(runnable, myEditor.getProject, "Introduce Type Alias")
    }

    CommandProcessor.getInstance.executeCommand(myProject, new Runnable {
      def run() {
        val revertInfo = ScalaRefactoringUtil.RevertInfo(IntroduceTypeAliasData.initialInfo._1, IntroduceTypeAliasData.initialInfo._2)

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
        }
        if (!myProject.isDisposed && myProject.isOpen) {
          PsiDocumentManager.getInstance(myProject).commitDocument(document)
        }
      }
    }, "Introduce Type Alias", null)
  }
}

class ScalaInplaceTypeAliasIntroducer(scNamedElement: ScNamedElement,
                                      substituted: PsiElement,
                                      editor: Editor,
                                      initialName: String,
                                      oldName: String) extends ScalaMemberInplaceRenamer(scNamedElement, substituted, editor, initialName, oldName) {

  override def setAdvertisementText(text: String) = {
    myAdvertisementText = "Press ctrl + alt + v" + " to show dialog with more options"
  }

  override def startsOnTheSameElement(handler: RefactoringActionHandler, element: PsiElement): Boolean = {
    def checkEquals(typeAliasDefinition: ScTypeAliasDefinition) = {
      IntroduceTypeAliasData.getNamedElement == element
    }

    element match {
      case typeAliasDefinition: ScTypeAliasDefinition =>
        checkEquals(typeAliasDefinition) && handler.isInstanceOf[ScalaIntroduceVariableHandler]
      case _ => false
    }
  }

  override def revertState(): Unit = {
    //do nothing. we don't need to revert state
  }
}