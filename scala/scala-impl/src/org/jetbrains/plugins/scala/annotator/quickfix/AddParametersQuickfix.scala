package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiFile, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.TemplateUtils
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil.addParametersToTemplate
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScArgumentExprList, ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createClauseFromText
import org.jetbrains.plugins.scala.lang.psi.types.{TypePresentationContext, api}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.mutable

final class AddParametersQuickfix(argExprList: SmartPsiElementPointer[ScArgumentExprList]) extends BaseIntentionAction {
  // We calculate _isAvailable when creating the quickfix because we need to search for the function definition,
  // which is not superfast. And isAvailable is supposed to run on the edt
  private val (_isAvailable, text) = {
    getInfo.map { case (argList, clause, fun) =>
      val arguments = argList.exprs
      val parameters = clause.parameters
      val addMultiple = arguments.length - parameters.length >= 2
      val text = fun.name match {
        case null =>
          if (addMultiple) ScalaBundle.message("add.parameters.to.method")
          else ScalaBundle.message("add.parameter.to.method")
        case name =>
          if (addMultiple) ScalaBundle.message("add.parameters.to.method.named", name)
          else ScalaBundle.message("add.parameter.to.method.named", name)
      }
      (true, text)
    }.getOrElse((false, ScalaBundle.message("add.parameters.to.method")))
  }

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    file.isValid && _isAvailable

  override def getFamilyName: String = ScalaBundle.message("add.parameters.to.method")

  override def getText: String = text

