package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import com.intellij.codeInsight.template.impl.{TemplateManagerImpl, TemplateState}
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.util.{BalloonConflictsReporter, ScalaTypeValidator}

/**
 * Created by Kate Ustyuzhanina on 8/10/15.
 */
class ScalaInplaceTypeAliasIntroducer(project: Project,
                                      editor: Editor,
                                      typeElement: ScTypeElement,
                                      namedElement: PsiNamedElement,
                                      title: String,
                                      replaceAll: Boolean,
                                      forceInferType: Option[Boolean])
  extends InplaceVariableIntroducer[ScTypeElement](namedElement, editor, project, title, Array.empty[ScTypeElement], typeElement) {

  private var myTypeAliasDefinitionStartOffset: Int = 0
  private val newTypeAliasDefinition = ScalaPsiUtil.getParentOfType(namedElement, classOf[ScTypeAliasDefinition])
  private val myFile: PsiFile = namedElement.getContainingFile

  setTypeAliasDefifinition(newTypeAliasDefinition)

  protected override def checkLocalScope(): PsiElement = {
    val scope = new LocalSearchScope(myElementToRename.getContainingFile)
    val elements: Array[PsiElement] = scope.getScope
    PsiTreeUtil.findCommonParent(elements: _*)
  }

  protected override def getReferencesSearchScope(file: VirtualFile): SearchScope = {
    new LocalSearchScope(myElementToRename.getContainingFile)
  }

  protected override def startRename: StartMarkAction = {
    StartMarkAction.start(myEditor, myProject, getCommandName)
  }

  private def findTypeAliasDefinition(offset: Int): PsiElement = {
    val elem = myFile.findElementAt(offset)
    ScalaPsiUtil.getParentOfType(elem, classOf[ScTypeAliasDefinition])
  }

  private def getTypeAliasDefinition: PsiElement = findTypeAliasDefinition(myTypeAliasDefinitionStartOffset)

  private def setTypeAliasDefifinition(declaration: PsiElement): Unit = {
    myTypeAliasDefinitionStartOffset = declaration.getTextRange.getStartOffset
  }

  protected override def moveOffsetAfter(success: Boolean): Unit = {
    try {
      if (success) {
        if (myExprMarker != null) {
          val startOffset: Int = myExprMarker.getStartOffset
          val elementAt: PsiElement = myFile.findElementAt(startOffset)
          if (elementAt != null) {
            myEditor.getCaretModel.moveToOffset(elementAt.getTextRange.getEndOffset)
          }
          else {
            myEditor.getCaretModel.moveToOffset(myExprMarker.getEndOffset)
          }
        } else if (getTypeAliasDefinition != null) {
          val declaration = getTypeAliasDefinition
          myEditor.getCaretModel.moveToOffset(declaration.getTextRange.getEndOffset)
        }
      } else if (getTypeAliasDefinition != null && !UndoManager.getInstance(myProject).isUndoInProgress) {
        val revertInfo = myEditor.getUserData(ScalaIntroduceVariableHandler.REVERT_INFO)
        if (revertInfo != null) {
          extensions.inWriteAction {
            myEditor.getDocument.replaceString(0, myFile.getTextLength, revertInfo.fileText)
          }
          myEditor.getCaretModel.moveToOffset(revertInfo.caretOffset)
          myEditor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
      }
    }
    finally {
      import scala.collection.JavaConversions._
      for (occurrenceMarker <- getOccurrenceMarkers) {
        occurrenceMarker.dispose()
      }
      if (getExprMarker != null) getExprMarker.dispose()
    }
  }

  private def namedElement(declaration: PsiElement): Option[ScNamedElement] = declaration match {
    case typeAlias: ScTypeAliasDefinition => Some(typeAlias)
    case _ => None
  }

  override def finish(success: Boolean): Unit = {
    try {
      val named = namedElement(getTypeAliasDefinition).orNull
      val templateState: TemplateState = TemplateManagerImpl.getTemplateState(myEditor)
      if (named != null && templateState != null) {
        val occurrences = (for (i <- 0 to templateState.getSegmentsCount - 1) yield templateState.getSegmentRange(i)).toArray
        val validator = ScalaTypeValidator(new BalloonConflictsReporter(myEditor),
          myProject, myEditor, myFile, named, occurrences)
        validator.isOK(named.name, replaceAll)
      }
    }
    catch {
      //templateState can contain null private fields
      case exc: NullPointerException =>
    }
    finally {
      myEditor.getSelectionModel.removeSelection()
    }
    super.finish(success)
  }
}
