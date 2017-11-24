package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import java.{util => ju}

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Pass, TextRange}
import com.intellij.psi.PsiModifier.PRIVATE
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.findElementOfClassAtOffset
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, childOf, inWriteAction, startCommand}
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
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
  * Created by Kate Ustyuzhanina
  * on 9/18/15
  */
trait IntroduceExpressions {
  this: ScalaIntroduceVariableHandler =>

  val INTRODUCE_VARIABLE_REFACTORING_NAME: String = ScalaBundle.message("introduce.variable.title")

  import IntroduceExpressions._

  def invokeExpression(file: PsiFile, startOffset: Int, endOffset: Int)
                      (implicit project: Project, editor: Editor): Unit = {
    try {
      Stats.trigger(FeatureKey.introduceVariable)

      PsiDocumentManager.getInstance(project).commitAllDocuments()
      writableScalaFile(file, INTRODUCE_VARIABLE_REFACTORING_NAME)

      val (expr, types) = getExpressionWithTypes(file, startOffset, endOffset).getOrElse {
        showErrorHint(ScalaBundle.message("cannot.refactor.not.expression"), INTRODUCE_VARIABLE_REFACTORING_NAME)
        return
      }

      checkCanBeIntroduced(expr).foreach { message =>
        showErrorHint(message, INTRODUCE_VARIABLE_REFACTORING_NAME)
        return
      }

      val occurrences = fileEncloser(file, startOffset).toSeq.flatMap {
        getOccurrenceRanges(expr, _)
      }

      implicit val validator: ScalaVariableValidator = ScalaVariableValidator(file, expr, occurrences)

      val suggestedNames = SuggestedNames(expr, types)
      val occurrencesInFile = OccurrencesInFile(file, new TextRange(startOffset, endOffset), occurrences)

      if (isInplaceAvailable(editor)) runInplace(suggestedNames, occurrencesInFile)
      else runWithDialog(suggestedNames, occurrencesInFile)
    }

    catch {
      case _: IntroduceException =>
    }
  }

  def runRefactoring(occurrences: OccurrencesInFile, expression: ScExpression, varName: String, varType: ScType,
                     replaceAllOccurrences: Boolean, isVariable: Boolean)
                    (implicit editor: Editor): Unit = {
    startCommand(editor.getProject, INTRODUCE_VARIABLE_REFACTORING_NAME) {
      runRefactoringInside(occurrences, expression, varName, varType, replaceAllOccurrences, isVariable, fromDialogMode = true) // this for better debug
    }
    editor.getSelectionModel.removeSelection()
  }

  private def runInplace(suggestedNames: SuggestedNames, occurrences: OccurrencesInFile)
                        (implicit project: Project, editor: Editor): Unit = {
    import OccurrencesChooser.ReplaceChoice

    val callback: Pass[ReplaceChoice] = (replaceChoice: ReplaceChoice) => {
      val replaceAll = ReplaceChoice.NO != replaceChoice

      startCommand(project, INTRODUCE_VARIABLE_REFACTORING_NAME) {
        val SuggestedNames(expression, types, names) = suggestedNames
        val reference = inWriteAction {
          runRefactoringInside(occurrences, expression, names.head, types.head, replaceAll, isVariable = false, fromDialogMode = false)
        }
        performInplaceRefactoring(reference.getElement, types.headOption, replaceAll, forceInferType(expression), names)
      }
    }

    val OccurrencesInFile(_, mainRange, occurrences_) = occurrences
    if (occurrences_.isEmpty) {
      callback.pass(ReplaceChoice.NO)
    } else {
      val chooser = new OccurrencesChooser[TextRange](editor) {
        override def getOccurrenceRange(occurrence: TextRange): TextRange = occurrence
      }

      chooser.showChooser(mainRange, ju.Arrays.asList(occurrences_ : _*), callback)
    }
  }

  private def runWithDialog(suggestedNames: SuggestedNames, occurrences: OccurrencesInFile)
                           (implicit project: Project, editor: Editor, validator: ScalaVariableValidator): Unit = {
    val occurrences_ = occurrences.occurrences

    val SuggestedNames(expression, types, names) = suggestedNames
    val dialog = new ScalaIntroduceVariableDialog(project, types, occurrences_.length, new ValidationReporter(project, this), names, expression)

    this.showDialogImpl(dialog, occurrences_).foreach { dialog =>
      runRefactoring(occurrences, suggestedNames.expression,
        varName = dialog.getEnteredName,
        varType = dialog.getSelectedType,
        replaceAllOccurrences = dialog.isReplaceAllOccurrences,
        isVariable = dialog.isDeclareVariable
      )
    }
  }
}

