package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.resolve.DynamicTypeReferenceResolver.getAllResolveResult

import scala.collection.JavaConverters._

object DynamicResolveProcessor {

  val APPLY_DYNAMIC_NAMED = "applyDynamicNamed"
  val APPLY_DYNAMIC = "applyDynamic"
  val SELECT_DYNAMIC = "selectDynamic"
  val UPDATE_DYNAMIC = "updateDynamic"
  val NAMED = "Named"

  def getDynamicReturn: ScType => ScType = {
    case methodType: ScMethodType => methodType.returnType
    case ScTypePolymorphicType(methodType: ScMethodType, parameters) =>
      ScTypePolymorphicType(getDynamicReturn(methodType), parameters)
    case scType => scType
  }

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

    def cachedClassType() =
      ScalaPsiManager.instance(reference.getProject)
        .getCachedClass(reference.getResolveScope, "scala.Dynamic")
        .map(ScDesignatorType(_))

    qualifierType().zip(cachedClassType()).exists {
      case (qualifierType, classType) => qualifierType.conforms(classType)
    }
  }

  def resolveDynamic(reference: ScReferenceExpression): Seq[ResolveResult] = {
    getAllResolveResult(reference).asScala
  }
}

