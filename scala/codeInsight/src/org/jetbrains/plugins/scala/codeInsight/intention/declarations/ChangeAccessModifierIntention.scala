package org.jetbrains.plugins.scala.codeInsight
package intention
package declarations

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.openapi.application.{ApplicationManager, ReadAction}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.{FinishMarkAction, StartMarkAction}
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager, EditorFontType}
import com.intellij.openapi.editor.markup.{HighlighterLayer, HighlighterTargetArea}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.{JBPopupFactory, JBPopupListener, LightweightWindowEvent}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import javax.swing.ListSelectionModel
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInsight.intention.declarations.ChangeAccessModifierIntention._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaChangeSignatureProcessor, ScalaParameterInfo}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

import scala.jdk.CollectionConverters._

class ChangeAccessModifierIntention extends BaseElementAtCaretIntentionAction {
  private def getMemberDescription(member: ScMember): Option[String] = member match {
    case idOwner: PsiNameIdentifierOwner => Option(idOwner.getNameIdentifier).map(_.getText)
    case holder: ScDeclaredElementsHolder if holder.declaredNames.nonEmpty => Some(holder.declaredNames.mkString(", "))
    case pattern: ScPatternDefinition => Some(pattern.pList.getText)
    case _ => None
  }

  private var targetModifier: Option[String] = None

  private def findMember(elem: PsiElement): Option[ScMember] = {
    def isAssign(psiElement: PsiElement): Boolean =
      psiElement.elementType == ScalaTokenTypes.tASSIGN

    elem.withParents
      .takeWhile {
        case _: PsiFile => false
        case _: ScExpression => false
        case _: ScTemplateBody => false
        case _: PsiComment => false
        case _ => true
      }
      .takeWhile {
        e =>
          // never show the intention if the caret is left of an =
          !isAssign(e) && !e.getPrevSiblingNotWhitespaceComment.toOption.exists(isAssign)
      }
      .collectFirst { case member: ScMember => member}
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val availableOpt = for {
      member <- findMember(element)
      description <- getMemberDescription(member)
    } yield {
      val available = availableModifiers(member)
      available match {
        case Seq(one) =>
          targetModifier = Some(one)
          setText(ScalaCodeInsightBundle.message("make.0.1", description, one))
        case _ =>
          targetModifier = None
          setText(actionName)
      }
      available
    }

    availableOpt.exists(_.nonEmpty)
  }

  override def getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

  override def startInWriteAction = false

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    for {
      member <- findMember(element)
      file <- member.containingFile
    } {
      val possibleModifiers = availableModifiers(member)

      targetModifier match {
        case Some(targetModifier) if possibleModifiers.contains(targetModifier) =>
          directlySetModifier(member, targetModifier)
        case _ =>
          startUpdatingModifier(member, possibleModifiers, file, project, editor)
      }
    }

  private def directlySetModifier(member: ScMember, modifier: String): Unit = {
    val modifierList = member.getModifierList
    val conflicts = checkForConflicts(member, modifier)
    conflicts.foreach(processWithConflicts(modifierList, modifier, _))
  }

