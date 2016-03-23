package org.jetbrains.plugins.scala
package lang
package psi
package types

import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType

import scala.collection.immutable.HashSet

/**
 * Use this type if you want to resolve generics.
 * In conformance using ScUndefinedSubstitutor you can accumulate information
 * about possible generic type.
 */
case class ScUndefinedType(tpt: ScTypeParameterType) extends NonValueType {
  var level = 0

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitUndefinedType(this)
  }

  def this(tpt: ScTypeParameterType, level: Int) {
    this(tpt)
    this.level = level
  }

  def inferValueType: ValueType = tpt

  override def equivInner(r: ScType, subst: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: api.TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = subst
    r match {
      case _ if falseUndef => (false, undefinedSubst)
      case u2: ScUndefinedType if u2.level > level =>
        (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), this))
      case u2: ScUndefinedType if u2.level < level =>
        (true, undefinedSubst.addUpper((tpt.name, tpt.getId), u2))
      case u2: ScUndefinedType if u2.level == level =>
        (true, undefinedSubst)
      case rt =>
        undefinedSubst = undefinedSubst.addLower((tpt.name, tpt.getId), rt)
        undefinedSubst = undefinedSubst.addUpper((tpt.name, tpt.getId), rt)
        (true, undefinedSubst)
    }
  }
}

/**
 * This type works like undefined type, but you cannot use this type
 * to resolve generics. It's important if two local type
 * inferences work together.
 */
case class ScAbstractType(tpt: ScTypeParameterType, lower: ScType, upper: ScType) extends ScalaType with NonValueType {
  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1) {
      hash = (upper.hashCode() * 31 + lower.hashCode()) * 31 + tpt.args.hashCode()
    }
    hash
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case ScAbstractType(oTpt, oLower, oUpper) =>
        lower.equals(oLower) && upper.equals(oUpper) && tpt.args.equals(oTpt.args)
      case _ => false
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: api.TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    r match {
      case _ if falseUndef => (false, uSubst)
      case rt =>
        var t: (Boolean, ScUndefinedSubstitutor) = r.conforms(upper, uSubst)
        if (!t._1) return (false, uSubst)
        t = lower.conforms(r, t._2)
        if (!t._1) return (false, uSubst)
        (true, t._2)
    }
  }

  def inferValueType = tpt

  def simplifyType: ScType = {
    if (upper.equiv(Any)) lower else if (lower.equiv(Nothing)) upper else lower
  }

  override def removeAbstracts = simplifyType

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
          ScAbstractType(tpt.recursiveUpdate(update, newVisited).asInstanceOf[ScTypeParameterType], lower.recursiveUpdate(update, newVisited),
            upper.recursiveUpdate(update, newVisited))
        }
        catch {
          case cce: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        try {
          ScAbstractType(tpt.recursiveVarianceUpdateModifiable(newData, update, variance).asInstanceOf[ScTypeParameterType],
            lower.recursiveVarianceUpdateModifiable(newData, update, -variance),
            upper.recursiveVarianceUpdateModifiable(newData, update, variance))
        }
        catch {
          case cce: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitAbstractType(this)
  }
}