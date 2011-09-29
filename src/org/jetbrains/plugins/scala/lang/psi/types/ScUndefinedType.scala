package org.jetbrains.plugins.scala
package lang
package psi
package types

import nonvalue.NonValueType

/**
 * Use this type if you want to resolve generics.
 * In conformance using ScUndefinedSubstitutor you can accumulate imformation
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

  override def equivInner(r: ScType, subst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = subst
    r match {
      case _ if falseUndef => (false, undefinedSubst)
      case u2: ScUndefinedType if u2.level > level =>
        (true, undefinedSubst.addUpper((u2.tpt.name, u2.tpt.getId), this))
      case u2: ScUndefinedType if u2.level < level =>
        (true, undefinedSubst.addUpper((tpt.name, tpt.getId), u2))
      case u2: ScUndefinedType if u2.level == level =>
        (true, undefinedSubst)
      case rt => {
        undefinedSubst = undefinedSubst.addLower((tpt.name, tpt.getId), rt)
        undefinedSubst = undefinedSubst.addUpper((tpt.name, tpt.getId), rt)
        (true, undefinedSubst)
      }
    }
  }
}

/**
 * This type works like undefined type, but you cannot use this type
 * to resolve generics. It's important if two local type
 * inferences work together.
 */
case class ScAbstractType(tpt: ScTypeParameterType, lower: ScType, upper: ScType) extends NonValueType {
  def inferValueType = tpt

  def simplifyType: ScType = {
    if (upper.equiv(Any)) lower else if (lower.equiv(Nothing)) upper else lower
  }

  override def removeAbstracts = simplifyType

  override def recursiveUpdate(update: ScType => (Boolean, ScType)): ScType = {
    update(this) match {
      case (true, res) => res
      case _ =>
        try {
          ScAbstractType(tpt.recursiveUpdate(update).asInstanceOf[ScTypeParameterType], lower.recursiveUpdate(update),
            upper.recursiveUpdate(update))
        }
        catch {
          case cce: ClassCastException => throw new RecursiveUpdateException
        }
    }
  }

  override def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int): ScType = {
    update(this, variance) match {
      case (true, res) => res
      case _ =>
        try {
          ScAbstractType(tpt.recursiveVarianceUpdate(update, variance).asInstanceOf[ScTypeParameterType],
            lower.recursiveVarianceUpdate(update, -variance),
            upper.recursiveVarianceUpdate(update, variance))
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