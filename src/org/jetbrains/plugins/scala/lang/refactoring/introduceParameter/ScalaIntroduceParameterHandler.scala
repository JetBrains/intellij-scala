package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceParameter


import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.{Editor, SelectionModel}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.{HelpID, RefactoringActionHandler, RefactoringBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.{ReachingDefintionsCollector, VariableInfo}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{Any => scTypeAny, ScType, Unit => scTypeUnit}
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaMethodDescriptor, ScalaParameterInfo}
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterHandler._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.IntroduceException
import org.jetbrains.plugins.scala.lang.refactoring.util.{DialogConflictsReporter, ScalaRefactoringUtil, ScalaVariableValidator}

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.06.2009
 */
class ScalaIntroduceParameterHandler extends RefactoringActionHandler with DialogConflictsReporter {

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    if (!file.isInstanceOf[ScalaFile]) return
    if (!ScalaRefactoringUtil.ensureFileWritable(project, file)) {
      showErrorMessage(ScalaBundle.message("file.is.not.writable"), project, editor, REFACTORING_NAME)
      return
    }

    val canBeIntroduced: (ScExpression) => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
    ScalaRefactoringUtil.afterExpressionChoosing(project, editor, file, dataContext, "Introduce Parameter", canBeIntroduced) {
      UsageTrigger.trigger(ScalaBundle.message("introduce.parameter.id"))
      invoke(project, editor, file)
    }
  }

  def functionalArg(elems: Seq[PsiElement], input: Iterable[VariableInfo], method: ScMethodLike): ScExpression = {
    val namesAndTypes = input.map { v =>
      val elem = v.element
      val typeText = ScType.ofNamedElement(v.element).getOrElse(scTypeAny).canonicalText
      s"${elem.name}: $typeText"
    }
    val project = method.getProject
    val arrow = ScalaPsiUtil.functionArrow(project)
    val paramsText = namesAndTypes.mkString("(", ", ", ")")
    val funText = elems match {
      case Seq(single: ScExpression) =>
        val bodyText = single.getText
        s"$paramsText $arrow $bodyText"
      case _ =>
        val bodyText = elems.map(_.getText).mkString
        s"$paramsText $arrow {\n$bodyText\n}"
    }
    val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(funText, elems.head.getContext, elems.head)
    CodeStyleManager.getInstance(project).reformat(expr).asInstanceOf[ScExpression]
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val (exprWithTypes, elems) = selectedElements(file, project, editor) match {
      case Some((x, y)) => (x, y)
      case None => return
    }

    afterMethodChoosing(elems.head, editor) { methodLike =>
      val data = collectData(exprWithTypes, elems, methodLike, editor)

      data.foreach { d =>
        val dialog = createDialog(project, d)
        if (dialog.showAndGet) {
          invokeLater {
            if (editor != null && !editor.isDisposed)
              editor.getSelectionModel.removeSelection()
          }
        }
      }
    }
  }

  private type ExprWithTypes = Option[(ScExpression, Array[ScType])]

  def selectedElements(file: PsiFile, project: Project, editor: Editor): Option[(ExprWithTypes, Seq[PsiElement])] = {
    try {
      val selModel: SelectionModel = editor.getSelectionModel
      if (!selModel.hasSelection) return None

      val (startOffset, endOffset) = (selModel.getSelectionStart, selModel.getSelectionEnd)
      ScalaRefactoringUtil.checkFile(file, project, editor, REFACTORING_NAME)

      val exprWithTypes = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset)
      val elems = exprWithTypes match {
        case Some((e, _)) => Seq(e)
        case None => ScalaRefactoringUtil.selectedElements(editor, file.asInstanceOf[ScalaFile], trimComments = false)
      }

      ScalaRefactoringUtil.showNotPossibleWarnings(elems, project, editor, REFACTORING_NAME)
      if (haveReturnStmts(elems)) {
        showErrorMessage("Refactoring is not supported: selection contains return statement", project, editor, REFACTORING_NAME)
        return None
      }

      Some((exprWithTypes, elems))
    }
    catch {
      case _: IntroduceException => None
    }
  }

  def collectData(exprWithTypes: ExprWithTypes, elems: Seq[PsiElement], methodLike: ScMethodLike, editor: Editor): Option[ScalaIntroduceParameterData] = {
    val project = methodLike.getProject

    val info = ReachingDefintionsCollector.collectVariableInfo(elems, methodLike)
    val input = info.inputVariables
    val (types, argText, argClauseText) =
      if (input.nonEmpty || exprWithTypes.isEmpty) {
        val funExpr = functionalArg(elems, input, methodLike)
        val argClauseText = input.map(_.element.name).mkString("(", ", ", ")")
        (Array(funExpr.getNonValueType().getOrAny, scTypeUnit), funExpr.getText, argClauseText)
      }
      else (exprWithTypes.get._2, exprWithTypes.get._1.getText, "")

    val superMethod = methodLike.findDeepestSuperMethod() match {
      case null => methodLike
      case scMethod: ScMethodLike => SuperMethodWarningUtil.checkSuperMethod(methodLike, RefactoringBundle.message("to.refactor"))
      case _ => methodLike
    }
    val methodToSearchFor = superMethod match {
      case m: ScMethodLike => m
      case _ => return None
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, superMethod)) return None

    val suggestedName = {
      val validator = new ScalaVariableValidator(this, project, elems.head, false, methodLike, methodLike)
      val possibleNames = elems match {
        case Seq(expr: ScExpression) => NameSuggester.suggestNames(expr, validator)
        case _ => NameSuggester.suggestNamesByType(types(0))
      }
      possibleNames(0)
    }

    val (occurrences, mainOcc) = elems match {
      case Seq(expr: ScExpression) =>
        val occurrencesScope = methodLike match {
          case ScFunctionDefinition.withBody(body) => body
          case pc: ScPrimaryConstructor => pc.containingClass.extendsBlock
          case _ => methodLike
        }

        val occurrences = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), occurrencesScope)
        if (occurrences.length > 1)
          ScalaRefactoringUtil.highlightOccurrences(project, occurrences, editor)

        (occurrences, expr.getTextRange)
      case _ => (Array.empty[TextRange], elems.head.getTextRange.union(elems.last.getTextRange))

    }
    val data = ScalaIntroduceParameterData(methodLike, methodToSearchFor, elems,
      suggestedName, types, types(0), occurrences, mainOcc, replaceAll = false, argText, Some(argClauseText))
    Some(data)
  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    /*do nothing*/
  }

  private def getEnclosingMethods(expr: PsiElement): Seq[ScMethodLike] = {
    var enclosingMethods = new ArrayBuffer[ScMethodLike]
    var elem: PsiElement = expr
    while (elem != null) {
      val newFun = PsiTreeUtil.getContextOfType(elem, true, classOf[ScFunctionDefinition], classOf[ScClass])
      newFun match {
        case f@ScFunctionDefinition.withBody(body) if PsiTreeUtil.isContextAncestor(body, expr, false) =>
          enclosingMethods += f
        case cl: ScClass => enclosingMethods ++= cl.constructor
        case _ =>
      }
      elem = newFun
    }
    if (enclosingMethods.size > 1) {
      val methodsNotImplementingLibraryInterfaces = enclosingMethods.filter {
        case f: ScFunctionDefinition if f.superMethods.exists(isLibraryInterfaceMethod) => false
        case _ => true
      }
      if (methodsNotImplementingLibraryInterfaces.nonEmpty)
        return methodsNotImplementingLibraryInterfaces
    }
    enclosingMethods
  }

  def createDialog(project: Project, data: ScalaIntroduceParameterData) = {
    val paramInfo = new ScalaParameterInfo(data.paramName, -1, data.tp, project, false, false, data.defaultArg, isIntroducedParameter = true)
    val descriptor = createMethodDescriptor(data.methodToSearchFor, paramInfo)
    new ScalaAddParameterDialog(project, descriptor, data)
  }

  def createMethodDescriptor(method: ScMethodLike, paramInfo: ScalaParameterInfo): ScalaMethodDescriptor = {
    new ScalaMethodDescriptor(method) {
      override def parametersInner: Seq[Seq[ScalaParameterInfo]] = {
        val params = super.parametersInner
        params.headOption match {
          case Some(seq) if seq.lastOption.exists(_.isRepeatedParameter) =>
            val newFirstClause = seq.dropRight(1) :+ paramInfo :+ seq.last
            newFirstClause +: params.tail
          case Some(seq) =>
            val newFirstClause = seq :+ paramInfo
            newFirstClause +: params.tail
          case None => Seq(Seq(paramInfo))
        }
      }
    }
  }

  private def getTextForElement(method: ScMethodLike): String = {
    method match {
      case pc: ScPrimaryConstructor => s"${pc.containingClass.name} (primary constructor)"
      case (f: ScFunctionDefinition) && ContainingClass(c: ScNewTemplateDefinition) => s"${f.name} (in anonymous class)"
      case (f: ScFunctionDefinition) && ContainingClass(c) => s"${f.name} (in ${c.name})"
      case f: ScFunctionDefinition => s"${f.name}"
    }
  }

  private def toHighlight(e: PsiElement) = e match {
    case pc: ScPrimaryConstructor => pc.containingClass.extendsBlock
    case _ => e
  }

  def afterMethodChoosing(elem: PsiElement, editor: Editor)(action: ScMethodLike => Unit): Unit = {
    val validEnclosingMethods: Seq[ScMethodLike] = getEnclosingMethods(elem)
    if (validEnclosingMethods.size > 1 && !ApplicationManager.getApplication.isUnitTestMode) {
      ScalaRefactoringUtil.showChooser[ScMethodLike](editor, validEnclosingMethods.toArray, action,
        s"Choose function for $REFACTORING_NAME", getTextForElement, toHighlight)
    }
    else if (validEnclosingMethods.size == 1 || ApplicationManager.getApplication.isUnitTestMode) {
      action(validEnclosingMethods.head)
    } else {
      showErrorMessage(ScalaBundle.message("cannot.refactor.no.function"), elem.getProject, editor, REFACTORING_NAME)
    }
  }

  private def isLibraryInterfaceMethod(method: PsiMethod): Boolean = {
    (method.hasModifierPropertyScala(PsiModifier.ABSTRACT) || method.isInstanceOf[ScFunctionDefinition]) &&
      !method.getManager.isInProject(method)
  }

  private def haveReturnStmts(elems: Seq[PsiElement]): Boolean = {
    for {
      elem <- elems
      ret @ (r: ScReturnStmt) <- elem.depthFirst
    } {
      if (ret.returnFunction.isEmpty || !elem.isAncestorOf(ret.returnFunction.get))
        return true
    }
    false
  }

  private def showErrorMessage(text: String, project: Project, editor: Editor, refactoringName: String): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    CommonRefactoringUtil.showErrorHint(project, editor, text, refactoringName, HelpID.INTRODUCE_PARAMETER)
  }
}

object ScalaIntroduceParameterHandler {
  val REFACTORING_NAME = ScalaBundle.message("introduce.parameter.title")
}