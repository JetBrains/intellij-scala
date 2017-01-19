package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.{PsiClass, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScAssignStmt, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.DynamicTypeReferenceResolver
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.mutable.ArrayBuffer

/**
  * Resolve reference for Dynamic type via DynamicTypeReferenceResolver's implementations
  */
class DynamicResolveProcessor(referenceExpression: ScReferenceExpression) {

  import scala.collection.JavaConversions._

  def resolve(): ArrayBuffer[ResolveResult] = {
    val res = new ArrayBuffer() ++= DynamicTypeReferenceResolver.getAllResolveResult(referenceExpression)
    res
  }
}

object DynamicResolveProcessor {

  val APPLY_DYNAMIC_NAMED = "applyDynamicNamed"
  val APPLY_DYNAMIC = "applyDynamic"
  val SELECT_DYNAMIC = "selectDynamic"
  val UPDATE_DYNAMIC = "updateDynamic"
  val NAMED = "Named"

  def getDynamicReturn(tp: ScType): ScType = {
    tp match {
      case pt@ScTypePolymorphicType(mt: ScMethodType, typeArgs) => ScTypePolymorphicType(mt.returnType, typeArgs)(pt.typeSystem)
      case mt: ScMethodType => mt.returnType
      case _ => tp
    }
  }

  def getDynamicNameForMethodInvocation(call: MethodInvocation): String =
    if (call.argumentExpressions.collect {
      case statement: ScAssignStmt => statement.getLExpression
    }.exists {
      case r: ScReferenceExpression => r.qualifier.isEmpty
      case _ => false
    }) APPLY_DYNAMIC_NAMED else APPLY_DYNAMIC


  def isDynamicReference(referenceExpression: ScReferenceExpression): Boolean = {
    implicit val typeSystem: TypeSystem = referenceExpression.getProject.typeSystem

    def findDynamicCachedClass(): Option[PsiClass] =
      ScalaPsiManager.instance(referenceExpression.getProject).getCachedClass(referenceExpression.getResolveScope, "scala.Dynamic")

    def computeType(): Option[ScType] = {
      referenceExpression.qualifier.flatMap { ttype =>
        ttype.getNonValueType(TypingContext.empty) match {
          case Success(elementType: ScType, _) => Some(elementType)
          case _ => None
        }
      }
    }

    computeType().exists { elementType =>
      findDynamicCachedClass().exists { clazz =>
        elementType.conforms(ScDesignatorType(clazz))
      }
    }
  }
}