  private def startUpdatingModifier(member: ScMember, possibleModifiers: Seq[String], file: PsiFile, project: Project, editor: Editor): Unit = {
    val modifierList = member.getModifierList

    val document = editor.getDocument
    val modifierRange = modifierList
        .accessModifier
        .fold(TextRange.from(modifierList.getTextRange.getStartOffset, 0))(_.getTextRange)
    val model = editor.getCaretModel
    val curCursorPos = model.getOffset
    val originalCursorPositionMarker = document.createRangeMarker(curCursorPos, curCursorPos)
    model.moveToOffset(modifierRange.getStartOffset)

    val markAction =
      try StartMarkAction.start(editor, project, actionName)
      catch {
        case e: StartMarkAction.AlreadyStartedException =>
          //noinspection ReferencePassedToNls
          Messages.showErrorDialog(project, e.getMessage, StringUtil.toTitleCase(actionName))
          return
      }

    val lvAttr = EditorColorsManager.getInstance.getGlobalScheme.getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES)
    val highlighter = editor.getMarkupModel.addRangeHighlighter(
      modifierRange.getStartOffset, modifierRange.getEndOffset,
      HighlighterLayer.LAST + 1, lvAttr, HighlighterTargetArea.EXACT_RANGE
    )
    highlighter.setGreedyToRight(true)
    highlighter.setGreedyToLeft(true)
    val updater = new ModifierTextUpdater(file, document, modifierRange, actionName)
    val memberPointer = SmartPointerManager.createPointer(member)
    JBPopupFactory.getInstance
      .createPopupChooserBuilder(possibleModifiers.asJava)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setSelectedValue(possibleModifiers.head, true)
      .setAccessibleName(ScalaCodeInsightBundle.message("title.change.modifier"))
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setFont(editor.getColorsScheme.getFont(EditorFontType.PLAIN))
      .setItemSelectedCallback(updater.setModifier)
      .addListener(new JBPopupListener() {
        override def onClosed(event: LightweightWindowEvent): Unit = {
          highlighter.dispose()
          model.moveToOffset(originalCursorPositionMarker.getStartOffset)
          if (!event.isOk) {
            FinishMarkAction.finish(project, editor, markAction)
            updater.undoChange(true)
          }
        }
      })
      .setNamerForFiltering(_.toString)
      .setItemChosenCallback(modifier => {
        updater.undoChange(false)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        updater.setModifier(modifier)
        // do not commit document now: checkForConflicts should have original content
        // while the editor should display the updated content to prevent flicker
        try {
          for {
            m <- Option(memberPointer.getElement)
            modifierList <- Option(m.getModifierList)
          } {
            checkForConflicts(member, modifier) match {
              case None =>
                updater.undoChange(true)
              case Some(conflicts) =>
                if (conflicts.nonEmpty) {
                  updater.undoChange(true)
                  PsiDocumentManager.getInstance(project).commitDocument(document)
                  processWithConflicts(modifierList, modifier, conflicts)
                } else {
                  updater.undoChange(false)
                  PsiDocumentManager.getInstance(project).commitDocument(document)
                  changeModifier(modifierList, modifier, hasConflicts = false)
                }
            }
          }
        } finally {
          FinishMarkAction.finish(project, editor, markAction)
        }
      })
      .createPopup()
      .showInBestPositionFor(editor)
  }

  override def getFamilyName: String = ChangeAccessModifierIntention.familyName

  private def processWithConflicts(modifierList: PsiModifierList, modifier: String, conflicts: Map[PsiElement, String]): Unit = {
    if (conflicts.nonEmpty) {
      if (ApplicationManager.getApplication.isUnitTestMode && !BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore)
        throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values.toSeq.asJava)

      if (!confirmInConflictsDialog(modifierList, modifier, conflicts))
        return
    }

    changeModifier(modifierList, modifier, conflicts.nonEmpty)
  }

  private def confirmInConflictsDialog(modifierList: PsiModifierList, modifier: String, conflicts: Map[PsiElement, String]): Boolean = {
    val conflictMap = new MultiMap[PsiElement, String]()
    conflictMap.putAllValues(conflicts.asJava)
    val dialog = new ConflictsDialog(modifierList.getProject, conflictMap, () => changeModifier(modifierList, modifier, hasConflicts = true))
    dialog.showAndGet
  }

  private def changeModifier(modifierList: PsiModifierList, modifier: String, hasConflicts: Boolean): Unit = {
    implicit val project: Project = modifierList.getProject
    val parent = modifierList.getParent
    parent match {
      case method: ScMethodLike if hasConflicts =>
        val changeInfo = ScalaChangeInfo(
          newVisibility = modifier,
          function = method,
          newName = method.name,
          newType =  method.getReturnType.toScType(),
          newParams =  ScalaParameterInfo.allForMethod(method),
          isAddDefaultArgs = false
        )
        val processor = new ScalaChangeSignatureProcessor(changeInfo)
        processor.run()

      case _ =>
        val file = modifierList.getContainingFile
        WriteCommandAction
          .writeCommandAction(file.getProject, file)
          .withName(ScalaCodeInsightBundle.message("change.access.modifier.intention"))
          .run(() => {
            modifierList.setModifierProperty(modifier, true)
            val whitespace = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(" ")
            val sibling = modifierList.getNextSibling
            if (sibling.isInstanceOf[PsiWhiteSpace]) {
              sibling.replace(whitespace)
              CodeStyleManager
                .getInstance(project)
                .reformatRange(parent, modifierList.getTextOffset, modifierList.getNextSibling.getTextOffset)
            }
          })
    }
  }

  private def checkForConflicts(member: ScMember, modifier: String): Option[Map[PsiElement, String]] = {
    implicit val project: Project = member.getProject
    var conflicts = Map.empty[PsiElement, String]
    val modifierList = member.getModifierList
    if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.PRIVATE)) return Some(conflicts)
    val newModifierList = ScalaPsiElementFactory
      .createScalaFileFromText(s"$modifier class a").typeDefinitions.head.getModifierList
    val useScope = member.getUseScope

    def getElementsToSearch(member: ScMember): collection.Seq[PsiElement] = member match {
      case holder: ScDeclaredElementsHolder => holder.declaredElements
      case _ => Seq(member)
    }

    val canceled = !ProgressManager.getInstance.runProcessWithProgressSynchronously((() =>
      ReadAction.run(() => {
        for (declaredElement <- getElementsToSearch(member)) {
          val search = ReferencesSearch.search(declaredElement, useScope)
          search.asScala.foreach { (reference: PsiReference) =>
            val referencedElement = reference.getElement
            if (!ResolveUtils.isAccessibleWithNewModifiers(member, referencedElement, newModifierList)) {
              val context = PsiTreeUtil.getParentOfType(referencedElement, classOf[PsiMethod], classOf[PsiField], classOf[PsiClass], classOf[PsiFile])
              if (context != null) {
                val message = ScalaCodeInsightBundle.message(
                  "0.with.1.visibility.is.not.accessible.from.2",
                  RefactoringUIUtil.getDescription(declaredElement, false),
                  modifier,
                  RefactoringUIUtil.getDescription(context, true)
                )
                conflicts += (referencedElement -> message)
              }
            }
          }
        }
      })): Runnable,
      ScalaCodeInsightBundle.message("detecting.possible.conflicts"), true, project)

    if (canceled) None
    else Some(conflicts)
  }
}