object IntroduceExpressions {

  private class SuggestedNames(val expression: ScExpression, val types: Array[ScType]) {

    def names: Array[String] = NameSuggester.suggestNames(expression).toArray
  }

  private object SuggestedNames {

    def apply(expression: ScExpression, types: Array[ScType]): SuggestedNames =
      new SuggestedNames(expression, types)

    def unapply(names: SuggestedNames): Option[(ScExpression, Array[ScType], Array[String])] =
      Some(names.expression, names.types, names.names)
  }

  case class OccurrencesInFile(file: PsiFile, mainRange: TextRange, occurrences: Seq[TextRange])

  private def performInplaceRefactoring(newDeclaration: PsiElement,
                                        maybeType: Option[ScType],
                                        replaceAll: Boolean,
                                        forceType: Boolean,
                                        suggestedNames: Array[String])
                                       (implicit project: Project, editor: Editor): Unit = {
    val maybeNamedElement = newDeclaration match {
      case holder: ScDeclaredElementsHolder => holder.declaredElements.headOption
      case enum: ScEnumerator => enum.pattern.bindings.headOption
      case _ => None
    }

    val newExpr = newDeclaration match {
      case ScVariableDefinition.expr(x) => x
      case ScPatternDefinition.expr(x) => x
      case enum: ScEnumerator => enum.rvalue
      case _ => null
    }

    maybeNamedElement.filter(_.isValid).foreach { named =>
      editor.getCaretModel.moveToOffset(named.getTextOffset)
      editor.getSelectionModel.removeSelection()

      if (isInplaceAvailable(editor)) {
        (editor.getDocument, PsiDocumentManager.getInstance(project)) match {
          case (document, manager) =>
            manager.commitDocument(document)
            manager.doPostponedOperationsAndUnblockDocument(document)
        }

        new ScalaInplaceVariableIntroducer(newExpr, maybeType, named, replaceAll, forceType)
          .performInplaceRefactoring(new ju.LinkedHashSet(ju.Arrays.asList(suggestedNames: _*)))
      }
    }
  }

  private def forceInferType(expression: ScExpression) = expression.isInstanceOf[ScFunctionExpr]

  //returns smart pointer to ScDeclaredElementsHolder or ScEnumerator
  private def runRefactoringInside(occurrencesInFile: OccurrencesInFile,
                                   expression: ScExpression,
                                   varName: String,
                                   varType: ScType,
                                   replaceAllOccurrences: Boolean,
                                   isVariable: Boolean,
                                   fromDialogMode: Boolean)
                                  (implicit editor: Editor): SmartPsiElementPointer[PsiElement] = {
    val OccurrencesInFile(file, mainRange, occurrences_) = occurrencesInFile

    val occurrences = if (replaceAllOccurrences) occurrences_ else Seq(mainRange)
    val mainOccurence = occurrences.indexWhere(range => range.contains(mainRange) || mainRange.contains(range))

    val copy = expressionToIntroduce(expression)
    val forceType = forceInferType(copy)

    def needsTypeAnnotation(element: PsiElement) =
      ScalaInplaceVariableIntroducer.needsTypeAnnotation(element, copy, forceType, fromDialogMode)

    val maybeTypeText = Option(varType).map(_.canonicalText)

    runRefactoringInside(file, unparExpr(copy), occurrences, mainOccurence, varName, isVariable, forceType) { element =>
      maybeTypeText
        .filter(_ => needsTypeAnnotation(element))
        .getOrElse("")
    }
  }

