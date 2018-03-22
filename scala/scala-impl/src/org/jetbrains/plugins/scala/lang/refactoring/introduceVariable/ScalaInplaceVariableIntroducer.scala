package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceVariable

import java.awt._
import java.awt.event.{ActionEvent, ItemEvent}

import com.intellij.codeInsight.template.impl.{TemplateManagerImpl, TemplateState}
import com.intellij.openapi.application.{ApplicationManager, Result}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener}
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer
import com.intellij.ui.NonFocusableCheckBox
import javax.swing._
import javax.swing.event.HyperlinkEvent
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.{BalloonConflictsReporter, ScalaNamesUtil, ScalaVariableValidator, ValidationReporter}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.settings.annotations._
import org.jetbrains.plugins.scala.util._

import scala.collection.mutable

/**
  * Nikolay.Tropin
  * 6/3/13
  */

class ScalaInplaceVariableIntroducer(expr: ScExpression,
                                     maybeSelectedType: Option[ScType],
                                     namedElement: PsiNamedElement,
                                     replaceAll: Boolean,
                                     forceInferType: Boolean)
                                    (implicit project: Project, editor: Editor)
  extends InplaceVariableIntroducer[ScExpression](namedElement, editor, project, ScalaBundle.message("introduce.variable.title"), Array.empty, expr) {

  implicit def projectContext: ProjectContext = project

  import ScalaInplaceVariableIntroducer._

  private var myVarCheckbox: JCheckBox = _
  private var mySpecifyTypeChb: JCheckBox = _
  private var myDeclarationStartOffset: Int = 0
  private val newDeclaration = findDeclaration(namedElement)
  private var myCheckIdentifierListener: Option[DocumentListener] = None
  private val myFile: PsiFile = namedElement.getContainingFile
  private val myBalloonPanel: JPanel = new JPanel()
  private var nameIsValid: Boolean = true
  private val isEnumerator: Boolean = newDeclaration.exists(_.isInstanceOf[ScEnumerator])
  private val initialName = ScalaNamesUtil.scalaName(namedElement)

  private val myLabel = new JLabel()
  private val myLabelPanel = new JPanel()

  private val myChbPanel = new JPanel()
  private val typePanel = new JPanel()

  private val needTypeDefault: Boolean = needsTypeAnnotation(namedElement, expr, forceInferType)

  newDeclaration.foreach(setDeclaration)

  private def checkIdentifierListener(): DocumentListener = new DocumentListener {
    override def documentChanged(e: DocumentEvent): Unit = {
      commitDocument()
      val range = new TextRange(myCaretRangeMarker.getStartOffset, myCaretRangeMarker.getEndOffset)
      if (range.getLength == 0 && UndoManager.getInstance(myProject).isUndoInProgress) {}
      else {
        val input = myCaretRangeMarker.getDocument.getText(range)
        val numberOfSpaces = input.lastIndexOf(' ') + 1
        val maybeDeclaration = findDeclarationAt(range.getStartOffset + numberOfSpaces)

        val named: Option[ScNamedElement] = namedElement(maybeDeclaration)
        if (named.isDefined) {
          maybeDeclaration.foreach(setDeclaration)
          if (nameIsValid != (named.isDefined && isIdentifier(input.trim, myFile.getLanguage))) {
            nameIsValid = !nameIsValid
          }
          resetBalloonPanel(nameIsValid)
        } else {
          nameIsValid = false
          resetBalloonPanel(nameIsValid)
        }
      }
    }
  }

  private def namedElement(maybeDeclaration: Option[PsiElement]): Option[ScNamedElement] = maybeDeclaration.flatMap {
    case value: ScValue => value.declaredElements.headOption
    case variable: ScVariable => variable.declaredElements.headOption
    case enumerator: ScEnumerator => enumerator.pattern.bindings.headOption
    case _ => None
  }

  private def findDeclarationAt(offset: Int): Option[PsiElement] =
    findDeclaration(myFile.findElementAt(offset))

  private def getDeclaration: Option[PsiElement] = findDeclarationAt(myDeclarationStartOffset)

  private def setDeclaration(declaration: PsiElement): Unit = {
    myDeclarationStartOffset = declaration.getTextRange.getStartOffset
  }

  private def commitDocument(): Unit = {
    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument)
  }

  private def needInferType =
    if (mySpecifyTypeChb != null) mySpecifyTypeChb.isSelected
    else needTypeDefault

  override def getInitialName: String = initialName

  protected override def getComponent: JComponent = {
    if (!isEnumerator) {
      myVarCheckbox = new NonFocusableCheckBox(ScalaBundle.message("introduce.variable.declare.as.var"))
      myVarCheckbox.setMnemonic('v')
      myVarCheckbox.addActionListener((e: ActionEvent) => {
        val writeAction = new WriteCommandAction[Unit](myProject, getCommandName, getCommandName) {

          protected def run(result: Result[Unit]): Unit = {
            val asVar = myVarCheckbox.isSelected
            getDeclaration.collect {
              case value: ScValue if asVar => (value, createVarFromValDeclaration(value))
              case variable: ScVariableDefinition if !asVar => (variable, createValFromVarDefinition(variable))
            }.map {
              case (declaration, replacement) => declaration.replace(replacement)
            }.foreach(setDeclaration)

            commitDocument()
          }
        }
        writeAction.execute()
      })
    }

    maybeSelectedType
      .filterNot(_ => forceInferType)
      .foreach { selectedType =>
        mySpecifyTypeChb = new NonFocusableCheckBox(ScalaBundle.message("introduce.variable.specify.type.explicitly"))
        mySpecifyTypeChb.setSelected(needTypeDefault)
        mySpecifyTypeChb.setMnemonic('t')
        mySpecifyTypeChb.addItemListener((e: ItemEvent) => {
          val greedyToRight = mutable.WeakHashMap[RangeHighlighter, Boolean]()

          def setGreedyToRightToFalse(): Unit = {
            val highlighters: Array[RangeHighlighter] = myEditor.getMarkupModel.getAllHighlighters
            for (highlighter <- highlighters; if checkRange(highlighter.getStartOffset, highlighter.getEndOffset))
              greedyToRight += (highlighter -> highlighter.isGreedyToRight)
          }

          def resetGreedyToRightBack(): Unit = {
            val highlighters: Array[RangeHighlighter] = myEditor.getMarkupModel.getAllHighlighters
            for (highlighter <- highlighters; if checkRange(highlighter.getStartOffset, highlighter.getEndOffset))
              highlighter.setGreedyToRight(greedyToRight(highlighter))
          }

          def checkRange(start: Int, end: Int): Boolean = {
            val named: Option[ScNamedElement] = namedElement(getDeclaration)
            if (named.isDefined) {
              val nameRange = named.get.getNameIdentifier.getTextRange
              nameRange.getStartOffset == start && nameRange.getEndOffset <= end
            } else false
          }

          val writeAction = new WriteCommandAction[Unit](myProject, getCommandName, getCommandName) {
            private def addTypeAnnotation(selectedType: ScType): Unit = {
              getDeclaration.foreach {
                case declaration@(_: ScDeclaredElementsHolder | _: ScEnumerator) =>
                  val declarationCopy = declaration.copy.asInstanceOf[ScalaPsiElement]
                  val fakeDeclaration = createDeclaration(selectedType, "x", isVariable = false, "", isPresentableText = false)
                  val first = fakeDeclaration.findFirstChildByType(ScalaTokenTypes.tCOLON)
                  val last = fakeDeclaration.findFirstChildByType(ScalaTokenTypes.tASSIGN)
                  val assign = declarationCopy.findFirstChildByType(ScalaTokenTypes.tASSIGN)
                  declarationCopy.addRangeAfter(first, last, assign)
                  assign.delete()

                  val replaced = declaration.replace(declarationCopy)
                  ScalaPsiUtil.adjustTypes(replaced)
                  setDeclaration(replaced)
                  commitDocument()
                case _ =>
              }
            }

            private def removeTypeAnnotation(): Unit = {
              getDeclaration.foreach {
                case holder: ScDeclaredElementsHolder =>
                  val colon = holder.findFirstChildByType(ScalaTokenTypes.tCOLON)
                  val assign = holder.findFirstChildByType(ScalaTokenTypes.tASSIGN)
                  implicit val manager: PsiManager = myFile.getManager
                  val whiteSpace = createExpressionFromText("1 + 1").findElementAt(1)
                  val newWhiteSpace = holder.addBefore(whiteSpace, assign)
                  holder.getNode.removeRange(colon.getNode, newWhiteSpace.getNode)
                  setDeclaration(holder)
                  commitDocument()
                case enum: ScEnumerator if enum.pattern.isInstanceOf[ScTypedPattern] =>
                  val colon = enum.pattern.findFirstChildByType(ScalaTokenTypes.tCOLON)
                  enum.pattern.getNode.removeRange(colon.getNode, null)
                  setDeclaration(enum)
                  commitDocument()
                case _ =>
              }
            }

            protected def run(result: Result[Unit]): Unit = {
              commitDocument()
              setGreedyToRightToFalse()
              if (needInferType) {
                addTypeAnnotation(selectedType)
              } else {
                removeTypeAnnotation()
              }
            }
          }
          writeAction.execute()
          ApplicationManager.getApplication.runReadAction(new Runnable {
            def run(): Unit = {
              if (needTypeDefault) resetGreedyToRightBack()
            }
          })
        })
    }

    myCheckIdentifierListener = Some(checkIdentifierListener())
    myCheckIdentifierListener.foreach {
      myEditor.getDocument.addDocumentListener
    }

    setBalloonPanel(nameIsValid = true)
    myBalloonPanel
  }

  private def setUpTypePanel(): JPanel = {
    typePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0))

    if (mySpecifyTypeChb != null) {
      typePanel.add(mySpecifyTypeChb)

      val myLinkContainer = new JPanel
      myLinkContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0))
      typePanel.add(myLinkContainer)

      val link = TypeAnnotationUtil.createTypeAnnotationsHLink(project, ScalaBundle.message("default.ta.settings"))
      link.addHyperlinkListener((e: HyperlinkEvent) => {
        extensions.invokeLater {
          mySpecifyTypeChb.setSelected(needTypeDefault)
        }
      })

      myLinkContainer.add(link)
    }

    typePanel
  }

  private def setBalloonPanel(nameIsValid: Boolean): Unit = {
    this.nameIsValid = nameIsValid

    myChbPanel.setLayout(new VerticalFlowLayout)
    myChbPanel.setBorder(null)
    Seq(myVarCheckbox, setUpTypePanel()).filter(_ != null).foreach { chb =>
      myChbPanel.add(chb)
      chb.setEnabled(nameIsValid)
    }

    myLabel.setText(ScalaBundle.message("introduce.variable.identifier.is.not.valid"))
    myLabel.setForeground(Color.RED)
    myLabelPanel.setLayout(new BoxLayout(myLabelPanel, BoxLayout.X_AXIS))
    myLabelPanel.add(Box.createHorizontalGlue())
    myLabelPanel.add(myLabel)
    myLabelPanel.add(Box.createHorizontalGlue())

    if (!nameIsValid) myBalloonPanel add myLabelPanel
    else myBalloonPanel add myChbPanel
  }

  private def resetBalloonPanel(nameIsValid: Boolean): Unit = {
    def isBulkUpdate = myEditor.getDocument match {
      case docEx: DocumentEx => docEx.isInBulkUpdate
      case _ => false
    }

    if (myBalloon == null || myBalloon.isDisposed || isBulkUpdate) return
    if (!nameIsValid) {
      myBalloonPanel add myLabelPanel
      myBalloonPanel remove myChbPanel
    }
    else {
      myBalloonPanel add myChbPanel
      myBalloonPanel remove myLabelPanel
    }
    Seq(myVarCheckbox, mySpecifyTypeChb).filter(_ != null).foreach(_.setEnabled(nameIsValid))
    myBalloon.revalidate()
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
        } else
          getDeclaration.foreach { declaration =>
            myEditor.getCaretModel.moveToOffset(declaration.getTextRange.getEndOffset)
          }
      } else if (getDeclaration.isDefined && !UndoManager.getInstance(myProject).isUndoInProgress) {
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
      getOccurrenceMarkers.forEach { occurenceMarker =>
        occurenceMarker.dispose()
      }
      if (getExprMarker != null) getExprMarker.dispose()
    }
  }

  protected override def getReferencesSearchScope(file: VirtualFile): SearchScope = {
    new LocalSearchScope(myElementToRename.getContainingFile)
  }

  protected override def checkLocalScope(): PsiElement = {
    val scope = new LocalSearchScope(myElementToRename.getContainingFile)
    val elements: Array[PsiElement] = scope.getScope
    PsiTreeUtil.findCommonParent(elements: _*)
  }

  protected override def startRename: StartMarkAction = {
    StartMarkAction.start(myEditor, myProject, getCommandName)
  }

  override def finish(success: Boolean): Unit = {
    myCheckIdentifierListener.foreach {
      myEditor.getDocument.removeDocumentListener
    }

    if (mySpecifyTypeChb != null && !isEnumerator)
      ScalaApplicationSettings.getInstance.INTRODUCE_VARIABLE_EXPLICIT_TYPE = mySpecifyTypeChb.isSelected

    try {
      val named = namedElement(getDeclaration).orNull
      val templateState: TemplateState = TemplateManagerImpl.getTemplateState(myEditor)
      if (named != null && templateState != null) {
        val occurrences =
          (for (i <- 0 until templateState.getSegmentsCount) yield templateState.getSegmentRange(i)).toArray
        implicit val validator: ScalaVariableValidator = ScalaVariableValidator(myFile, named, occurrences)

        val reporter = new ValidationReporter(myProject, new BalloonConflictsReporter(myEditor))
        reporter.isOK(named.name, replaceAll)
      }
    }
    catch {
      //templateState can contain null private fields
      case _: NullPointerException =>
    }
    finally {
      myEditor.getSelectionModel.removeSelection()
    }
    super.finish(success)
  }
}

object ScalaInplaceVariableIntroducer {

  private[introduceVariable] def needsTypeAnnotation(anchor: PsiElement,
                                                     expression: ScExpression,
                                                     forceType: Boolean,
                                                     fromDialogMode: Boolean = false): Boolean =
    if (!forceType) {
      if (expression == null || !expression.isValid) false
      else if (fromDialogMode) ScalaApplicationSettings.getInstance.INTRODUCE_VARIABLE_EXPLICIT_TYPE
      else ScalaTypeAnnotationSettings(anchor.getProject).isTypeAnnotationRequiredFor(
        Declaration(Visibility.Default), Location(anchor), Some(Expression(expression)))
    } else true

  private def findDeclaration(element: PsiElement) =
    element.nonStrictParentOfType(Seq(classOf[ScEnumerator], classOf[ScDeclaredElementsHolder]))
}
