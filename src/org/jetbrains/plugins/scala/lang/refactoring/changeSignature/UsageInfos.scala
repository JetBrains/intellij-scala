package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import com.intellij.refactoring.changeSignature.{ChangeInfo, JavaChangeInfo}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.light.isWrapper
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo

/**
 * Nikolay.Tropin
 * 2014-08-12
 */

private[changeSignature] trait ScalaNamedElementUsageInfo {
  this: UsageInfo =>

  def namedElement: ScNamedElement
  def parameters: Seq[Parameter] = Seq.empty
  def defaultValues: Seq[Option[String]] = Seq.empty
}

private[changeSignature] object ScalaNamedElementUsageInfo {
  def unapply(ou: UsageInfo): Option[ScalaNamedElementUsageInfo] = {
    ou match {
      case scUsage: ScalaNamedElementUsageInfo => Some(scUsage)
      case _ => None
    }
  }

  def apply(named: PsiNamedElement): UsageInfo with ScalaNamedElementUsageInfo = {
    val unwrapped = named match {
      case isWrapper(elem) => elem
      case _ => named
    }
    unwrapped match {
      case fun: ScFunction => FunUsageInfo(fun)
      case bp: ScBindingPattern => OverriderValUsageInfo(bp)
      case cp: ScClassParameter => OverriderClassParamUsageInfo(cp)
      case _ => null
    }
  }
}

private[changeSignature] case class FunUsageInfo(namedElement: ScFunction)
        extends UsageInfo(namedElement) with ScalaNamedElementUsageInfo {
  
  val parameterClauses = namedElement.paramClauses.clauses

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

private[changeSignature] case class OverriderValUsageInfo(namedElement: ScBindingPattern)
        extends UsageInfo(namedElement) with ScalaNamedElementUsageInfo

private[changeSignature] case class OverriderClassParamUsageInfo(namedElement: ScClassParameter)
        extends UsageInfo(namedElement) with ScalaNamedElementUsageInfo

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

private[changeSignature] case class ParameterUsageInfo(oldIndex: Int, newName: String, ref: ScReferenceElement)
        extends UsageInfo(ref: PsiElement)

private[changeSignature] object UsageUtil {

  def invocation(usage: UsageInfo): MethodInvocation = usage match {
    case MethodCallUsageInfo(_, call) => call
    case InfixExprUsageInfo(inf) => inf
    case PostfixExprUsageInfo(post) => post
    case _ => null
  }

  def scalaUsage(usage: UsageInfo): Boolean = usage match {
    case ScalaNamedElementUsageInfo(_) | _: ParameterUsageInfo | _: MethodUsageInfo | _: MethodValueUsageInfo => true
    case _ => false
  }

  def substitutor(usage: ScalaNamedElementUsageInfo): ScSubstitutor = usage match {
    case ScalaNamedElementUsageInfo(funUsage: FunUsageInfo) =>
      funUsage.namedElement.superMethodAndSubstitutor match {
        case Some((_, subst)) => subst
        case _ => ScSubstitutor.empty
      }
    case _ => ScSubstitutor.empty
  }

  def returnType(change: ChangeInfo, usage: ScalaNamedElementUsageInfo): Option[ScType] = {
    val newType = change match {
      case sc: ScalaChangeInfo => sc.newType
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
