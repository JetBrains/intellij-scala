package org.jetbrains.plugins.scala
package lang
package psi
package types

import java.util.Objects

import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{AfterUpdate, RecursiveUpdateException, Update}
import org.jetbrains.plugins.scala.project.ProjectContext



/**
 * This type works like undefined type, but you cannot use this type
 * to resolve generics. It's important if two local type
 * inferences work together.
 */
case class ScAbstractType(typeParameter: TypeParameter, lower: ScType, upper: ScType) extends ScalaType with NonValueType {

  override implicit def projectContext: ProjectContext = typeParameter.psiTypeParameter

  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1)
      hash = Objects.hash(typeParameter, upper, lower)

    hash
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case ScAbstractType(oTypeParameter, oLower, oUpper) =>
        lower.equals(oLower) && upper.equals(oUpper) &&
          typeParameter.equals(oTypeParameter)
      case _ => false
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    r match {
      case _ if falseUndef => (false, uSubst)
      case _ =>
        var t: (Boolean, ScUndefinedSubstitutor) = r.conforms(upper, uSubst)
        if (!t._1) return (false, uSubst)
        t = lower.conforms(r, t._2)
        if (!t._1) return (false, uSubst)
        (true, t._2)
    }
  }

  def inferValueType: TypeParameterType = TypeParameterType(typeParameter)

  def simplifyType: ScType = {
    if (upper.equiv(Any)) lower else if (lower.equiv(Nothing)) upper else lower
  }

  override def removeAbstracts: ScType = simplifyType

  override def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): ScAbstractType = {
    ScAbstractType(
      typeParameter,
      lower.recursiveUpdateImpl(updates, visited),
      upper.recursiveUpdateImpl(updates, visited)
    )
  }

  override def updateSubtypesVariance(update: (ScType, Variance) => AfterUpdate,
                                      variance: Variance = Covariant,
                                      revertVariances: Boolean = false)
                                     (implicit visited: Set[ScType]): ScType = {
    ScAbstractType(
      typeParameter,
      lower.recursiveVarianceUpdate(update, -variance),
      upper.recursiveVarianceUpdate(update, variance)
    )
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitAbstractType(this)
    case _ =>
  }
}