package org.jetbrains.plugins.scala
package lang.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
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
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentListener, DocumentAdapter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}

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
  var myPointer: SmartPsiElementPointer[ScDeclaredElementsHolder] = null
  private val newDeclaration = ScalaPsiUtil.getParentOfType(namedElement, classOf[ScDeclaredElementsHolder]).asInstanceOf[ScDeclaredElementsHolder]
  private var myCheckIdentifierListener: DocumentListener = null
  private val myFile: PsiFile = namedElement.getContainingFile
  private var myBalloonPanel: JPanel = null
  private val myLblNotValid: JLabel = new JLabel()

  setDeclaration(newDeclaration)
  myCheckIdentifierListener = checkIdentifierListener()

  private def checkIdentifierListener(): DocumentListener = new DocumentAdapter() {
    override def documentChanged(e: DocumentEvent) {
      val range = new TextRange(myCaretRangeMarker.getStartOffset, myCaretRangeMarker.getEndOffset)
      val input = myCaretRangeMarker.getDocument.getText(range)
      val element = myFile.findElementAt(range.getStartOffset)
      val declaration = ScalaPsiUtil.getParentOfType(element, classOf[ScDeclaredElementsHolder]).asInstanceOf[ScDeclaredElementsHolder]
      val named: Option[ScNamedElement] = namedElement(declaration)
      if (named.isDefined && range.contains(named.get.getNameIdentifier.getTextRange)) {
        setDeclaration(declaration)
        if (named.isEmpty || !isIdentifier(input, declaration.getLanguage) || named.get.name != input) {
          myVarCheckbox.setEnabled(false)
          mySpecifyTypeChb.setEnabled(false)
        } else {
          myVarCheckbox.setEnabled(true)
          mySpecifyTypeChb.setEnabled(true)
        }
      }
      super.documentChanged(e)
    }
  }

  private def namedElement(declaration: ScDeclaredElementsHolder): Option[ScNamedElement] = declaration match {
    case v: ScValue => v.declaredElements.headOption
    case v: ScVariable => v.declaredElements.headOption
    case _ => None
  }

  def getDeclaration: ScDeclaredElementsHolder = myPointer.getElement

  def setDeclaration(declaration: ScDeclaredElementsHolder) {
    myPointer = if (declaration != null) SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declaration)
    else myPointer
  }

  private def commitDocument() {
    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument)
  }

  override def getComponent: JComponent = {

    myVarCheckbox = new NonFocusableCheckBox(ScalaBundle.message("introduce.variable.declare.as.var"))
    val withVarSetting = ScalaApplicationSettings.getInstance.INTRODUCE_LOCAL_CREATE_VARIABLE
    if (withVarSetting != null) {
      myVarCheckbox.setSelected(withVarSetting)
    }
    myVarCheckbox.setMnemonic('v')
    myVarCheckbox.addActionListener(new ActionListener {
      def actionPerformed(e: ActionEvent) {
        val writeAction = new WriteCommandAction[Unit](myProject, getCommandName, getCommandName) {

          private def changeValOrVar(asVar: Boolean, declaration: ScDeclaredElementsHolder) {
            val replacement = declaration match {
              case value: ScValue if asVar =>
                ScalaPsiElementFactory.createVarFromValDeclaration(value, value.getManager)
              case variable: ScVariableDefinition if !asVar =>
                ScalaPsiElementFactory.createValFromVarDefinition(variable, variable.getManager)
            }
            setDeclaration(declaration.replace(replacement).asInstanceOf[ScDeclaredElementsHolder])
          }

          protected def run(result: Result[Unit]) {
            changeValOrVar(myVarCheckbox.isSelected, getDeclaration)
            commitDocument()
          }
        }
        writeAction.execute()
      }
    })

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
          def checkRange(start: Int, end: Int, declaration: ScDeclaredElementsHolder): Boolean = {
            val named: Option[ScNamedElement] = namedElement(declaration)
            if (named.isDefined) {
              val nameRange = named.get.getNameIdentifier.getTextRange
              nameRange.getStartOffset == start && nameRange.getEndOffset <= end
            } else false
          }

          val writeAction = new WriteCommandAction[Unit](myProject, getCommandName, getCommandName) {

            private def addTypeAnnotation(selectedType: ScType) {
              val declarationCopy = getDeclaration.copy.asInstanceOf[ScDeclaredElementsHolder]
              val manager = declarationCopy.getManager
              val fakeDeclaration =
                ScalaPsiElementFactory.createDeclaration(selectedType, "x", isVariable = false, "", manager, isPresentableText = true)
              val first = fakeDeclaration.findFirstChildByType(ScalaTokenTypes.tCOLON)
              val last = fakeDeclaration.findFirstChildByType(ScalaTokenTypes.tASSIGN)
              val assign = declarationCopy.findFirstChildByType(ScalaTokenTypes.tASSIGN)
              declarationCopy.addRangeAfter(first, last, assign)
              assign.delete()
              val replaced = getDeclaration.replace(declarationCopy).asInstanceOf[ScDeclaredElementsHolder]
              setDeclaration(replaced)
              commitDocument()
            }

            private def removeTypeAnnotation(declaration: ScDeclaredElementsHolder) {
              val declarationCopy = getDeclaration.copy.asInstanceOf[ScDeclaredElementsHolder]
              val first = declarationCopy.findFirstChildByType(ScalaTokenTypes.tCOLON)
              val assign: PsiElement = declarationCopy.findFirstChildByType(ScalaTokenTypes.tASSIGN)
              val whiteSpace = ScalaPsiElementFactory.createExpressionFromText("1 + 1", declaration.getManager).findElementAt(1)
              declarationCopy.addBefore(whiteSpace, first)
              declarationCopy.deleteChildRange(first, assign.getPrevSibling)
              val replaced = getDeclaration.replace(declarationCopy).asInstanceOf[ScDeclaredElementsHolder]
              setDeclaration(replaced)
              commitDocument()
            }

            protected def run(result: Result[Unit]) {
              commitDocument()
              if (mySpecifyTypeChb.isSelected) {
                setGreedyToRightToFalse()
                addTypeAnnotation(selectedType)
                val templateState: TemplateState = TemplateManagerImpl.getTemplateState(myEditor)
                if (templateState != null) templateState.doReformat(getDeclaration.getTextRange)
              } else {
                removeTypeAnnotation(getDeclaration)
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

    myBalloonPanel = new JPanel(new GridBagLayout)
    myBalloonPanel.setBorder(null)
   /* val label: JLabel = new JLabel("Identifier is not valid")
    myBalloon.add(label)*/
    var count: Int = 1
    if (myVarCheckbox != null) {
      myBalloonPanel.add(myVarCheckbox,
        new GridBagConstraints(0, count, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0))
      count += 1
    }
    if (mySpecifyTypeChb != null) {
      myBalloonPanel.add(mySpecifyTypeChb,
        new GridBagConstraints(0, count, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0))
      count += 1
    }
    myBalloonPanel.add(Box.createVerticalBox,
      new GridBagConstraints(0, count, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0))

    editor.getDocument.addDocumentListener(myCheckIdentifierListener)

    myBalloonPanel
  }

  protected override def moveOffsetAfter(success: Boolean) {
    if (!replaceAll || myExprMarker == null) {
      myEditor.getCaretModel.moveToOffset(getDeclaration.getTextRange.getEndOffset)
    }
    else {
      val startOffset: Int = myExprMarker.getStartOffset
      val file: PsiFile = getDeclaration.getContainingFile
      val elementAt: PsiElement = file.findElementAt(startOffset)
      if (elementAt != null) {
        myEditor.getCaretModel.moveToOffset(elementAt.getTextRange.getEndOffset)
      }
      else {
        myEditor.getCaretModel.moveToOffset(myExprMarker.getEndOffset)
      }
    }
  }

  private def createWarningBalloon(message: String) {
    SwingUtilities invokeLater new Runnable {
      def run {
        val popupFactory = JBPopupFactory.getInstance
        val bestLocation = popupFactory.guessBestPopupLocation(myEditor)
        popupFactory.createHtmlTextBalloonBuilder(message, null, MessageType.WARNING.getPopupBackground, null).
                setFadeoutTime(-1).setShowCallout(false).
                createBalloon.show(bestLocation, Balloon.Position.below)
      }
    }
  }

  override def finish(success: Boolean) {

    ScalaApplicationSettings.getInstance.INTRODUCE_LOCAL_CREATE_VARIABLE = myVarCheckbox.isSelected
    ScalaApplicationSettings.getInstance.SPECIFY_TYPE_EXPLICITLY = mySpecifyTypeChb.isSelected

    /*val declaration = getDeclaration
    val named = namedElement(getDeclaration).getOrElse(null)
    if (named != null) {
      val templateState: TemplateState = TemplateManagerImpl.getTemplateState(myEditor)
      val occurrences = (for (i <- 0 to templateState.getSegmentsCount - 1) yield templateState.getSegmentRange(i)).toArray

      val validator = ScalaVariableValidator(new ConflictsReporter {
        def reportConflicts(conflicts: Array[String], project: Project): Boolean = {
          createWarningBalloon(conflicts.toSet.mkString("\n"))
          true //this means that we do nothing, only show balloon
        }
      }, project, editor, declaration.getContainingFile, named, occurrences)
      validator.isOK(named.name, replaceAll)
    }*/
    editor.getDocument.removeDocumentListener(myCheckIdentifierListener)

    super.finish(success)
  }
}
