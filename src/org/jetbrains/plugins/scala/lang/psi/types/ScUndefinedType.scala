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
  def this(tpt: ScTypeParameterType, level: Int) {
    this(tpt)
    this.level = level
  }

  def inferValueType: ValueType = tpt
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
}