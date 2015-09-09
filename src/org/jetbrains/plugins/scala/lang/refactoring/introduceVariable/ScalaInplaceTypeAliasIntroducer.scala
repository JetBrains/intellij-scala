package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import java.util

import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi._
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
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
    if (IntroduceTypeAliasData.scopeElements.size < 2) {
      IntroduceTypeAliasData.scopeElements.last.computeRanges()
      IntroduceTypeAliasData.scopeElements.last.computeTypeAliasOffset()
    }
    new ScalaInplaceTypeAliasIntroducer(scNamedElement, substituted, editor, initialName, oldName, scopeItem, file)
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
    //    getVariable eq element
    //    elements.apply(0).usualOccurrences.apply(0) == element
    true
  }


  //if revert to initial file before TemplateState.gototheend  - get error
  override def revertState(): Unit = {

    if (myOldName == null) return

    CommandProcessor.getInstance.executeCommand(myProject, new Runnable {
      def run() {
        val revertInfo = if (IntroduceTypeAliasData.revertToInitial) {
          ScalaRefactoringUtil.RevertInfo(IntroduceTypeAliasData.initialInfo._1, IntroduceTypeAliasData.initialInfo._2)
        }
        else {
          myEditor.getUserData(ScalaMemberInplaceRenamer.REVERT_INFO)
        }
        val document = myEditor.getDocument
        if (revertInfo != null) {
          extensions.inWriteAction {
            val textLength = document.getTextLength
            document.replaceString(0, document.getTextLength, revertInfo.fileText)
            val diff = textLength - revertInfo.fileText.length
            val q:String = " "*diff
            document.insertString(revertInfo.fileText.length, q)
            PsiDocumentManager.getInstance(myProject).commitDocument(document)
          }

//          if (IntroduceTypeAliasData.revertToInitial && myElementToRename.getContainingFile != file) {
//            val runnable = new Runnable() {
//              def run() {
//                  scopeItem.fileEncloser.getNode.removeChild(myElementToRename.getNode)
//              }
//            }
//            ScalaUtils.runWriteAction(runnable, editor.getProject, "Introduce Type Alias")
//            PsiDocumentManager.getInstance(myProject).commitDocument(editor.getDocument)
//          }

          val offset = revertInfo.caretOffset
          myEditor.getCaretModel.moveToOffset(offset)
          myEditor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
          PsiDocumentManager.getInstance(myEditor.getProject).commitDocument(document)
          val clazz = myElementToRename.getClass
          val element = TargetElementUtilBase.findTargetElement(myEditor,
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


  override def collectRefs(referencesSearchScope: SearchScope): util.Collection[PsiReference] = {
//    super.collectRefs(referencesSearchScope)
    val ranges = IntroduceTypeAliasData.scopeElements.head.occurrencesRanges
    val typeElements = ranges.map((x: TextRange) => PsiTreeUtil.findElementOfClassAtOffset(file, x.getStartOffset, classOf[ScTypeElement], true))
    val q = typeElements.map((x: ScTypeElement) => PsiTreeUtil.getChildOfAnyType(x, classOf[ScStableCodeReferenceElement]))

    import scala.collection.JavaConversions.asJavaCollection
    new util.ArrayList[PsiReference](q.toIterable)
  }

  override def finish(success: Boolean) = {
    super.finish(success)
    if (StartMarkAction.canStart(editor.getProject) != null) {
//      IntroduceTypeAliasData.clearData()
      println("clear")
    }

    //    IntroduceTypeAliasData.clearData
  }


}