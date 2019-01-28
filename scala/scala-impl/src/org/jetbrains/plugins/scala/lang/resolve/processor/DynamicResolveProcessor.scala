package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiReference, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.resolve.{DynamicTypeReferenceResolver, ScalaResolveResult}

import scala.collection.JavaConverters

object DynamicResolveProcessor {

  val APPLY_DYNAMIC_NAMED = "applyDynamicNamed"
  val APPLY_DYNAMIC = "applyDynamic"
  val SELECT_DYNAMIC = "selectDynamic"
  val UPDATE_DYNAMIC = "updateDynamic"
  val NAMED = "Named"

  def getDynamicNameForMethodInvocation(expressions: Seq[ScExpression]): String = {
    val qualifiers = expressions.collect {
      case ScAssignment(reference: ScReferenceExpression, _) => reference.qualifier
    }

    if (qualifiers.exists(_.isEmpty)) APPLY_DYNAMIC_NAMED
    else APPLY_DYNAMIC
  }

  object DynamicReference {

    def unapply(reference: PsiReference): Option[Seq[ResolveResult]] = reference match {
      case expression: ScReferenceExpression if hasValidType(expression) =>
        import JavaConverters._
        val results = DynamicTypeReferenceResolver.getAllResolveResult(expression).asScala
        Some(results)
      case _ => None
    }

    private def hasValidType(expression: ScReferenceExpression): Boolean =
      expression.qualifier
        .flatMap(_.getNonValueType().toOption)
        .exists(conformsToDynamic(_, expression.getResolveScope))
  }

  def conformsToDynamic(tp: ScType, scope: GlobalSearchScope): Boolean =
    ElementScope(tp.projectContext, scope)
      .getCachedClass("scala.Dynamic")
      .map(ScDesignatorType(_))
      .exists(tp.conforms)

  def isApplyDynamicNamed(r: ScalaResolveResult): Boolean =
    r.isDynamic && r.name == APPLY_DYNAMIC_NAMED

  def dynamicResolveProcessor(ref: ScReferenceExpression,
                              qualifier: ScExpression,
                              fromProcessor: BaseProcessor): MethodResolveProcessor = {

    import ref.projectContext

    val expressionsOrContext = ref.getContext match {
      case postfix: ScPostfixExpr => Left(postfix)
      case MethodInvocation(`ref`, expressions) => Right(expressions)
      case _ => Left(ref)
    }

    val name = expressionsOrContext match {
      case Right(expressions) => getDynamicNameForMethodInvocation(expressions)
      case Left(reference) =>
        reference.getContext match {
          case ScAssignment(`reference`, _) => UPDATE_DYNAMIC
          case _ => SELECT_DYNAMIC
        }
    }

    val emptyStringExpression = createExpressionFromText("\"\"")(qualifier.projectContext)

    fromProcessor match {
      case processor: MethodResolveProcessor =>
        new MethodResolveProcessor(qualifier, name, List(List(emptyStringExpression), expressionsOrContext.getOrElse(Seq.empty)),
          processor.typeArgElements, processor.prevTypeInfo, processor.kinds, processor.expectedOption,
          processor.isUnderscore, processor.isShapeResolve, processor.constructorResolve, processor.noImplicitsForArgs,
          processor.enableTupling, processor.selfConstructorResolve, nameArgForDynamic = Some(ref.refName))
      case _ =>
        new MethodResolveProcessor(qualifier, name,
          List(List(emptyStringExpression), expressionsOrContext.getOrElse(Seq.empty)),
          Seq.empty, Seq.empty,
          nameArgForDynamic = Some(ref.refName))
    }
  }

  private def getDynamicReturn: ScType => ScType = {
    case methodType: ScMethodType => methodType.result
    case ScTypePolymorphicType(methodType: ScMethodType, parameters) =>
      ScTypePolymorphicType(getDynamicReturn(methodType), parameters)
    case scType => scType
  }

  implicit class ScTypeForDynamicProcessorEx(val tp: ScType) extends AnyVal {
    def updateTypeOfDynamicCall(isDynamic: Boolean): ScType = if (isDynamic) getDynamicReturn(tp) else tp
  }

}

