package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import java.util

import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Computable, Pass, TextRange}
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, childOf}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScalaRefactoringUtil, ScalaVariableValidator, ValidationReporter}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScalaUtils

import scala.collection.JavaConverters._

/**
 * Created by Kate Ustyuzhanina
 * on 9/18/15
 */
trait IntroduceExpressions {
  this: ScalaIntroduceVariableHandler =>

  val INTRODUCE_VARIABLE_REFACTORING_NAME: String = ScalaBundle.message("introduce.variable.title")

  def invokeExpression(file: PsiFile, startOffset: Int, endOffset: Int)
                      (implicit project: Project, editor: Editor): Unit = {
    try {
      UsageTrigger.trigger(ScalaBundle.message("introduce.variable.id"))

      PsiDocumentManager.getInstance(project).commitAllDocuments()
      writableScalaFile(file, INTRODUCE_VARIABLE_REFACTORING_NAME)
      val (expr, types) = getExpressionWithTypes(file, startOffset, endOffset).
        getOrElse(showErrorHintWithException(ScalaBundle.message("cannot.refactor.not.expression"), INTRODUCE_VARIABLE_REFACTORING_NAME))

      checkCanBeIntroduced(expr)
        .foreach(showErrorHintWithException(_, INTRODUCE_VARIABLE_REFACTORING_NAME))

      val occurrences: Array[TextRange] = fileEncloser(file, startOffset).toArray.flatMap {
        getOccurrenceRanges(unparExpr(expr), _)
      }

      implicit val validator: ScalaVariableValidator = ScalaVariableValidator(file, expr, occurrences)

      def runWithDialog() {
        val dialog = getDialog(project, editor, expr, types, occurrences, declareVariable = false)
        if (!dialog.isOK) {
          occurrenceHighlighters.foreach(_.dispose())
          occurrenceHighlighters = Seq.empty
          return
        }
        val varName: String = dialog.getEnteredName
        val varType: ScType = dialog.getSelectedType
        val isVariable: Boolean = dialog.isDeclareVariable
        val replaceAllOccurrences: Boolean = dialog.isReplaceAllOccurrences
        runRefactoring(startOffset, endOffset, file, editor, expr, occurrences, varName, varType, replaceAllOccurrences, isVariable)
      }

      def runInplace() {

        val callback = new Pass[OccurrencesChooser.ReplaceChoice] {
          def pass(replaceChoice: OccurrencesChooser.ReplaceChoice) {
            val replaceAll = OccurrencesChooser.ReplaceChoice.NO != replaceChoice
            val suggestedNames = NameSuggester.suggestNames(expr)

            val asVar = false
            val selectedType = types(0)
            val introduceRunnable: Computable[SmartPsiElementPointer[PsiElement]] =
              introduceVariable(startOffset, endOffset, file, editor, expr, occurrences, suggestedNames.head, selectedType,
                replaceAll, asVar)
            CommandProcessor.getInstance.executeCommand(project, () => {
              val newDeclaration: PsiElement = ApplicationManager.getApplication.runWriteAction(introduceRunnable).getElement
              val namedElement: PsiNamedElement = newDeclaration match {
                case holder: ScDeclaredElementsHolder =>
                  holder.declaredElements.headOption.orNull
                case enum: ScEnumerator => enum.pattern.bindings.headOption.orNull
                case _ => null
              }
              val newExpr: ScExpression = newDeclaration match {
                case ScVariableDefinition.expr(x) => x
                case ScPatternDefinition.expr(x) => x
                case enum: ScEnumerator => enum.rvalue
                case _ => null
              }
              if (namedElement != null && namedElement.isValid) {
                editor.getCaretModel.moveToOffset(namedElement.getTextOffset)
                editor.getSelectionModel.removeSelection()
                if (isInplaceAvailable(editor)) {
                  PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
                  PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument)
                  val variableIntroducer =
                    new ScalaInplaceVariableIntroducer(project, editor, newExpr, types, namedElement,
                      INTRODUCE_VARIABLE_REFACTORING_NAME, replaceAll, asVar, forceInferType(expr))

                  variableIntroducer.performInplaceRefactoring(new util.LinkedHashSet[String](suggestedNames.asJavaCollection))
                }
              }
            }, INTRODUCE_VARIABLE_REFACTORING_NAME, null)
          }
        }

        val chooser = new OccurrencesChooser[TextRange](editor) {
          override def getOccurrenceRange(occurrence: TextRange): TextRange = occurrence
        }

        if (occurrences.isEmpty) {
          callback.pass(OccurrencesChooser.ReplaceChoice.NO)
        } else {
          import scala.collection.JavaConverters._
          chooser.showChooser(new TextRange(startOffset, endOffset), occurrences.toList.asJava, callback)
        }
      }

      if (isInplaceAvailable(editor)) runInplace()
      else runWithDialog()
    }

