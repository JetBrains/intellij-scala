package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.{PsiClass, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
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