  override def invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = {
    for ((argList, clause, _) <- getInfo if psiFile.isValid) {
      IntentionPreviewUtils.write { () =>
        if (!IntentionPreviewUtils.isIntentionPreviewActive) {
          implicit val tpc: TypePresentationContext = clause
          implicit val pCtx: ProjectContext = clause
          val arguments = argList.exprs
          val parameters = clause.parameters

          val injectionPositions = findInjectionPositions(arguments, parameters)
          val newParamTexts = generateNewParameterTexts(injectionPositions, parameters.map(_.name))
          val newClauseText = createNewClauseText(clause, newParamTexts)
          val newClause = clause.replace(createClauseFromText(newClauseText, clause.getParent)).asInstanceOf[ScParameterClause]

          val isNewParam = injectionPositions.map { case (_, _, idx) => idx }.toSet
          val builder = new TemplateBuilderImpl(newClause)
          addParametersToTemplate(newClause, builder, p => isNewParam(newClause.parameters.indexOf(p)))
          CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newClause)
          TemplateUtils.positionCursorAndStartTemplate(newClause, builder.buildTemplate(), editor)
        }
      }
    }
  }

  private def getInfo: Option[(ScArgumentExprList, ScParameterClause, ScFunction)] = {
    val argExprList = this.argExprList.getElement
    if (argExprList == null || !argExprList.isValid || argExprList.isBlockArgs) return None

    val methodCall = PsiTreeUtil.getParentOfType(argExprList, classOf[ScMethodCall])
    if (methodCall == null) return None

    // Find the actual function and the parameter clause that corresponds to the edited argument list
    val (fun, clause) = findParamList(methodCall) match {
      case Some((fun, clause)) if !clause.hasRepeatedParam => (fun, clause)
      case _ => return None
    }

    val arguments = argExprList.exprs
    val parameters = clause.parameters

    if (arguments.length > parameters.length && clause.isValid && clause.isPhysical) {
      Some((argExprList, clause, fun))
    } else {
      None
    }
  }

  private def findParamList(call: ScMethodCall): Option[(ScFunction, ScParameterClause)] = {
    // Walk down via getEffectiveInvokedExpr until we reach a ScMethodCall
    // that actually resolves to a function.
    @tailrec
    def findFun(call: ScMethodCall, idxAcc: Int): Option[(ScFunction, Int)] =
      call.target match {
        case Some(target) =>
          target.element match {
            case fun: ScFunction if !fun.isInCompiledFile => Some(fun, idxAcc)
            case _ => None
          }
        case None =>
          call.getEffectiveInvokedExpr match {
            case call: ScMethodCall => findFun(call, idxAcc + 1)
            case _ => None
          }
      }

    for {
      (fun, idx) <- findFun(call, 0)
      clauses = fun.paramClauses.clauses
      clause <- clauses.lift(idx)
    } yield (fun, clause)
  }

  /**
   * Determines where the new parameters should be inserted in the parameter list.
   *
   * To do this, we use a modified version of the levenshtein distance,
   * where we only allow going through the arguments (right) and replace params with args (diagonal)
   * and prioritize keeping existing parameters at the front.
   *
   * Example:
   *   Let's say that a, b, c, d, e, and f are types that are only conforming to themselves.
   *   This gives us a matrix like this (empty fields are irrelevant and will not be calculated):
   *
   *   {{{
   *           a   b   c   d   e   <- argument types
   *       0 - 1   3
   *             \
   *     b     1   2 - 3
   *                     \
   *     d         2   3   3
   *                         \
   *     f             3   4   4
   *     ^
   *   param types
   *   }}}
   *
   *   The resulting parameters are [new a, old b, new c, old d, old f (type mismatch)]
   */
  private def findInjectionPositions(arguments: Seq[ScExpression], parameters: Seq[ScParameter])
                                    (implicit projectContext: ProjectContext): Seq[(ScExpression, Option[ScParameter], Int)] = {
    val indexedArgs = (api.Nothing +: arguments.map(_.getTypeAfterImplicitConversion().tr.getOrNothing)).toArray
    val indexedParams = (api.Any +: parameters.map(_.`type`().getOrAny)).toArray

    case class Entry(cost: Int, path: List[(ScExpression, Option[ScParameter], Int)])
    val cache = mutable.Map.empty[(Int, Int), Entry]
    cache.put((0, 0), Entry(0, List.empty))

    def findPath(pos: (Int, Int)): Entry = cache.getOrElseUpdate(pos, {
      val (argIdx, paramIdx) = pos
      lazy val beforeTakingArgument = findPath((argIdx - 1, paramIdx))
      lazy val beforeReplacing = findPath((argIdx - 1, paramIdx - 1))

      def takeArgumentCost = beforeTakingArgument.cost + 1
      def replaceUnfittingCost = beforeReplacing.cost + 1
      def replaceFittingCost = beforeReplacing.cost

      lazy val replaceCost =
        if (indexedArgs(argIdx).conforms(indexedParams(paramIdx)))
          replaceFittingCost
        else
          replaceUnfittingCost

      val useReplace = {
        if (paramIdx == 0) false
        else if (argIdx == paramIdx) true
        else replaceCost < takeArgumentCost
      }

      if (useReplace) {
        Entry(replaceCost, beforeReplacing.path)
      } else {
        val argument = arguments(argIdx - 1)
        val anchor = parameters.lift(paramIdx - 1)
        Entry(takeArgumentCost, (argument, anchor, argIdx - 1) :: beforeTakingArgument.path)
      }
    })

    findPath((arguments.length, parameters.length)).path.reverse
  }

  private def createNewClauseText(oldClause: ScParameterClause, newParamTexts: Seq[(String, Option[ScParameter])]): String = {
    def insert(str: String, pos: Int, insertText: String): String = {
      val (before, after) = str.splitAt(pos)
      before + insertText + after
    }

    val clauseText = oldClause.getText
    val firstParamIndex = clauseText.indexOf('(') + 1
    lazy val hasParameter = oldClause.parameters.nonEmpty

    newParamTexts.foldRight(clauseText) {
      case ((paramText, anchor), clauseText) =>
        anchor match {
          case Some(anchor) =>
            insert(clauseText, anchor.getTextRangeInParent.getEndOffset, ", " + paramText)
          case None =>
            if (hasParameter) insert(clauseText, firstParamIndex, paramText + ", ")
            else insert(clauseText, firstParamIndex, paramText)
        }
    }
  }

  private def generateNewParameterTexts(arguments: Seq[(ScExpression, Option[ScParameter], Int)], paramNames: Seq[String])
                                       (implicit typePresentationCtx: TypePresentationContext): Seq[(String, Option[ScParameter])] = {
    var index = 1
    @tailrec
    def mkNextArgName(): String = {
      val name = s"arg$index"
      index += 1
      if (!paramNames.contains(name)) name
      else {
        mkNextArgName()
      }
    }

    for ((argument, anchor, _) <- arguments) yield {
      val name = mkNextArgName()
      val typeText = argumentTypeText(argument)
      val paramText = s"$name: $typeText"
      (paramText, anchor)
    }
  }

  private def argumentTypeText(expr: ScExpression)(implicit typePresentationCtx: TypePresentationContext): String =
    expr.getTypeAfterImplicitConversion().tr.getOrAny.widenIfLiteral.presentableText
}

object AddParametersQuickfix {
  def from(invocation: MethodInvocation): Option[AddParametersQuickfix] =
    invocation.asOptionOf[ScMethodCall].map(from)
  def from(argExprList: ScMethodCall): AddParametersQuickfix =
    new AddParametersQuickfix(argExprList.args.createSmartPointer)
}