    catch {
      case _: IntroduceException =>
    }
  }

  private def forceInferType(expr: ScExpression): Boolean = expr.isInstanceOf[ScFunctionExpr]

  //returns smart pointer to ScDeclaredElementsHolder or ScEnumerator
  private def runRefactoringInside(startOffset: Int, endOffset: Int, file: PsiFile, editor: Editor, expression_ : ScExpression,
                           occurrences_ : Array[TextRange], varName: String, varType: ScType,
                           replaceAllOccurrences: Boolean, isVariable: Boolean, fromDialogMode: Boolean = false): SmartPsiElementPointer[PsiElement] = {

    implicit val projectContext: ProjectContext = file

    def isIntroduceEnumerator(parExpr: PsiElement, prev: PsiElement, firstOccurenceOffset: Int): Option[ScForStatement] = {
      val result = prev match {
        case forSt: ScForStatement if forSt.body.orNull == parExpr => None
        case forSt: ScForStatement => Some(forSt)
        case _: ScEnumerator | _: ScGenerator => Option(prev.getParent.getParent.asInstanceOf[ScForStatement])
        case guard: ScGuard if guard.getParent.isInstanceOf[ScEnumerators] => Option(prev.getParent.getParent.asInstanceOf[ScForStatement])
        case _ =>
          parExpr match {
            case forSt: ScForStatement => Some(forSt) //there are occurrences both in body and in enumerators
            case _ => None
          }
      }
      for {
      //check that first occurence is after first generator
        forSt <- result
        enums <- forSt.enumerators
        generator = enums.generators.head
        if firstOccurenceOffset > generator.getTextRange.getEndOffset
      } yield forSt
    }
    def addPrivateIfNotLocal(declaration: PsiElement) {
      declaration match {
        case member: ScMember if !member.isLocal =>
          member.setModifierProperty("private", value = true)
        case _ =>
      }
    }
    def replaceRangeByDeclaration(range: TextRange, element: PsiElement): PsiElement = {
      val (start, end) = (range.getStartOffset, range.getEndOffset)
      val text: String = element.getText
      val document = editor.getDocument
      document.replaceString(start, end, text)
      PsiDocumentManager.getInstance(element.getProject).commitDocument(document)
      val newEnd = start + text.length
      editor.getCaretModel.moveToOffset(newEnd)
      val decl = PsiTreeUtil.findElementOfClassAtOffset(file, start, classOf[ScMember], /*strictStart =*/ false)
      lazy val enum = PsiTreeUtil.findElementOfClassAtOffset(file, start, classOf[ScEnumerator], /*strictStart =*/ false)
      Option(decl).getOrElse(enum)
    }

    object inExtendsBlock {
      def unapply(e: PsiElement): Option[ScExtendsBlock] = {
        e match {
          case extBl: ScExtendsBlock =>
            Some(extBl)
          case elem if PsiTreeUtil.getParentOfType(elem, classOf[ScClassParents]) != null =>
            PsiTreeUtil.getParentOfType(elem, classOf[ScExtendsBlock]) match {
              case _ childOf (_: ScNewTemplateDefinition) => None
              case extBl => Some(extBl)
            }
          case _ => None
        }
      }
    }

    def isOneLiner = {
      val lineText = getLineText(editor)
      val model = editor.getSelectionModel
      val document = editor.getDocument
      val selectedText = model.getSelectedText
      val lineNumber = document.getLineNumber(model.getSelectionStart)

      val oneLineSelected = selectedText != null && lineText != null && selectedText.trim == lineText.trim

      val element = file.findElementAt(model.getSelectionStart)
      var parent = element
      def atSameLine(elem: PsiElement) = {
        val offsets = Seq(elem.getTextRange.getStartOffset, elem.getTextRange.getEndOffset)
        offsets.forall(document.getLineNumber(_) == lineNumber)
      }
      while (parent != null && !parent.isInstanceOf[PsiFile] && atSameLine(parent)) {
        parent = parent.getParent
      }
      val insideExpression = parent match {
        case null | _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions | _: PsiFile => false
        case _ => true
      }
      oneLineSelected && !insideExpression
    }

    val revertInfo = RevertInfo(file.getText, editor.getCaretModel.getOffset)
    editor.putUserData(ScalaIntroduceVariableHandler.REVERT_INFO, revertInfo)

    val typeName = if (varType != null) varType.canonicalText else ""
    val expression = expressionToIntroduce(expression_)

    def needsTypeAnnotation =
      ScalaInplaceVariableIntroducer.needsTypeAnnotation(_: PsiElement, expression, forceInferType(expression), fromDialogMode)

    val isFunExpr = expression.isInstanceOf[ScFunctionExpr]
    val mainRange = new TextRange(startOffset, endOffset)
    val occurrences: Array[TextRange] = if (!replaceAllOccurrences) {
      Array[TextRange] (mainRange)
    } else occurrences_
    val occCount = occurrences.length

    val mainOcc = occurrences.indexWhere(range => range.contains(mainRange) || mainRange.contains(range))
    val fastDefinition = occCount == 1 && isOneLiner

    //changes document directly
    val replacedOccurences = replaceOccurences(occurrences, varName, file)

    //only Psi-operations after this moment
    var firstRange = replacedOccurences(0)
    val firstElement = findParentExpr(file, firstRange)
    val parentExprs =
      if (occCount == 1)
        firstElement match {
          case _ childOf ((block: ScBlock) childOf ((_) childOf (call: ScMethodCall)))
            if isFunExpr && block.statements.size == 1 => Seq(call)
          case _ childOf ((block: ScBlock) childOf (infix: ScInfixExpr))
            if isFunExpr && block.statements.size == 1 => Seq(infix)
          case expr => Seq(expr)
        }
      else replacedOccurences.toSeq.map(findParentExpr(file, _))
    val commonParent: PsiElement = PsiTreeUtil.findCommonParent(parentExprs: _*)

    val nextParentInFile = nextParent(commonParent, file)

    editor.getCaretModel.moveToOffset(replacedOccurences(mainOcc).getEndOffset)

    def createEnumeratorIn(forStmt: ScForStatement): ScEnumerator = {
      val parent: ScEnumerators = forStmt.enumerators.orNull
      val inParentheses = parent.prevSiblings.toList.exists(_.getNode.getElementType == ScalaTokenTypes.tLPARENTHESIS)
      val needType = needsTypeAnnotation(parent)
      val created = createEnumerator(varName, unparExpr(expression), if (needType) typeName else "")
      val elem = parent.getChildren.filter(_.getTextRange.contains(firstRange)).head
      var result: ScEnumerator = null
      if (elem != null) {
        var needSemicolon = true
        var sibling = elem.getPrevSibling
        if (inParentheses) {
          while (sibling != null && sibling.getText.trim == "") sibling = sibling.getPrevSibling
          if (sibling != null && sibling.getText.endsWith(";")) needSemicolon = false
          val semicolon = parent.addBefore(createSemicolon, elem)
          result = parent.addBefore(created, semicolon).asInstanceOf[ScEnumerator]
          if (needSemicolon) {
            parent.addBefore(createSemicolon, result)
          }
        } else {
          if (sibling.getText.indexOf('\n') != -1) needSemicolon = false
          result = parent.addBefore(created, elem).asInstanceOf[ScEnumerator]
          parent.addBefore(createNewLine()(elem.getManager), elem)
          if (needSemicolon) {
            parent.addBefore(createNewLine(), result)
          }
        }
      }
      result
    }

    def createVariableDefinition(): PsiElement = {
      if (fastDefinition) {
        val addType = needsTypeAnnotation(firstElement)
        replaceRangeByDeclaration(replacedOccurences(0), createDeclaration(varName, if (addType) typeName else "", isVariable, unparExpr(expression)))
      } else {
        var needFormatting = false
        val parent = commonParent match {
          case inExtendsBlock(extBl) =>
            needFormatting = true
            extBl.addEarlyDefinitions()
          case _ =>
            val needBraces = !commonParent.isInstanceOf[ScBlock] && ScalaRefactoringUtil.needBraces(commonParent, nextParentInFile)
            if (needBraces) {
              firstRange = firstRange.shiftRight(1)
              val replaced = commonParent.replace(createExpressionFromText("{" + commonParent.getText + "}"))
              replaced.getPrevSibling match {
                case ws: PsiWhiteSpace if ws.getText.contains("\n") => ws.delete()
                case _ =>
              }
              replaced
            } else container(commonParent, file)
        }
        val anchor = parent.getChildren.find(_.getTextRange.contains(firstRange)).getOrElse(parent.getLastChild)
        if (anchor != null) {
          val addType = needsTypeAnnotation(anchor)

          val created = createDeclaration(varName, if (addType) typeName else "", isVariable, unparExpr(expression))

          val result = ScalaPsiUtil.addStatementBefore(created.asInstanceOf[ScBlockStatement], parent, Some(anchor))
          CodeEditUtil.markToReformat(parent.getNode, needFormatting)
          result
        } else throw new IntroduceException
      }
    }

    val createdDeclaration: PsiElement = isIntroduceEnumerator(commonParent, nextParentInFile, firstRange.getStartOffset) match {
      case Some(forStmt) => createEnumeratorIn(forStmt)
      case _ => createVariableDefinition()
    }

    addPrivateIfNotLocal(createdDeclaration)
    ScalaPsiUtil.adjustTypes(createdDeclaration)
    SmartPointerManager.getInstance(file.getProject).createSmartPsiElementPointer(createdDeclaration)
  }

  def runRefactoring(startOffset: Int, endOffset: Int, file: PsiFile, editor: Editor, expression: ScExpression,
                     occurrences_ : Array[TextRange], varName: String, varType: ScType,
                     replaceAllOccurrences: Boolean, isVariable: Boolean) {
    val runnable = new Runnable() {
      def run() {
        runRefactoringInside(startOffset, endOffset, file, editor, expression, occurrences_, varName,
          varType, replaceAllOccurrences, isVariable, fromDialogMode = true) //this for better debug
      }
    }

    ScalaUtils.runWriteAction(runnable, editor.getProject, INTRODUCE_VARIABLE_REFACTORING_NAME)
    editor.getSelectionModel.removeSelection()
  }

  protected def introduceVariable(startOffset: Int, endOffset: Int, file: PsiFile, editor: Editor, expression: ScExpression,
                        occurrences_ : Array[TextRange], varName: String, varType: ScType,
                        replaceAllOccurrences: Boolean, isVariable: Boolean): Computable[SmartPsiElementPointer[PsiElement]] = {

    () => runRefactoringInside(startOffset, endOffset, file, editor, expression, occurrences_, varName,
      varType, replaceAllOccurrences, isVariable)

  }

  protected def getDialog(project: Project, editor: Editor, expr: ScExpression, typez: Array[ScType],
                          occurrences: Array[TextRange], declareVariable: Boolean)
                         (implicit validator: ScalaVariableValidator): ScalaIntroduceVariableDialog = {
    // Add occurrences highlighting
    if (occurrences.length > 1)
      occurrenceHighlighters = highlightOccurrences(project, occurrences, editor)

    val possibleNames = NameSuggester.suggestNames(expr).toArray
    val reporter = new ValidationReporter(project, this)

    val dialog = new ScalaIntroduceVariableDialog(project, typez, occurrences.length, reporter, possibleNames, expr)
    dialog.show()
    if (!dialog.isOK) {
      if (occurrences.length > 1) {
        WindowManager.getInstance.getStatusBar(project).
          setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
      }
    }

    dialog
  }
}
