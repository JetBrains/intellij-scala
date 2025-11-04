package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.TemplateUtils
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil.addParametersToTemplate
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScBooleanLiteral, ScDoubleLiteral, ScFloatLiteral, ScIntegerLiteral, ScStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createClauseFromText

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

final class AddParametersQuickfix extends PsiElementBaseIntentionAction {

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    if (element.isValid) addParameterFix(element, editor).foreach(_.apply())

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    addParameterFix(element, editor).isDefined

  override def getFamilyName: String = ScalaBundle.message("add.parameter.to.method.family.name")

  override def getText: String = ScalaBundle.message("add.parameter.to.method")

  private def addParameterFix(element: PsiElement, editor: Editor): Option[() => Unit] = {
    val argList: ScArgumentExprList = PsiTreeUtil.getParentOfType(element, classOf[ScArgumentExprList])
    if (argList == null || argList.isBlockArgs) return None

    val methodCall = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall])
    val arguments = methodCall.args.exprs

    // Find the actual function and the parameter clause that corresponds to the edited argument list
    val clause = findParamList(methodCall) match {
      case Some(clause) => clause
      case None => return None
    }

    val parameters = clause.parameters
    if (parameters.size >= arguments.size) return None

    if (!clause.isValid || !clause.isPhysical) return None

    Some(() => IntentionPreviewUtils.write { () =>
      if (!IntentionPreviewUtils.isIntentionPreviewActive) {
        val indices       = findNewParameterIndices(parameters, arguments)
        val newParamTexts = generateNewParameterTexts(arguments, indices, parameters.map(_.name))
        val newClauseText = createNewClauseText(clause.getText, newParamTexts)
        val newClause     = clause.replace(createClauseFromText(newClauseText, clause.getParent)(element.getManager)).asInstanceOf[ScParameterClause]

        val builder = new TemplateBuilderImpl(newClause)
        addParametersToTemplate(newClause, builder, p => indices.contains(newClause.parameters.indexOf(p)))
        CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newClause)
        TemplateUtils.positionCursorAndStartTemplate(newClause, builder.buildTemplate(), editor)
      }
    })
  }

  private def findParamList(call: ScMethodCall): Option[ScParameterClause] = {
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

    findFun(call, 0).flatMap {
      case (fun, idx) =>
        val clauses = fun.paramClauses.clauses
        clauses.lift(idx)
    }
  }

  private def createNewClauseText(oldClauseText: String, newParamTexts: Seq[(Int, String)]): String = {
    val paramTexts = oldClauseText
      .substring(1, oldClauseText.length - 1)
      .split(',')
      .map(_.trim)
      .filter(_.nonEmpty)
      .toList

    val newTxtList = newParamTexts.foldLeft(paramTexts) { case (pts, (i, newParamText)) =>
      val (prev, next) = pts.splitAt(i)
      prev ::: newParamText :: next
    }
    "(" + newTxtList.mkString(", ") + ")"
  }

  private def findNewParameterIndices(parameters: Seq[ScParameter], arguments: Seq[ScExpression]): Seq[Int] = {
    val buf = ArrayBuffer.empty[Int]
    var ai = 0
    var pi = 0
    while (ai < arguments.size) {
      if (pi == parameters.size) buf.addOne(ai)
      else {
        val ptr = parameters(pi).`type`().getOrNothing.toString
        val atr = argumentTypeText(arguments(ai))
        if (ptr != atr) buf.addOne(ai) else pi += 1
      }
      ai += 1
    }
    buf.toSeq
  }

  private def argumentTypeText(expr: ScExpression): String = expr match {
    case _: ScIntegerLiteral => "Int"
    case _: ScDoubleLiteral  => "Double"
    case _: ScFloatLiteral   => "Float"
    case _: ScBooleanLiteral => "Boolean"
    case _: ScStringLiteral  => "String"
    case _ => expr.getTypeAfterImplicitConversion().tr.getOrAny.toString
  }

  private def generateNewParameterTexts(arguments: Seq[ScExpression], indices: Seq[Int], paramNames: Seq[String]): Seq[(Int, String)] = {
    var index = 0
    indices.map { i =>
      index += 1
      var name = s"arg$index"
      while (paramNames.contains(name)) {
        index += 1
        name = s"arg$index"
      }
      val typeText = argumentTypeText(arguments(i))
      i -> s"$name: $typeText"
    }
  }
}