  private[this] def runRefactoringInside(file: PsiFile,
                                         expression: ScExpression,
                                         occurrences: Seq[TextRange],
                                         mainOccurence: Int,
                                         varName: String,
                                         isVariable: Boolean,
                                         forceType: Boolean)
                                        (typeTextIfNeeded: PsiElement => String)
                                        (implicit editor: Editor): SmartPsiElementPointer[PsiElement] = {
    implicit val projectContext: ProjectContext = file

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

      val oneLineSelected = selectedText != null && lineText != null && selectedText.trim == lineText.trim

      val element = file.findElementAt(model.getSelectionStart)
      var parent = element

      def atSameLine(elem: PsiElement) = {
        val textRange = elem.getTextRange
        val lineNumbers = Seq(model.getSelectionStart, textRange.getStartOffset, textRange.getEndOffset)
          .map(document.getLineNumber)

        lineNumbers.distinct.size == 1
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

    val fastDefinition = occurrences.length == 1 && isOneLiner

    //changes document directly
    val replacedOccurences = replaceOccurences(occurrences, varName, file)

    //only Psi-operations after this moment
    var firstRange = replacedOccurences.head
    val firstElement = findParentExpr(file, firstRange)
    val parentExprs =
      if (occurrences.length == 1)
        firstElement match {
          case _ childOf ((block: ScBlock) childOf ((_) childOf (call: ScMethodCall)))
            if forceType && block.statements.size == 1 => Seq(call)
          case _ childOf ((block: ScBlock) childOf (infix: ScInfixExpr))
            if forceType && block.statements.size == 1 => Seq(infix)
          case expr => Seq(expr)
        }
      else replacedOccurences.toSeq.map(findParentExpr(file, _))
    val commonParent: PsiElement = PsiTreeUtil.findCommonParent(parentExprs: _*)

    val nextParentInFile = nextParent(commonParent, file)

    editor.getCaretModel.moveToOffset(replacedOccurences(mainOccurence).getEndOffset)

    def createEnumeratorIn(forStmt: ScForStatement): ScEnumerator = {
      val parent: ScEnumerators = forStmt.enumerators.orNull
      val inParentheses = parent.prevSiblings.toList.exists(_.getNode.getElementType == ScalaTokenTypes.tLPARENTHESIS)
      val created = createEnumerator(varName, expression, typeTextIfNeeded(parent))
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
        val declaration = createDeclaration(varName, typeTextIfNeeded(firstElement), isVariable, expression)
        replaceRangeByDeclaration(declaration.getText, firstRange)(declaration.getProject, editor)

        val start = firstRange.getStartOffset
        Option(findElementOfClassAtOffset(file, start, classOf[ScMember], /*strictStart =*/ false))
          .getOrElse(findElementOfClassAtOffset(file, start, classOf[ScEnumerator], /*strictStart =*/ false))
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
            } else container(commonParent).getOrElse(file)
        }
        val anchor = parent.getChildren.find(_.getTextRange.contains(firstRange)).getOrElse(parent.getLastChild)
        if (anchor != null) {
          val created = createDeclaration(varName, typeTextIfNeeded(anchor), isVariable, expression)
          val result = ScalaPsiUtil.addStatementBefore(created.asInstanceOf[ScBlockStatement], parent, Some(anchor))
          CodeEditUtil.markToReformat(parent.getNode, needFormatting)
          result
        } else throw new IntroduceException
      }
    }

    val createdDeclaration: PsiElement = isIntroduceEnumerator(commonParent, nextParentInFile, firstRange) match {
      case Some(forStmt) => createEnumeratorIn(forStmt)
      case _ => createVariableDefinition()
    }

    setPrivateModifier(createdDeclaration)
    ScalaPsiUtil.adjustTypes(createdDeclaration)
    createdDeclaration.createSmartPointer(SmartPointerManager.getInstance(file.getProject))
  }

  private[this] def replaceRangeByDeclaration(text: String, range: TextRange)
                                             (implicit project: Project, editor: Editor): Unit = {
    val startOffset = range.getStartOffset

    val document = editor.getDocument
    document.replaceString(startOffset, range.getEndOffset, text)
    PsiDocumentManager.getInstance(project).commitDocument(document)

    editor.getCaretModel.moveToOffset(startOffset + text.length)
  }

  private[this] def isIntroduceEnumerator(parent: PsiElement, element: PsiElement, range: TextRange): Option[ScForStatement] = {
    val maybeParent = element match {
      case statement: ScForStatement if statement.body.contains(parent) => None
      case statement: ScForStatement => Some(statement)
      case _: ScEnumerator | _: ScGenerator => Option(element.getParent.getParent)
      case guard: ScGuard if guard.getParent.isInstanceOf[ScEnumerators] => Option(element.getParent.getParent)
      case _ => Some(parent)
    }

    maybeParent.collect {
      case statement: ScForStatement => statement
    }.filter(_.enumerators.exists(isAfterFirstGenerator(_, range)))
  }

  private[this] def setPrivateModifier(declaration: PsiElement): Unit = declaration match {
    case member: ScMember if !member.isLocal => member.setModifierProperty(PRIVATE, value = true)
    case _ =>
  }

  private[this] def isAfterFirstGenerator(enumerators: ScEnumerators, range: TextRange): Boolean =
    enumerators.generators.headOption
      .exists(_.getTextRange.getEndOffset < range.getStartOffset)
}
