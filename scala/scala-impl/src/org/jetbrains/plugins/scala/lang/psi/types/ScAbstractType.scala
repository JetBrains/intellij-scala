package org.jetbrains.plugins.scala
package lang
package psi
package types

import java.util.Objects

import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{RecursiveUpdateException, Update}
import org.jetbrains.plugins.scala.project.ProjectContext



/**
 * This type works like undefined type, but you cannot use this type
 * to resolve generics. It's important if two local type
 * inferences work together.
 */
case class ScAbstractType(parameterType: TypeParameterType, lower: ScType, upper: ScType) extends ScalaType with NonValueType {

  override implicit def projectContext: ProjectContext = parameterType.projectContext

  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1)
      hash = Objects.hash(parameterType.arguments, upper, lower)

    hash
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case ScAbstractType(oTpt, oLower, oUpper) =>
        lower.equals(oLower) && upper.equals(oUpper) &&
          parameterType.arguments.equals(oTpt.arguments)
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

  def inferValueType: TypeParameterType = parameterType

  def simplifyType: ScType = {
    if (upper.equiv(Any)) lower else if (lower.equiv(Nothing)) upper else lower
  }

  override def removeAbstracts: ScType = simplifyType

  override def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): ScAbstractType = {
    try {
      ScAbstractType(
        parameterType.recursiveUpdateImpl(updates, visited).asInstanceOf[TypeParameterType],
        lower.recursiveUpdateImpl(updates, visited),
        upper.recursiveUpdateImpl(updates, visited)
      )
    }
    catch {
      case _: ClassCastException => throw new RecursiveUpdateException
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Variance, T) => (Boolean, ScType, T),
                                                    v: Variance = Covariant, revertVariances: Boolean = false): ScType = {
    update(this, v, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        try {
          ScAbstractType(
            parameterType.recursiveVarianceUpdateModifiable(newData, update, v).asInstanceOf[TypeParameterType],
            lower.recursiveVarianceUpdateModifiable(newData, update, -v),
            upper.recursiveVarianceUpdateModifiable(newData, update, v))
        }
        catch {
          case _: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitAbstractType(this)
    case _ =>
  }
}
