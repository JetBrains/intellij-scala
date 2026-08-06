package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiReference, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor.InvocationClause
import org.jetbrains.plugins.scala.lang.resolve.{DynamicTypeReferenceResolver, ScalaResolveResult}

import scala.annotation.tailrec

object DynamicResolveProcessor {

  val APPLY_DYNAMIC_NAMED = "applyDynamicNamed"
  val APPLY_DYNAMIC       = "applyDynamic"
  val SELECT_DYNAMIC      = "selectDynamic"
  val UPDATE_DYNAMIC      = "updateDynamic"

  def getDynamicNameForMethodInvocation(expressions: Iterable[Expression]): String = {
    val qualifiers = expressions.collect {
      case ScAssignment(reference: ScReferenceExpression, _) => reference.qualifier
    }

    if (qualifiers.exists(_.isEmpty)) APPLY_DYNAMIC_NAMED
    else                              APPLY_DYNAMIC
  }

  object DynamicReference {

    def unapply(reference: PsiReference): Option[Seq[ResolveResult]] = reference match {
      case expression: ScReferenceExpression if hasValidType(expression) =>
        val results = DynamicTypeReferenceResolver.getAllResolveResult(expression)
        Some(results)
      case _ => None
    }

    private def hasValidType(expression: ScReferenceExpression): Boolean = {
      implicit val context: Context = Context(expression)

      expression.qualifier
        .flatMap(_.getNonValueType().toOption)
        .exists(conformsToDynamic(_, expression.getResolveScope))
    }
  }

  def conformsToDynamic(tp: ScType, scope: GlobalSearchScope)(implicit context: Context): Boolean =
    ElementScope(tp.projectContext, scope)
      .getCachedClass("scala.Dynamic")
      .map(ScDesignatorType(_))
      .exists(tp.conforms)

  def isApplyDynamicNamed(r: ScalaResolveResult): Boolean =
    r.isDynamic && r.name == APPLY_DYNAMIC_NAMED

  def dynamicResolveProcessor(
    ref:           ScReferenceExpression,
    qualifier:     ScExpression,
    fromProcessor: BaseProcessor
  ): MethodResolveProcessor = {
    def isSameOrApply(invoked: ScExpression, target: ScExpression): Boolean =
      invoked == target || ref.refName == CommonNames.Apply

    @tailrec
    def getContext(e: ScExpression): Either[ScExpression, Seq[ScExpression]] =
      e.getContext match {
        case postfix: ScPostfixExpr                                  => Left(postfix)
        case gen: ScGenericCall                                      => getContext(gen)
        case MethodInvocation(expr, args) if isSameOrApply(expr, e)  => Right(args)
        case _                                                       => Left(ref)
      }

    val expressionsOrContext = getContext(ref)

    val name = expressionsOrContext match {
      case Right(expressions) => getDynamicNameForMethodInvocation(expressions)
      case Left(reference) =>
        reference.getContext match {
          case ScAssignment(`reference`, _) => UPDATE_DYNAMIC
          case _                            => SELECT_DYNAMIC
        }
    }

    val emptyStringExpression = createExpressionFromText("\"\"", ref)(qualifier.projectContext)
    val args                  = Seq(Seq(emptyStringExpression), expressionsOrContext.getOrElse(Seq.empty))
    val clauses               = args.map(InvocationClause.argsOnly)

    fromProcessor match {
      case processor: MethodResolveProcessor =>
        processor.copy(
          ref               = qualifier,
          refName           = name,
          invocationClauses = clauses,
          nameArgForDynamic = Option(ref.refName)
        )
      case _ =>
        new MethodResolveProcessor(
          qualifier,
          name,
          clauses,
          Seq.empty,
          nameArgForDynamic = Some(ref.refName)
        )
    }
  }

  private def getDynamicReturn: ScType => ScType = {
    case methodType: ScMethodType => methodType.result
    case ScTypePolymorphicType(methodType: ScMethodType, parameters) =>
      ScTypePolymorphicType(getDynamicReturn(methodType), parameters)
    case scType => scType
  }

  implicit class ScTypeForDynamicProcessorEx(private val tp: ScType) extends AnyVal {
    def updateTypeOfDynamicCall(isDynamic: Boolean): ScType = if (isDynamic) getDynamicReturn(tp) else tp
  }
}

