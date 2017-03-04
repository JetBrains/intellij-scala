package org.jetbrains.plugins.scala
package lang
package psi
package types

import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType

import scala.collection.immutable.HashSet

/**
 * This type works like undefined type, but you cannot use this type
 * to resolve generics. It's important if two local type
 * inferences work together.
 */
case class ScAbstractType(parameterType: TypeParameterType, lower: ScType, upper: ScType) extends ScalaType with NonValueType {
  override lazy val hashCode: Int = {
    (upper.hashCode() * 31 + lower.hashCode()) * 31 + parameterType.arguments.hashCode()
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case ScAbstractType(oTpt, oLower, oUpper) =>
        lower.equals(oLower) && upper.equals(oUpper) && parameterType.arguments.equals(oTpt.arguments)
      case _ => false
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
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

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    val newVisited = visited + this
    update(this) match {
      case (true, res) => res
      case _ =>
        try {
          ScAbstractType(parameterType.recursiveUpdate(update, newVisited).asInstanceOf[TypeParameterType], lower.recursiveUpdate(update, newVisited),
            upper.recursiveUpdate(update, newVisited))
        }
        catch {
          case _: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        try {
          ScAbstractType(parameterType.recursiveVarianceUpdateModifiable(newData, update, variance).asInstanceOf[TypeParameterType],
            lower.recursiveVarianceUpdateModifiable(newData, update, -variance),
            upper.recursiveVarianceUpdateModifiable(newData, update, variance))
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
