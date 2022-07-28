package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceParameter

import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.plugins.scala.codeInsight.intention.expression.ConvertParameterToUnderscoreIntention
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.{ReachingDefinitionsCollector, VariableInfo}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaMethodDescriptor, ScalaParameterInfo}
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterHandler._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.lang.refactoring.util.{DialogConflictsReporter, ScalaVariableValidator}
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.annotation.nowarn
import scala.collection.immutable.ArraySeq

class ScalaIntroduceParameterHandler extends ScalaRefactoringActionHandler with DialogConflictsReporter {

  private var occurrenceHighlighters: Iterable[RangeHighlighter] = Seq.empty[RangeHighlighter]


  override def invoke(file: PsiFile)
                     (implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    val scalaFile = maybeWritableScalaFile(file, REFACTORING_NAME)
      .getOrElse(return)

    afterExpressionChoosing(file, REFACTORING_NAME) {
      invoke(scalaFile)
    }
  }

  def functionalArg(elems: Seq[PsiElement], input: Iterable[VariableInfo], method: ScMethodLike): (ScExpression, ScType) = {
    import method.projectContext

    val namesAndTypes = input.map { v =>
      val elem = v.element
      val typeText = elem match {
        case fun: ScFunction => fun.`type`().getOrAny.canonicalCodeText
        case _ => v.element.ofNamedElement().getOrElse(Any).canonicalCodeText
      }
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
    val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(funText, elems.head.getContext, elems.head).asInstanceOf[ScFunctionExpr]
    val toReturn = ConvertParameterToUnderscoreIntention.createExpressionToIntroduce(expr, withoutParameterTypes = true) match {
      case Left(e) => e
      case _ => expr
    }
    ScalaPsiUtil.adjustTypes(toReturn, addImports = false)
    (CodeStyleManager.getInstance(project).reformat(toReturn).asInstanceOf[ScExpression], expr.getTypeWithoutImplicits(ignoreBaseType = true).getOrAny)
  }

  private def invoke(file: ScalaFile)
                    (implicit project: Project, editor: Editor): Unit = {
    Stats.trigger(FeatureKey.introduceParameter)

    trimSpacesAndComments(editor, file)
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val (exprWithTypes, elems) = selectedElementsInFile(file) match {
      case Some((x, y)) => (x, y)
      case None => return
    }

    afterMethodChoosing(elems.head) { methodLike =>
      val data = collectData(exprWithTypes, elems, methodLike, editor)

      data.foreach { data =>
        val dialog = createDialog(data)
        if (dialog.showAndGet) {
          invokeLater {
            if (editor != null && !editor.isDisposed)
              editor.getSelectionModel.removeSelection()
          }
        } else {
          occurrenceHighlighters.foreach(_.dispose())
          occurrenceHighlighters = Seq.empty
        }
      }
    }
  }

  private type ExprWithTypes = Option[(ScExpression, ArraySeq[ScType])]

  def selectedElementsInFile(file: PsiFile)
                            (implicit project: Project, editor: Editor): Option[(ExprWithTypes, Seq[PsiElement])] = {
    try {
      if (!editor.getSelectionModel.hasSelection) return None

      val scalaFile = writableScalaFile(file, REFACTORING_NAME)

      val exprWithTypes = getExpressionWithTypes(scalaFile)
      val elems = exprWithTypes match {
        case Some((e, _)) => Seq(e)
        case None => selectedElements(editor, scalaFile, trimComments = false)
      }

      elems match {
        case seq if showNotPossibleWarnings(seq, REFACTORING_NAME) => None
        case seq if haveReturnStmts(seq) =>
          showErrorHint(ScalaBundle.message("refactoring.is.not.supported.contains.return"), REFACTORING_NAME)
          None
        case seq => Some((exprWithTypes, seq))
      }
    }
    catch {
      case _: IntroduceException => None
    }
  }


  def collectData(exprWithTypes: ExprWithTypes, elems: Seq[PsiElement], methodLike: ScMethodLike, editor: Editor): Option[ScalaIntroduceParameterData] = {
    implicit val project: Project = methodLike.getProject

    val info = ReachingDefinitionsCollector.collectVariableInfo(elems, methodLike)
    val input = info.inputVariables
    val (types, argText, argClauseText) =
      if (input.nonEmpty || exprWithTypes.isEmpty) {
        val (funExpr, funType) = functionalArg(elems, input, methodLike)
        val argClauseText = input.map(_.element.name).mkString("(", ", ", ")")
        val allTypes = funType match {
          case FunctionType(retType, _) => ArraySeq(funType, retType, Any)
          case _ => ArraySeq(funType, Any)
        }
        (allTypes, funExpr.getText, argClauseText)
      }
      else (exprWithTypes.get._2, exprWithTypes.get._1.getText, "")

    val superMethod = (methodLike.findDeepestSuperMethod(): @nowarn("cat=deprecation")) match {
      case null => methodLike
      case _: ScMethodLike => SuperMethodWarningUtil.checkSuperMethod(methodLike)
      case _ => methodLike
    }
    val methodToSearchFor: ScMethodLike = superMethod match {
      case m: ScMethodLike => m
      case _ => return None
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, superMethod)) return None

    val suggestedName = {
      val validator: ScalaVariableValidator = new ScalaVariableValidator(elems.head, false, methodLike, methodLike)
      val possibleNames = elems match {
        case Seq(expr: ScExpression) => NameSuggester.suggestNames(expr, validator, types)
        case _ => NameSuggester.suggestNamesByType(types(0))
      }
      possibleNames.head
    }

    val (occurrences, mainOcc) = elems match {
      case Seq(expr: ScExpression) =>
        val occurrencesScope = methodLike match {
          case ScFunctionDefinition.withBody(body) => body
          case pc: ScPrimaryConstructor => pc.containingClass.extendsBlock
          case _ => methodLike
        }

        val occurrences = getOccurrenceRanges(expr, occurrencesScope)
        if (occurrences.length > 1)
          occurrenceHighlighters = highlightOccurrences(project, occurrences, editor)

        (occurrences, expr.getTextRange)
      case _ => (Seq.empty, elems.head.getTextRange.union(elems.last.getTextRange))

    }
    val data = ScalaIntroduceParameterData(methodLike, methodToSearchFor, elems,
      suggestedName, types, types(0), occurrences, mainOcc, replaceAll = false, argText, Some(argClauseText))
    Some(data)
  }

