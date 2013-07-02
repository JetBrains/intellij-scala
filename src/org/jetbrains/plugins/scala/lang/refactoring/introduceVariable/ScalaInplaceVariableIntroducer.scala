package org.jetbrains.plugins.scala
package lang.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScExpression}
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.{ScrollType, Editor}
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
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScalaRefactoringUtil, ScalaVariableValidator, ConflictsReporter}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypedPattern
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.openapi.command.undo.UndoManager

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
  private var myPointer: SmartPsiElementPointer[PsiElement] = null
  private val newDeclaration = ScalaPsiUtil.getParentOfType(namedElement, classOf[ScEnumerator], classOf[ScDeclaredElementsHolder])
  private var myCheckIdentifierListener: DocumentListener = null
  private val myFile: PsiFile = namedElement.getContainingFile
  private val myBalloonPanel: JPanel = new JPanel()
  private var nameIsValid: Boolean = true
  private val isEnumerator: Boolean = newDeclaration.isInstanceOf[ScEnumerator]

  private val myLabel = new JLabel()
  private val myLabelPanel = new JPanel()

  private val myChbPanel = new JPanel()

  setDeclaration(newDeclaration)
  myCheckIdentifierListener = checkIdentifierListener()

  private def checkIdentifierListener(): DocumentListener = new DocumentAdapter() {
    override def documentChanged(e: DocumentEvent): Unit = {
      commitDocument()
      val range = new TextRange(myCaretRangeMarker.getStartOffset, myCaretRangeMarker.getEndOffset)
      if (range.getLength == 0 && UndoManager.getInstance(myProject).isUndoInProgress) {}
      else {
        val input = myCaretRangeMarker.getDocument.getText(range)
        val numberOfSpaces = input.lastIndexOf(' ') + 1
        val element = myFile.findElementAt(range.getStartOffset + numberOfSpaces)
        val declaration = ScalaPsiUtil.getParentOfType(element, classOf[ScEnumerator], classOf[ScDeclaredElementsHolder])
        val named: Option[ScNamedElement] = namedElement(declaration)
        if (named.isDefined && range.contains(named.get.getNameIdentifier.getTextRange)) {
          setDeclaration(declaration)
          if (nameIsValid != (named.isDefined && isIdentifier(input, myFile.getLanguage) && named.get.name == input)) {
            nameIsValid = !nameIsValid
          }
          resetBalloonPanel(nameIsValid)
        } else {
          nameIsValid = false
          resetBalloonPanel(nameIsValid)
        }
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

  def setDeclaration(declaration: PsiElement): Unit = {
    myPointer = if (declaration != null) SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declaration)
    else myPointer
  }

  private def commitDocument(): Unit = {
    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument)
  }

  override def getComponent: JComponent = {

    if (!isEnumerator) {
      myVarCheckbox = new NonFocusableCheckBox(ScalaBundle.message("introduce.variable.declare.as.var"))
      val withVarSetting = ScalaApplicationSettings.getInstance.INTRODUCE_LOCAL_CREATE_VARIABLE
      if (withVarSetting != null) {
        myVarCheckbox.setSelected(withVarSetting)
      }
      myVarCheckbox.setMnemonic('v')
      myVarCheckbox.addActionListener(new ActionListener {
        def actionPerformed(e: ActionEvent): Unit = {
          val writeAction = new WriteCommandAction[Unit](myProject, getCommandName, getCommandName) {

            private def changeValOrVar(asVar: Boolean, declaration: PsiElement): Unit = {
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

            protected def run(result: Result[Unit]): Unit = {
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
        def actionPerformed(e: ActionEvent): Unit = {
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
              val declaration = getDeclaration
              declaration match {
                case _: ScDeclaredElementsHolder | _: ScEnumerator =>
                  val declarationCopy = declaration.copy.asInstanceOf[ScalaPsiElement]
                  val manager = declarationCopy.getManager
                  val typeName = ScalaRefactoringUtil.typeNameWithImportAliases(selectedType, declaration)
                  val fakeDeclaration =
                    ScalaPsiElementFactory.createDeclaration("x", typeName, isVariable = false, null, manager)
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
            private def removeTypeAnnotation(): Unit = {
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
            protected def run(result: Result[Unit]): Unit = {
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
            def run(): Unit = {
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

  private def setBalloonPanel(nameIsValid: Boolean): Unit = {
    this.nameIsValid = nameIsValid

    myChbPanel.setLayout(new BoxLayout(myChbPanel, BoxLayout.Y_AXIS))
    myChbPanel.setBorder(null)
    Seq(myVarCheckbox, mySpecifyTypeChb).filter(_ != null).foreach{chb =>
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
    if (myBalloon.isDisposed) return
    if (!nameIsValid) {
      myBalloonPanel add myLabelPanel
      myBalloonPanel remove myChbPanel
    }
    else {
      myBalloonPanel add myChbPanel
      myBalloonPanel remove myLabelPanel
    }
    Seq(myVarCheckbox, mySpecifyTypeChb) filter(_ != null) foreach (_.setEnabled(nameIsValid))
    myBalloon.revalidate()
  }

  protected override def moveOffsetAfter(success: Boolean): Unit = {
    try {
      myBalloon.hide()
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
        } else if (getDeclaration != null) {
          myEditor.getCaretModel.moveToOffset(getDeclaration.getTextRange.getEndOffset)
        }
      } else if (getDeclaration != null) {
        val revertInfo = editor.getUserData(ScalaIntroduceVariableHandler.REVERT_INFO)
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

  private def createWarningBalloon(message: String): Unit = {
    SwingUtilities invokeLater new Runnable {
      def run(): Unit = {
        val popupFactory = JBPopupFactory.getInstance
        val bestLocation = popupFactory.guessBestPopupLocation(myEditor)
        val screenPoint: Point = bestLocation.getScreenPoint
        val y: Int = screenPoint.y - editor.getLineHeight * 2
        val balloon: Balloon = popupFactory.createHtmlTextBalloonBuilder(message, null, MessageType.WARNING.getPopupBackground, null).
                setFadeoutTime(-1).setShowCallout(false).createBalloon
        balloon.show(new RelativePoint(new Point(screenPoint.x, y)), Balloon.Position.above)
      }
    }
  }

  override def getReferencesSearchScope(file: VirtualFile): SearchScope = {
   new LocalSearchScope(myElementToRename.getContainingFile)
  }

  override def checkLocalScope(): PsiElement = {
    val scope = new LocalSearchScope(myElementToRename.getContainingFile)
    val elements: Array[PsiElement] = scope.getScope
    PsiTreeUtil.findCommonParent(elements: _*)
  }

  override def finish(success: Boolean): Unit = {
    editor.getDocument.removeDocumentListener(myCheckIdentifierListener)

    if (myVarCheckbox != null) ScalaApplicationSettings.getInstance.INTRODUCE_LOCAL_CREATE_VARIABLE = myVarCheckbox.isSelected
    if (mySpecifyTypeChb != null && !isEnumerator) ScalaApplicationSettings.getInstance.SPECIFY_TYPE_EXPLICITLY = mySpecifyTypeChb.isSelected

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
