package org.jetbrains.plugins.scala.codeInspection.internal

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.{OperationOnCollectionInspection, Qualified, Simplification, SimplificationType, invocation, invocationText}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgument
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement.calcType
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.types.Context
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.immutable.ArraySeq

class InstanceOfInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(InstanceOfShouldBeIsInspection)
}

object InstanceOfShouldBeIsInspection extends SimplificationType() {
  override val hint: String = ScalaInspectionBundle.message("replace.with.is")

  private val `.isInstanceOf`: Qualified = invocation("isInstanceOf")

  private def typeArgConformsToBaseExprType(base: ScExpression, targ: ScTypeArgument): Boolean = {
    val conforms =
      for {
        baseType <- base.`type`().toOption.map(_.widen)
        targType <- targ.typeElement.map(_.calcType)
      } yield targType.conforms(baseType)

    conforms.getOrElse(false)
  }

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    implicit val context: Context = Context(expr)

    expr match {
      case _ if !expr.getProject.isIntellijScalaPluginProject && !isUnitTestMode => None
      case `.isInstanceOf`(base) & ScGenericCall(_, Seq(targ)) if typeArgConformsToBaseExprType(base, targ) =>
        Some(replace(expr).withText(invocationText(base, "is") + s"[${targ.getText}]").highlightRef)
      case _ =>
        None
    }
  }
}