  private def getEnclosingMethods(expr: PsiElement): Seq[ScMethodLike] = {
    var enclosingMethodsBuilder = ArraySeq.newBuilder[ScMethodLike]
    var elem: PsiElement = expr
    while (elem != null) {
      val newFun = PsiTreeUtil.getContextOfType(elem, true, classOf[ScFunctionDefinition], classOf[ScClass])
      newFun match {
        case f@ScFunctionDefinition.withBody(body) if PsiTreeUtil.isContextAncestor(body, expr, false) =>
          enclosingMethodsBuilder += f
        case cl: ScClass => enclosingMethodsBuilder ++= cl.constructor
        case _ =>
      }
      elem = newFun
    }

    val enclosingMethods = enclosingMethodsBuilder.result()
    if (enclosingMethods.size > 1) {
      val methodsNotImplementingLibraryInterfaces = enclosingMethods.filter {
        case f: ScFunctionDefinition if f.superMethods.exists(isLibraryInterfaceMethod) => false
        case _ => true
      }
      if (methodsNotImplementingLibraryInterfaces.nonEmpty) methodsNotImplementingLibraryInterfaces
      else enclosingMethods
    } else enclosingMethods
  }

  def createDialog(data: ScalaIntroduceParameterData)
                  (implicit project: Project): ScalaIntroduceParameterDialog = {
    val paramInfo = new ScalaParameterInfo(data.paramName, -1, data.tp, project, false, false, data.defaultArg, isIntroducedParameter = true)
    val descriptor = createMethodDescriptor(data.methodToSearchFor, paramInfo)
    new ScalaIntroduceParameterDialog(descriptor, data)
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
      case (f: ScFunctionDefinition) && ContainingClass(_: ScNewTemplateDefinition) => s"${f.name} (in anonymous class)"
      case (f: ScFunctionDefinition) && ContainingClass(c) => s"${f.name} (in ${c.name})"
      case f: ScFunctionDefinition => s"${f.name}"
    }
  }

  private def toHighlight(e: PsiElement) = e match {
    case pc: ScPrimaryConstructor => pc.containingClass.extendsBlock
    case _ => e
  }

  private def afterMethodChoosing(element: PsiElement)
                                 (action: ScMethodLike => Unit)
                                 (implicit project: Project = element.getProject, editor: Editor): Unit = {
    val validEnclosingMethods = getEnclosingMethods(element)
    if (validEnclosingMethods.size > 1 && !ApplicationManager.getApplication.isUnitTestMode) {
      showChooser[ScMethodLike](editor, validEnclosingMethods, action,
        ScalaBundle.message("choose.function.for.refactoring", REFACTORING_NAME), getTextForElement, toHighlight)
    }
    else if (validEnclosingMethods.size == 1 || ApplicationManager.getApplication.isUnitTestMode) {
      action(validEnclosingMethods.head)
    } else {
      showErrorHint(ScalaBundle.message("cannot.refactor.no.function"), REFACTORING_NAME)
    }
  }

  private def isLibraryInterfaceMethod(method: PsiMethod): Boolean = {
    (method.hasModifierPropertyScala(PsiModifier.ABSTRACT) || method.is[ScFunctionDefinition]) &&
      !method.getManager.isInProject(method)
  }

  private def haveReturnStmts(elems: Iterable[PsiElement]): Boolean = {
    for {
      elem <- elems
      ret@(r: ScReturn) <- elem.depthFirst()
    } {
      if (ret.method.isEmpty || !elem.isAncestorOf(ret.method.get))
        return true
    }
    false
  }
}

object ScalaIntroduceParameterHandler {
  val REFACTORING_NAME: String = ScalaBundle.message("introduce.parameter.title")
}
