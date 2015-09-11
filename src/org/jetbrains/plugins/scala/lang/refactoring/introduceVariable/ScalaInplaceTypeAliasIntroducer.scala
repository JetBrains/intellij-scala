package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import java.util

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.rename.inplace.ScalaMemberInplaceRenamer
import org.jetbrains.plugins.scala.lang.refactoring.scopeSuggester.ScopeItem
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.util.ScalaUtils


/**
 * Created by Kate Ustyuzhanina on 8/10/15.
 */
object ScalaInplaceTypeAliasIntroducer {
  def apply(scNamedElement: ScNamedElement,
            substituted: PsiElement,
            editor: Editor,
            initialName: String,
            oldName: String,
            scopeItem: ScopeItem,
            file: PsiFile): ScalaInplaceTypeAliasIntroducer = {

    IntroduceTypeAliasData.addScopeElement(scopeItem)
    IntroduceTypeAliasData.currentScope.computeRanges()
    IntroduceTypeAliasData.currentScope.computeTypeAliasOffset()
    new ScalaInplaceTypeAliasIntroducer(scNamedElement, substituted, editor, initialName, oldName, scopeItem, file)
  }

  def revertState(myEditor: Editor, packageMode: Boolean, scopeItem: ScopeItem, namedElement: ScNamedElement): Unit = {
    val myProject = myEditor.getProject
    if (packageMode) {
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
                                      oldName: String,
                                      scopeItem: ScopeItem,
                                      file: PsiFile) extends ScalaMemberInplaceRenamer(scNamedElement, substituted, editor, initialName, oldName) {

  override def setAdvertisementText(text: String) = {
    myAdvertisementText = "Press ctrl + alt + v" + " to show dialog with more options"
  }

  override def startsOnTheSameElement(handler: RefactoringActionHandler, element: PsiElement): Boolean = {
    def checkEquals(typeAliasDefinition: ScTypeAliasDefinition) = {
      IntroduceTypeAliasData.getNamedElement == element
    }

    element match  {
      case typeAliasDefinition: ScTypeAliasDefinition =>
        checkEquals(typeAliasDefinition) && handler.isInstanceOf[ScalaIntroduceVariableHandler]
      case _ => false
    }
  }

  //TODO to find occurrence we need its file
  //we need't find fresh reference because we have their offsets
  //  override def collectRefs(referencesSearchScope: SearchScope): util.Collection[PsiReference] = {
  //    val ranges = IntroduceTypeAliasData.currentScope.occurrencesRanges
  //    val typeElements = ranges.map((x: TextRange) => PsiTreeUtil.findElementOfClassAtRange(file, x.getStartOffset,x.getEndOffset, classOf[ScTypeElement]))
  //    val q = typeElements.map((x: ScTypeElement) => PsiTreeUtil.getChildOfAnyType(x, classOf[ScStableCodeReferenceElement]))
  //    import scala.collection.JavaConversions.asJavaCollection
  //    new util.ArrayList[PsiReference](q.toIterable)
  //  }
}