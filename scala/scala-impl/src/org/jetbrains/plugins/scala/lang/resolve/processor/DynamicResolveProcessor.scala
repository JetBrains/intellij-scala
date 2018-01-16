package org.jetbrains.plugins.scala.lang.resolve.processor

import scala.collection.JavaConverters._

import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.resolve.DynamicTypeReferenceResolver.getAllResolveResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

object DynamicResolveProcessor {

  val APPLY_DYNAMIC_NAMED = "applyDynamicNamed"
  val APPLY_DYNAMIC = "applyDynamic"
  val SELECT_DYNAMIC = "selectDynamic"
  val UPDATE_DYNAMIC = "updateDynamic"
  val NAMED = "Named"

  def getDynamicNameForMethodInvocation(expressions: Seq[ScExpression]): String = {
    val qualifiers = expressions.collect {
      case ScAssignStmt(reference: ScReferenceExpression, _) => reference.qualifier
    }

    if (qualifiers.exists(_.isEmpty)) APPLY_DYNAMIC_NAMED
    else APPLY_DYNAMIC
  }

  def isDynamicReference(reference: ScReferenceExpression): Boolean = {
    def qualifierType() = reference.qualifier
      .flatMap(_.getNonValueType().toOption)

    qualifierType().exists(conformsToDynamic(_, reference.getResolveScope))
  }

  def conformsToDynamic(tp: ScType, scope: GlobalSearchScope): Boolean = {
    val dynamicType = ElementScope(tp.projectContext, scope)
      .getCachedClass("scala.Dynamic")
      .map(ScDesignatorType(_))
    dynamicType.exists(tp.conforms)
  }

  def resolveDynamic(reference: ScReferenceExpression): Seq[ResolveResult] = {
    getAllResolveResult(reference).asScala
  }

  def isApplyDynamicNamed(r: ScalaResolveResult): Boolean =
    r.isDynamic && r.name == APPLY_DYNAMIC_NAMED

  private def getDynamicReturn: ScType => ScType = {
    case methodType: ScMethodType => methodType.returnType
    case ScTypePolymorphicType(methodType: ScMethodType, parameters) =>
      ScTypePolymorphicType(getDynamicReturn(methodType), parameters)
    case scType => scType
  }

  implicit class ScTypeForDynamicProcessorEx(val tp: ScType) extends AnyVal {
    def updateTypeOfDynamicCall(isDynamic: Boolean): ScType = if (isDynamic) getDynamicReturn(tp) else tp
  }
}

