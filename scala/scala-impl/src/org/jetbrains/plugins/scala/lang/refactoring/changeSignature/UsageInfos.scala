package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.psi.{PsiElement, PsiMethod, PsiNamedElement, PsiReference}
import com.intellij.refactoring.changeSignature.{ChangeInfo, JavaChangeInfo}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern, ScInfixPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScPrimaryConstructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.light.isWrapper
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo

/**
 * Nikolay.Tropin
 * 2014-08-12
 */

private[changeSignature] trait ScalaNamedElementUsageInfo {
  this: UsageInfo =>

  def namedElement: ScNamedElement

  val paramClauses: Option[ScParameters] = namedElement match {
    case fun: ScFunction => Some(fun.paramClauses)
    case cl: ScClass => cl.clauses
    case _ => None
  }
  val scParams: Seq[ScParameter] = paramClauses.toSeq.flatMap(_.params)
  val parameters: Seq[Parameter] = scParams.map(Parameter(_))
  val defaultValues: Seq[Option[String]] = scParams.map(_.getActualDefaultExpression.map(_.getText))
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

private[changeSignature] case class FunUsageInfo(override val namedElement: ScFunction)
        extends UsageInfo(namedElement) with ScalaNamedElementUsageInfo

private[changeSignature] case class PrimaryConstructorUsageInfo(pc: ScPrimaryConstructor)
        extends {
          override val namedElement = pc.containingClass.asInstanceOf[ScClass]
        } with UsageInfo(pc) with ScalaNamedElementUsageInfo

private[changeSignature] case class OverriderValUsageInfo(override val namedElement: ScBindingPattern)
        extends UsageInfo(namedElement) with ScalaNamedElementUsageInfo

private[changeSignature] case class OverriderClassParamUsageInfo(override val namedElement: ScClassParameter)
        extends UsageInfo(namedElement) with ScalaNamedElementUsageInfo

private[changeSignature] trait MethodUsageInfo {
  def expr: ScExpression
  def argsInfo: OldArgsInfo
  def ref: ScReference
  def method: PsiNamedElement = ref.resolve() match {
    case e: PsiNamedElement => e
    case _ => throw new IllegalArgumentException("Found reference does not resolve")
  }
}

private[changeSignature] case class MethodCallUsageInfo(override val ref: ScReferenceExpression, call: ScMethodCall)
        extends UsageInfo(call) with MethodUsageInfo {

  private val resolveResult = Option(ref).flatMap(_.bind())
  val substitutor: Option[ScSubstitutor] = resolveResult.map(_.substitutor)
  override val expr: ScExpression = call
  override val argsInfo: OldArgsInfo = OldArgsInfo(allArgs(call), method)

  private def allArgs(call: ScMethodCall): collection.Seq[ScExpression] = {
    call.getInvokedExpr match {
      case mc: ScMethodCall => allArgs(mc) ++ call.argumentExpressions
      case _ => call.argumentExpressions
    }
  }
}

private[changeSignature] case class RefExpressionUsage(refExpr: ScReferenceExpression)
        extends UsageInfo(refExpr: PsiReference) with MethodUsageInfo {
  override val expr: ScExpression = refExpr
  override val ref: ScReference = refExpr
  override val argsInfo: OldArgsInfo = OldArgsInfo(Seq.empty, method)
}

private[changeSignature] case class InfixExprUsageInfo(infix: ScInfixExpr)
        extends UsageInfo(infix) with MethodUsageInfo {
  override val expr: ScExpression = infix
  override val ref: ScReference = infix.operation
  override val argsInfo: OldArgsInfo = OldArgsInfo(infix.argumentExpressions, method)
}

private[changeSignature] case class PostfixExprUsageInfo(postfix: ScPostfixExpr)
        extends UsageInfo(postfix) with MethodUsageInfo {
  override val expr: ScExpression = postfix
  override val ref: ScReference = postfix.operation
  override val argsInfo: OldArgsInfo = OldArgsInfo(postfix.argumentExpressions, method)
}

private[changeSignature] case class ConstructorUsageInfo(override val ref: ScReference, constrInvocation: ScConstructorInvocation)
        extends UsageInfo(constrInvocation) with MethodUsageInfo {

  private val resolveResult = Option(ref).flatMap(_.bind())
  val substitutor: Option[ScSubstitutor] = resolveResult.map(_.substitutor)
  override val expr: ScExpression = {
    val newText = s"new ${constrInvocation.getText}"
    createExpressionFromText(newText)(constrInvocation.getManager)
  }
  override val argsInfo: OldArgsInfo = OldArgsInfo(constrInvocation.arguments.flatMap(_.exprs), method)
}

private[changeSignature] case class AnonFunUsageInfo(expr: ScExpression, ref: ScReferenceExpression)
        extends UsageInfo(expr)

private[changeSignature] object isAnonFunUsage {
  def unapply(ref: ScReferenceExpression): Option[AnonFunUsageInfo] = {
    ref match {
      case ChildOf(mc: MethodInvocation) if mc.argumentExpressions.exists(ScUnderScoreSectionUtil.isUnderscore) => Some(AnonFunUsageInfo(mc, ref))
      case ChildOf(und: ScUnderscoreSection) => Some(AnonFunUsageInfo(und, ref))
      case ResolvesTo(m: PsiMethod) && ChildOf(elem)
        if m.getParameterList.getParametersCount > 0 && !elem.isInstanceOf[MethodInvocation] =>
        Some(AnonFunUsageInfo(ref, ref))
      case _ => None
    }
  }
}

private[changeSignature] case class ParameterUsageInfo(oldIndex: Int, newName: String, ref: ScReference)
        extends UsageInfo(ref: PsiElement)

private[changeSignature] case class ImportUsageInfo(imp: ScReference) extends UsageInfo(imp: PsiElement)

private[changeSignature] trait PatternUsageInfo {
  def pattern: ScPattern
}

private[changeSignature] case class ConstructorPatternUsageInfo(override val pattern: ScConstructorPattern) extends UsageInfo(pattern) with PatternUsageInfo

private[changeSignature] case class InfixPatternUsageInfo(override val pattern: ScInfixPattern) extends UsageInfo(pattern) with PatternUsageInfo

private[changeSignature] object UsageUtil {

  def scalaUsage(usage: UsageInfo): Boolean = usage match {
    case ScalaNamedElementUsageInfo(_) | _: ParameterUsageInfo | _: MethodUsageInfo | _: AnonFunUsageInfo | _: ImportUsageInfo => true
    case _ => false
  }

  def substitutor(usage: ScalaNamedElementUsageInfo): ScSubstitutor = usage match {
    case ScalaNamedElementUsageInfo(funUsage: FunUsageInfo) =>
      funUsage.namedElement match {
        case fun: ScFunction =>
          fun.superMethodAndSubstitutor match {
            case Some((_, subst)) => subst
            case _ => ScSubstitutor.empty
          }
        case _ => ScSubstitutor.empty
      }
    case _ => ScSubstitutor.empty
  }

  def returnType(change: ChangeInfo, usage: ScalaNamedElementUsageInfo): Option[ScType] = {
    val newType = change match {
      case sc: ScalaChangeInfo => sc.newType
      case jc: JavaChangeInfo =>
        val method = jc.getMethod
        val psiType = jc.getNewReturnType.getType(method.getParameterList, method.getManager)
        psiType.toScType()(method.projectContext)
      case _ => return None
    }

    val substType = substitutor(usage)(newType)
    Some(substType)
  }

}

private[changeSignature] case class OldArgsInfo(args: collection.Seq[ScExpression], namedElement: PsiNamedElement) {

  val byOldParameterIndex: Map[Int, collection.Seq[ScExpression]] = {
    args.groupBy(a => ScalaPsiUtil.parameterOf(a).fold(-1)(_.index))
            .updated(-1, Seq.empty)
  }

}
