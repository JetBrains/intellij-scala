package org.jetbrains.plugins.scala.lang.refactoring.inline

import com.intellij.lang.refactoring.InlineHandler
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.extensions.{ObjectExt, Parent, TraversableExt}
import org.jetbrains.plugins.scala.format.{Injection, InterpolatedStringFormatter, InterpolatedStringParser}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._

import scala.collection.JavaConverters._

class ScalaInliner extends InlineHandler.Inliner {

  override def getConflicts(reference: PsiReference, referenced: PsiElement): MultiMap[PsiElement, String] = MultiMap.empty()

  override def inlineUsage(usage: UsageInfo, referenced: PsiElement): Unit = {
    val newValue = usage.getReference.getElement match {
      case Parent(call: ScMethodCall) if call.argumentExpressions.isEmpty =>
        replace(call, referenced)
      case Parent(call: ScMethodCall) =>
        replaceFunctionCall(call, referenced)
      case expr: ScExpression =>
        replace(expr, referenced)
      case Parent(typeElement: ScTypeElement) =>
        replace(typeElement, referenced)
      case _ => None
    }
    newValue.foreach(postProcess)
  }

  private def replaceFunctionCall(call: ScMethodCall, referenced: PsiElement): Option[ScExpression] = {
    referenced match {
      case function: ScFunctionDefinition =>
        val replacement = replacementForCall(call, function)
        Some(call.replaceExpression(replacement, removeParenthesis = true))
      case _ =>
        None
    }
  }

  private def replacementForCall(call: ScMethodCall, function: ScFunctionDefinition): ScExpression = {
    val funBodyCopy = function.body match {
      case Some(body) =>
        ScalaPsiElementFactory.createExpressionWithContextFromText(body.getText, function, body)
      case _ => return call
    }
    val paramToReplacement = call.matchedParameters.flatMap {
      case (expr, p) => p.paramInCode.map((_, expr))
    }

    val scope = new LocalSearchScope(funBodyCopy)

    val refToReplacement = paramToReplacement.flatMap { case (p, expr) =>
      ReferencesSearch.search(p, scope).asScala.filterBy[ScReferenceExpression].map(ref => (ref.asInstanceOf[ScExpression], expr))
    }.toMap

    unparExpr(replaceAll(funBodyCopy, refToReplacement))
  }


  private def replace(expr: ScExpression, referenced: PsiElement): Option[ScExpression] = {
    val replacementExpr = referenced match {
      case bp: ScBindingPattern =>
        PsiTreeUtil.getParentOfType(bp, classOf[ScDeclaredElementsHolder]) match {
          case v@ScPatternDefinition.expr(e) if v.declaredElements == Seq(bp) => Some(unparExpr(e))
          case v@ScVariableDefinition.expr(e) if v.declaredElements == Seq(bp) => Some(unparExpr(e))
          case _ => None
        }
      case funDef: ScFunctionDefinition if funDef.parameters.isEmpty => funDef.body.map(unparExpr)
      case funDef: ScFunctionDefinition =>
        unparExpr(expr).asOptionOf[ScMethodCall]
          .map(replacementForCall(_, funDef))
      case _ => None
    }
    replacementExpr.map { replacement =>
      expr match {
        case isInjectionIn(intrp) =>
          val newLiteral = withInjectionsReplaced(intrp, Map(expr -> replacement))
          intrp.replaceExpression(newLiteral, removeParenthesis = true)
        case _ =>
          expr.replaceExpression(replacement, removeParenthesis = true)
      }
    }
  }

  private def replace(te: ScTypeElement, referenced: PsiElement): Option[PsiElement] = {
    referenced match {
      case ta: ScTypeAliasDefinition =>
        ta.aliasedTypeElement.map(te.replace)
      case _ => None
    }
  }

  private object isInjectionIn {
    def unapply(ref: ScExpression): Option[ScInterpolatedStringLiteral] = ref.getParent match {
      case literal: ScInterpolatedStringLiteral if !literal.reference.contains(ref) => Some(literal)
      case _ => None
    }
  }

  private def replaceAll(expr: ScExpression, refToReplacement: Map[ScExpression, ScExpression]): ScExpression = {
    val (injections, regular) = refToReplacement.partition {
      case (isInjectionIn(_), _) => true
      case _ => false
    }
    val injectionReplacements = injections.groupBy(_._1.getParent.asInstanceOf[ScExpression]).map {
      case (intrp, map) => (intrp, withInjectionsReplaced(intrp, map))
    }

    if (regular.keySet.contains(expr))
      regular(expr)
    else if (injectionReplacements.keySet.contains(expr))
      injectionReplacements(expr)
    else {
      (regular.toSeq ++ injectionReplacements.toSeq).foreach {
        case (oldExpr, newExpr) => oldExpr.replaceExpression(newExpr, removeParenthesis = true)
      }
      expr
    }
  }

  private def withInjectionsReplaced(intrp: ScExpression, refToReplacement: Map[ScExpression, ScExpression]): ScExpression = {
    import intrp.projectContext

    InterpolatedStringParser.parse(intrp) match {
      case Some(parts) =>
        val newParts = parts.map {
          case Injection(ref: ScReferenceExpression, s) => Injection(refToReplacement.getOrElse(ref, ref), s)
          case p => p
        }
        val newText = InterpolatedStringFormatter.format(newParts)
        createExpressionFromText(newText)
      case _ =>
        intrp
    }
  }

  private def postProcess(newValue: PsiElement) = {
    val project = newValue.getProject
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    highlightOccurrences(Seq(newValue))(project, editor)
    CodeStyleManager.getInstance(project).reformatRange(newValue.getContainingFile, newValue.getTextRange.getStartOffset - 1,
      newValue.getTextRange.getEndOffset + 1) //to prevent situations like this 2 ++2 (+2 was inlined)
  }
}
