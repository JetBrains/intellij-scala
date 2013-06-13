package org.jetbrains.plugins.scala
package lang.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScExpression}
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer
import javax.swing._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.ui.NonFocusableCheckBox
import java.awt.event.{ActionEvent, ActionListener}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.application.{ApplicationManager, Result}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import java.awt._
import com.intellij.codeInsight.template.impl.{TemplateManagerImpl, TemplateState}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import com.intellij.openapi.editor.markup.RangeHighlighter
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener, DocumentAdapter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScalaVariableValidator, ConflictsReporter}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern

/**
 * Nikolay.Tropin
 * 6/3/13
 */

class ScalaInplaceVariableIntroducer(project: Project,
                                     editor: Editor,
                                     expr: ScExpression,
                                     types: Array[ScType],
                                     namedElement: PsiNamedElement,
                                     occurences: Array[ScExpression],
                                     title: String,
                                     replaceAll: Boolean,
                                     asVar: Boolean,
                                     noTypeInference: Boolean)
        extends InplaceVariableIntroducer[ScExpression](namedElement, editor, project, title, occurences, expr) {

  private var myVarCheckbox: JCheckBox = null
  private var mySpecifyTypeChb: JCheckBox = null
  var myPointer: SmartPsiElementPointer[PsiElement] = null
  private val newDeclaration = ScalaPsiUtil.getParentOfType(namedElement, classOf[ScEnumerator], classOf[ScDeclaredElementsHolder])
  private var myCheckIdentifierListener: DocumentListener = null
  private val myFile: PsiFile = namedElement.getContainingFile
  private var myBalloonPanel: JPanel = null
  private var nameIsValid: Boolean = true

  private val myLabel: JLabel = new JLabel()

  setDeclaration(newDeclaration)
  myCheckIdentifierListener = checkIdentifierListener()

  private def checkIdentifierListener(): DocumentListener = new DocumentAdapter() {
    override def documentChanged(e: DocumentEvent) {
      commitDocument()
      val range = new TextRange(myCaretRangeMarker.getStartOffset, myCaretRangeMarker.getEndOffset)
      val input = myCaretRangeMarker.getDocument.getText(range)
      val numberOfSpaces = input.lastIndexOf(' ') + 1
      val inputTrimmed = input.trim
      val element = myFile.findElementAt(range.getStartOffset + numberOfSpaces)
      val declaration = ScalaPsiUtil.getParentOfType(element, classOf[ScEnumerator], classOf[ScDeclaredElementsHolder])
      val named: Option[ScNamedElement] = namedElement(declaration)
      if (named.isDefined && range.contains(named.get.getNameIdentifier.getTextRange)) {
        setDeclaration(declaration)
        if (nameIsValid != (named.isDefined && isIdentifier(inputTrimmed, myFile.getLanguage) && named.get.name == inputTrimmed)) {
          nameIsValid = !nameIsValid
          resetBalloonPanel(nameIsValid)
        }
      } else {
        nameIsValid = false
        resetBalloonPanel(nameIsValid)
      }
      super.documentChanged(e)
    }
  }

  private def namedElement(declaration: PsiElement): Option[ScNamedElement] = declaration match {
    case value: ScValue => value.declaredElements.headOption
    case variable: ScVariable => variable.declaredElements.headOption
    case enumerator: ScEnumerator => enumerator.pattern.bindings.headOption
    case _ => None
  }

  def getDeclaration: PsiElement = myPointer.getElement

  def setDeclaration(declaration: PsiElement) {
    myPointer = if (declaration != null) SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declaration)
    else myPointer
  }

  private def commitDocument() {
    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument)
  }

  override def getComponent: JComponent = {

    if (getDeclaration.isInstanceOf[ScDeclaredElementsHolder]) {
      myVarCheckbox = new NonFocusableCheckBox(ScalaBundle.message("introduce.variable.declare.as.var"))
      val withVarSetting = ScalaApplicationSettings.getInstance.INTRODUCE_LOCAL_CREATE_VARIABLE
      if (withVarSetting != null) {
        myVarCheckbox.setSelected(withVarSetting)
      }
      myVarCheckbox.setMnemonic('v')
      myVarCheckbox.addActionListener(new ActionListener {
        def actionPerformed(e: ActionEvent) {
          val writeAction = new WriteCommandAction[Unit](myProject, getCommandName, getCommandName) {

            private def changeValOrVar(asVar: Boolean, declaration: PsiElement) {
              val replacement =
                declaration match {
                case value: ScValue if asVar =>
                  ScalaPsiElementFactory.createVarFromValDeclaration(value, value.getManager)
                case variable: ScVariableDefinition if !asVar =>
                  ScalaPsiElementFactory.createValFromVarDefinition(variable, variable.getManager)
                case _ => declaration
              }
              if (replacement != declaration) setDeclaration(declaration.replace(replacement))
            }

            protected def run(result: Result[Unit]) {
              changeValOrVar(myVarCheckbox.isSelected, getDeclaration)
              commitDocument()
            }
          }
          writeAction.execute()
        }
      })
    }

    if (types.nonEmpty && !noTypeInference) {
      val selectedType = types(0)
      mySpecifyTypeChb = new NonFocusableCheckBox(ScalaBundle.message("introduce.variable.specify.type.explicitly"))
      val specifyTypeSetting = ScalaApplicationSettings.getInstance.SPECIFY_TYPE_EXPLICITLY
      if (specifyTypeSetting != null) {
        mySpecifyTypeChb.setSelected(specifyTypeSetting)
      } else {
        mySpecifyTypeChb.setSelected(true)
      }
      mySpecifyTypeChb.setMnemonic('t')
      mySpecifyTypeChb.addActionListener(new ActionListener {
        def actionPerformed(e: ActionEvent) {
          val greedyToRight = mutable.WeakHashMap[RangeHighlighter, Boolean]()

          def setGreedyToRightToFalse() {
            val highlighters: Array[RangeHighlighter] = myEditor.getMarkupModel.getAllHighlighters
            for (highlighter <- highlighters; if checkRange(highlighter.getStartOffset, highlighter.getEndOffset, getDeclaration))
              greedyToRight += (highlighter -> highlighter.isGreedyToRight)
          }
          def resetGreedyToRightBack() {
            val highlighters: Array[RangeHighlighter] = myEditor.getMarkupModel.getAllHighlighters
            for (highlighter <- highlighters; if checkRange(highlighter.getStartOffset, highlighter.getEndOffset, getDeclaration))
              highlighter.setGreedyToRight(greedyToRight(highlighter))
          }
          def checkRange(start: Int, end: Int, declaration: PsiElement): Boolean = {
            val named: Option[ScNamedElement] = namedElement(declaration)
            if (named.isDefined) {
              val nameRange = named.get.getNameIdentifier.getTextRange
              nameRange.getStartOffset == start && nameRange.getEndOffset <= end
            } else false
          }

          val writeAction = new WriteCommandAction[Unit](myProject, getCommandName, getCommandName) {
            private def addTypeAnnotation(selectedType: ScType) {
              getDeclaration match {
                case _: ScDeclaredElementsHolder | _: ScEnumerator =>
                  val declarationCopy = getDeclaration.copy.asInstanceOf[ScalaPsiElement]
                  val manager = declarationCopy.getManager
                  val fakeDeclaration =
                    ScalaPsiElementFactory.createDeclaration(selectedType, "x", isVariable = false, "", manager, isPresentableText = true)
                  val first = fakeDeclaration.findFirstChildByType(ScalaTokenTypes.tCOLON)
                  val last = fakeDeclaration.findFirstChildByType(ScalaTokenTypes.tASSIGN)
                  val assign = declarationCopy.findFirstChildByType(ScalaTokenTypes.tASSIGN)
                  declarationCopy.addRangeAfter(first, last, assign)
                  assign.delete()
                  val replaced = getDeclaration.replace(declarationCopy)
                  setDeclaration(replaced)
                  commitDocument()
                case _ =>
              }
            }
            private def removeTypeAnnotation() {
              getDeclaration match {
                case holder: ScDeclaredElementsHolder =>
                  val colon = holder.findFirstChildByType(ScalaTokenTypes.tCOLON)
                  val assign = holder.findFirstChildByType(ScalaTokenTypes.tASSIGN)
                  val whiteSpace = ScalaPsiElementFactory.createExpressionFromText("1 + 1", myFile.getManager).findElementAt(1)
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
            protected def run(result: Result[Unit]) {
              commitDocument()
              setGreedyToRightToFalse()
              if (mySpecifyTypeChb.isSelected) {
                addTypeAnnotation(selectedType)
              } else {
                removeTypeAnnotation()
              }
            }
          }
          writeAction.execute()
          ApplicationManager.getApplication.runReadAction(new Runnable {
            def run() {
              if (mySpecifyTypeChb.isSelected) {
                resetGreedyToRightBack()
              }
            }
          })
        }
      })
    }

    editor.getDocument.addDocumentListener(myCheckIdentifierListener)

    setBalloonPanel(nameIsValid = true)
    myBalloonPanel
  }

  private def setBalloonPanel(nameIsValid: Boolean) {
    this.nameIsValid = nameIsValid
    myBalloonPanel = new JPanel(new GridBagLayout)
    myBalloonPanel.setBorder(null)
    myBalloonPanel.add(myLabel)
    if (!nameIsValid) myLabel.setText(ScalaBundle.message("introduce.variable.identifier.is.not.valid"))
    else myLabel.setText(" ")

    var count: Int = 1
    Seq(myVarCheckbox, mySpecifyTypeChb).filter(_ != null).foreach{chb =>
      myBalloonPanel.add(chb,
        new GridBagConstraints(0, count, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0))
      count += 1
      chb.setEnabled(nameIsValid)
    }
    myBalloonPanel.add(Box.createVerticalBox,
      new GridBagConstraints(0, count, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0))

    myBalloonPanel
  }

  private def resetBalloonPanel(nameIsValid: Boolean) {
    if (!nameIsValid) myLabel.setText(ScalaBundle.message("introduce.variable.identifier.is.not.valid"))
    else myLabel.setText(" ")
    Seq(myVarCheckbox, mySpecifyTypeChb) filter(_ != null) foreach (_.setEnabled(nameIsValid))
  }

  protected override def moveOffsetAfter(success: Boolean) {
    if (myExprMarker != null) {
      val startOffset: Int = myExprMarker.getStartOffset
      val elementAt: PsiElement = myFile.findElementAt(startOffset)
      if (elementAt != null) {
        myEditor.getCaretModel.moveToOffset(elementAt.getTextRange.getEndOffset)
      }
      else {
        myEditor.getCaretModel.moveToOffset(myExprMarker.getEndOffset)
      }
    } else if (getDeclaration != null) {
      myEditor.getCaretModel.moveToOffset(getDeclaration.getTextRange.getEndOffset)
    }
  }

  private def createWarningBalloon(message: String) {
    SwingUtilities invokeLater new Runnable {
      def run() {
        val popupFactory = JBPopupFactory.getInstance
        val bestLocation = popupFactory.guessBestPopupLocation(myEditor)
        popupFactory.createHtmlTextBalloonBuilder(message, null, MessageType.WARNING.getPopupBackground, null).
                setFadeoutTime(-1).setShowCallout(false).
                createBalloon.show(bestLocation, Balloon.Position.below)
      }
    }
  }

  override def finish(success: Boolean) {
    editor.getDocument.removeDocumentListener(myCheckIdentifierListener)

    if (myVarCheckbox != null) ScalaApplicationSettings.getInstance.INTRODUCE_LOCAL_CREATE_VARIABLE = myVarCheckbox.isSelected
    if (mySpecifyTypeChb != null) ScalaApplicationSettings.getInstance.SPECIFY_TYPE_EXPLICITLY = mySpecifyTypeChb.isSelected

    val named = namedElement(getDeclaration).getOrElse(null)
    val templateState: TemplateState = TemplateManagerImpl.getTemplateState(myEditor)
    if (named != null && templateState != null) {
      val occurrences = (for (i <- 0 to templateState.getSegmentsCount - 1) yield templateState.getSegmentRange(i)).toArray
      val validator = ScalaVariableValidator(new ConflictsReporter {
        def reportConflicts(conflicts: Array[String], project: Project): Boolean = {
          createWarningBalloon(conflicts.toSet.mkString("\n"))
          true //this means that we do nothing, only show balloon
        }
      }, project, editor, myFile, named, occurrences)
      validator.isOK(named.name, replaceAll)
    }
    super.finish(success)
  }
}
