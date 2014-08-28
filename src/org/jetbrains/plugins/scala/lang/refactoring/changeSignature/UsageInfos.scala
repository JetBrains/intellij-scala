package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.incors.plaf.alloy.a.i
import com.intellij.lang.Language
import com.intellij.psi.{PsiNamedElement, PsiMethod, PsiElement, PsiReference}
import com.intellij.refactoring.changeSignature.{JavaChangeInfo, ChangeInfo}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScClassParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.light.{PsiTypedDefinitionWrapper, ScFunctionWrapper, StaticPsiTypedDefinitionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScSubstitutor}

/**
 * Nikolay.Tropin
 * 2014-08-12
 */

private[changeSignature] trait ScalaOverriderUsageInfo {
  this: UsageInfo =>

  def overrider: ScNamedElement
  def parameters: Seq[Parameter] = Seq.empty
  def defaultValues: Seq[Option[String]] = Seq.empty
}

private[changeSignature] object ScalaOverriderUsageInfo {
  def unapply(ou: UsageInfo): Option[ScalaOverriderUsageInfo] = {
    ou match {
      case scUsage: ScalaOverriderUsageInfo => return Some(scUsage)
      case _ =>
    }
    ou.getElement match {
      case f: ScFunction => Some(ScalaOverriderUsageInfo(f))
      case fw: ScFunctionWrapper => Some(ScalaOverriderUsageInfo(fw.function))
      case tw: PsiTypedDefinitionWrapper => Some(ScalaOverriderUsageInfo(tw.typedDefinition))
      case stw: StaticPsiTypedDefinitionWrapper => Some(ScalaOverriderUsageInfo(stw.typedDefinition))
      case _ => None
    }
  }

  def apply(named: ScNamedElement): ScalaOverriderUsageInfo = {
    named match {
      case fun: ScFunction => OverriderFunUsageInfo(fun)
      case bp: ScBindingPattern => OverriderValUsageInfo(bp)
      case cp: ScClassParameter => OverriderClassParamUsageInfo(cp)
      case _ => throw new IllegalArgumentException(s"Cannot create overrider usage info for element: ${named.getText}")
    }
  }
}

private[changeSignature] case class OverriderFunUsageInfo(overrider: ScFunction)
        extends UsageInfo(overrider) with ScalaOverriderUsageInfo {
  val parameterClauses = overrider.paramClauses.clauses

  override val (parameters, defaultValues) = {
    val all = for {
      clause <- parameterClauses
      param <- clause.parameters
    } yield {
      (new Parameter(param), param.getActualDefaultExpression.map(_.getText))
    }
    all.unzip
  }
}

private[changeSignature] case class OverriderValUsageInfo(overrider: ScBindingPattern)
        extends UsageInfo(overrider) with ScalaOverriderUsageInfo

private[changeSignature] case class OverriderClassParamUsageInfo(overrider: ScClassParameter)
        extends UsageInfo(overrider) with ScalaOverriderUsageInfo

private[changeSignature] trait MethodUsageInfo {
  def expr: ScExpression
  def argsInfo: OldArgsInfo
  def ref: ScReferenceExpression
  def method: PsiNamedElement = ref.resolve() match {
    case e: PsiNamedElement => e
    case _ => throw new IllegalArgumentException("Found reference does not resolve")
  }
}

private[changeSignature] case class MethodCallUsageInfo(ref: ScReferenceExpression, call: ScMethodCall)
        extends UsageInfo(call) with MethodUsageInfo {

  private val resolveResult = Option(ref).flatMap(_.bind())
  val substitutor = resolveResult.map(_.substitutor)
  val expr = call
  val argsInfo = OldArgsInfo(call.argumentExpressions, method)
}

private[changeSignature] case class RefExpressionUsage(refExpr: ScReferenceExpression)
        extends UsageInfo(refExpr: PsiReference) with MethodUsageInfo {
  val expr = refExpr
  val ref = refExpr
  val argsInfo = OldArgsInfo(Seq.empty, method)
}

private[changeSignature] case class InfixExprUsageInfo(infix: ScInfixExpr)
        extends UsageInfo(infix) with MethodUsageInfo {
  val expr = infix
  val ref = infix.operation
  val argsInfo = OldArgsInfo(infix.argumentExpressions, method)
}

private[changeSignature] case class PostfixExprUsageInfo(postfix: ScPostfixExpr)
        extends UsageInfo(postfix) with MethodUsageInfo {
  val expr = postfix
  val ref = postfix.operation
  val argsInfo = OldArgsInfo(postfix.argumentExpressions, method)
}

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
    case ScalaOverriderUsageInfo(scUsage) => scUsage.overrider.nameId
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
    case ScalaOverriderUsageInfo(_) => true
    case _ =>
      usage.getElement.getLanguage.isKindOf(Language.findInstance(classOf[ScalaLanguage]))
  }

  def substitutor(usage: ScalaOverriderUsageInfo): ScSubstitutor = usage match {
    case ScalaOverriderUsageInfo(funUsage: OverriderFunUsageInfo) =>
      funUsage.overrider.superMethodAndSubstitutor match {
        case Some((_, subst)) => subst
        case _ => ScSubstitutor.empty
      }
    case _ => ScSubstitutor.empty
  }

  def returnType(change: ChangeInfo, usage: ScalaOverriderUsageInfo): Option[ScType] = {
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

private[changeSignature] case class OldArgsInfo(args: Seq[ScExpression], namedElement: PsiNamedElement) {

  val byOldParameterIndex = {
    args.groupBy(a => ScalaPsiUtil.parameterOf(a).fold(-1)(_.index))
            .updated(-1, Seq.empty)
  }

}
