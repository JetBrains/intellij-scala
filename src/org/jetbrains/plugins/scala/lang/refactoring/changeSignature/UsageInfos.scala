package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.changeSignature.{JavaChangeInfo, ChangeInfo}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScClassParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.light.{PsiTypedDefinitionWrapper, ScFunctionWrapper, StaticPsiTypedDefinitionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScSubstitutor}

/**
 * Nikolay.Tropin
 * 2014-08-12
 */

private[changeSignature] trait ScalaOverriderUsageInfo {
  def overrider: ScNamedElement
}

private[changeSignature] object ScalaOverriderUsageInfo {
  def unapply(ou: UsageInfo): Option[ScNamedElement] = {
    ou.getElement match {
      case f: ScFunction => Some(f)
      case fw: ScFunctionWrapper => Some(fw.function)
      case tw: PsiTypedDefinitionWrapper => Some(tw.typedDefinition)
      case stw: StaticPsiTypedDefinitionWrapper => Some(stw.typedDefinition)
      case _ => None
    }
  }
}

private[changeSignature] case class OverriderFunUsageInfo(overrider: ScFunction)
        extends UsageInfo(overrider) with ScalaOverriderUsageInfo

private[changeSignature] case class OverriderValUsageInfo(overrider: ScBindingPattern)
        extends UsageInfo(overrider) with ScalaOverriderUsageInfo

private[changeSignature] case class OverriderClassParamUsageInfo(overrider: ScClassParameter)
        extends UsageInfo(overrider) with ScalaOverriderUsageInfo

private[changeSignature] case class MethodCallUsageInfo(ref: ScReferenceExpression, call: ScMethodCall)
        extends UsageInfo(call) {

  private val resolveResult = Option(ref).flatMap(_.bind())
  val method = resolveResult.map(_.getActualElement)
  val substitutor = resolveResult.map(_.substitutor)
}

private[changeSignature] case class RefExpressionUsage(refExpr: ScReferenceExpression)
        extends UsageInfo(refExpr: PsiReference)

private[changeSignature] case class InfixExprUsageInfo(infix: ScInfixExpr)
        extends UsageInfo(infix)

private[changeSignature] case class PostfixExprUsageInfo(postfix: ScPostfixExpr)
        extends UsageInfo(postfix)

private[changeSignature] case class MethodValueUsageInfo(und: ScUnderscoreSection)
        extends UsageInfo(und)

private[changeSignature] case class ApplyUsageInfo(ref: ScReferenceExpression)
        extends UsageInfo(ref: PsiReference)

private[changeSignature] case class UnapplyUsageInfo(ref: ScReferenceElement)
        extends UsageInfo(ref: PsiReference)

private[changeSignature] case class ParameterUsageInfo(scParam: ScParameter, ref: ScReferenceElement)
        extends UsageInfo(ref: PsiReference)

private[changeSignature] object UsageUtil {
  def nameId(usage: UsageInfo): PsiElement = usage match {
    case ScalaOverriderUsageInfo(named) => named.nameId
    case MethodCallUsageInfo(ref, _) => ref.nameId
    case RefExpressionUsage(r) => r.nameId
    case InfixExprUsageInfo(i) => i.operation.nameId
    case PostfixExprUsageInfo(p) => p.operation.nameId
    case MethodValueUsageInfo(und) => und.bindingExpr match {
      case Some(r: ScReferenceExpression) => r.nameId
      case _ => null
    }
    case _ => null
  }

  def invocation(usage: UsageInfo): MethodInvocation = usage match {
    case MethodCallUsageInfo(_, call) => call
    case InfixExprUsageInfo(inf) => inf
    case PostfixExprUsageInfo(post) => post
    case _ => null
  }

  def scalaUsage(usage: UsageInfo): Boolean = usage match {
    case ScalaOverriderUsageInfo(named: ScNamedElement) => true
    case _ =>
      usage.getElement.getLanguage.isKindOf(Language.findInstance(classOf[ScalaLanguage]))
  }

  def substitutor(usage: UsageInfo): ScSubstitutor = usage match {
    case ScalaOverriderUsageInfo(fun: ScFunction) =>
      fun.superMethodAndSubstitutor match {
        case Some((_, subst)) => subst
        case _ => ScSubstitutor.empty
      }
    case _ => ScSubstitutor.empty
  }

  def returnType(change: ChangeInfo, usage: UsageInfo): Option[ScType] = {
    val newType = change match {
      case jc: JavaChangeInfo =>
        val method = jc.getMethod
        val javaType = jc.getNewReturnType.getType(method.getParameterList, method.getManager)
        ScType.create(javaType, method.getProject)
      case _ => return None
    }

    val substType = substitutor(usage).subst(newType)
    Some(substType)
  }

}