object ChangeAccessModifierIntention {
  val actionName: String =
    ScalaCodeInsightBundle.message("change.access.modifier.intention")

  // we cannot have the same familyName as the java equivalent
  // so for now we just use the same name but with an s at the end to make modifier plural
  // for text that is shown when pressing alt+enter we still use the singular variant (because plural makes no sense there)
  // use `actionName` for that.
  val familyName: String =
    ScalaCodeInsightBundle.message("change.access.modifier.intention.family")

  private def availableModifiers(member: ScMember): Seq[String] = {
    if (member.isLocal)
      return Seq.empty

    val modifierSeq = Seq.newBuilder[String]

    val accessModifier = member.getModifierList.accessModifier

    if (accessModifier.nonEmpty)
      modifierSeq += "public"
    val containingClass = member.containingClass
    if (containingClass != null && !containingClass.is[ScObject] && !accessModifier.exists(_.isProtected))
      modifierSeq += "protected"
    if (!accessModifier.exists(_.isPrivate))
      modifierSeq += "private"

    // TODO: support the more sophisticated scala access scopes
    /*if (member.containingClass != null) {
      modifierSeq += "private[this]"
      modifierSeq += "protected[this]"
    }

    for (qualifiedName <- member.qualifiedNameOpt) {
      val parts = qualifiedName.split('.').init

      for (part <- parts.reverse.distinct) {
        modifierSeq += s"private[$part]"
        modifierSeq += s"protected[$part]"
      }
    }*/

    modifierSeq.result()
  }

  private class ModifierTextUpdater(val myFile: PsiFile, val myDocument: Document, val range: TextRange, @Nls val myActionName: String) {
    private val sequence: CharSequence = myDocument.getCharsSequence
    private val myExtendLeft = range.getStartOffset > 0 && !StringUtil.isWhiteSpace(sequence.charAt(range.getStartOffset - 1))
    private val myExtendRight = range.getEndOffset < sequence.length && !StringUtil.isWhiteSpace(sequence.charAt(range.getEndOffset))
    private val myOriginalText = sequence.subSequence(range.getStartOffset, range.getEndOffset).toString
    private val myMarker = {
      val marker = myDocument.createRangeMarker(range)
      marker.setGreedyToRight(true)
      marker.setGreedyToLeft(true)
      marker
    }

    def undoChange(viaUndoManager: Boolean): Unit = {
      val project = myFile.getProject
      val fileEditorManager = FileEditorManager.getInstance(project)
      val fileEditor = fileEditorManager.getSelectedEditor(myFile.getVirtualFile)
      val manager = UndoManager.getInstance(project)
      if (viaUndoManager && manager.isUndoAvailable(fileEditor)) manager.undo(fileEditor)
      else WriteCommandAction.writeCommandAction(project, myFile).withName(myActionName).run(() => myDocument.replaceString(myMarker.getStartOffset, myMarker.getEndOffset, myOriginalText))
    }

    def setModifier(target: String): Unit = {
      if (target == null) return
      var updatedText = target

      if (myExtendRight)
        updatedText = updatedText + " "

      if (myExtendLeft)
        updatedText = " " + updatedText

      if (target == "public")
        updatedText = ""

      WriteCommandAction.writeCommandAction(myFile.getProject, myFile)
        .withName(myActionName)
        .run(() => myDocument.replaceString(myMarker.getStartOffset, myMarker.getEndOffset, updatedText))
    }
  }
}
