package org.jetbrains.plugins.scala
package lang
package psi
package types

import org.jetbrains.plugins.scala.lang.psi.types.api.TypeVisitor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.collection.mutable.ArrayBuffer

trait ScType {
  private var aliasType: Option[AliasType] = null

  final def isAliasType: Option[AliasType] = {
    if (aliasType == null) {
      aliasType = isAliasTypeInner
    }
    aliasType
  }

  protected def isAliasTypeInner: Option[AliasType] = None

  override final def toString = presentableText

  def isValue: Boolean

  // TODO: Review this against SLS 3.2.1
  def isStable: Boolean = false

  def isFinalType: Boolean = false

  def inferValueType: ValueType

  def unpackedType: ScType = {
    val wildcards = new ArrayBuffer[ScExistentialArgument]
    val quantified = recursiveUpdate({
      case s: ScSkolemizedType =>
        wildcards += ScExistentialArgument(s.name, s.args, s.lower, s.upper)
        (true, ScTypeVariable(s.name))
      case t => (false, t)
    })
    if (wildcards.nonEmpty) {
      ScExistentialType(quantified, wildcards.toList).simplify()
    } else quantified
  }

  /**
   * This method is important for parameters expected type.
   * There shouldn't be any abstract type in this expected type.
   * todo rewrite with recursiveUpdate method
   */
  def removeAbstracts: ScType = this

  def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean)
                (implicit typeSystem: api.TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    (false, uSubst)
  }

  class RecursiveUpdateException extends Exception {
    override def getMessage: String = "Type mismatch after update method"
  }

  import scala.collection.immutable.{HashSet => IHashSet}

  /**
   * use 'update' to replace appropriate type part with another type
   * 'update' should return true if type changed, false otherwise.
   * To just collect info about types (see collectAbstracts) always return false
   *
   * default implementation for types, which don't contain other types.
   */
  def recursiveUpdate(update: ScType => (Boolean, ScType), visited: IHashSet[ScType] = IHashSet.empty): ScType = {
    val res = update(this)
    if (res._1) res._2
    else this
  }

  def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int = 1): ScType = {
    recursiveVarianceUpdateModifiable[Unit]((), (tp, v, T) => {
      val (newTp, newV) = update(tp, v)
      (newTp, newV, ())
    }, variance)
  }

  def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    val res = update(this, variance, data)
    if (res._1) res._2
    else this
  }

  def visitType(visitor: TypeVisitor)

  def typeDepth: Int = 1

  def presentableText: String

  def canonicalText: String
}
