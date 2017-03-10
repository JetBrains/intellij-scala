package org.jetbrains.plugins.scala
package lang
package psi
package types

import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeVisitor, ValueType}
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

  private var unpacked: ScType = null

  final def unpackedType: ScType = {
    if (unpacked == null) {
      unpacked = unpackedTypeInner
    }
    unpacked
  }

  protected def isAliasTypeInner: Option[AliasType] = None

  override final def toString: String = presentableText

  def isValue: Boolean

  def isFinalType: Boolean = false

  def inferValueType: ValueType

  protected def unpackedTypeInner: ScType = {
    val existingWildcards = ScExistentialType.existingWildcards(this)
    val wildcards = new ArrayBuffer[ScExistentialArgument]
    val quantified = recursiveVarianceUpdateModifiable[Set[String]](Set.empty, {
      case (s: ScExistentialArgument, _, data) if !data.contains(s.name) =>
        val name = ScExistentialType.fixExistentialArgumentName(s.name, existingWildcards)
        if (!wildcards.exists(_.name == name)) wildcards += ScExistentialArgument(name, s.args, s.lower, s.upper)
        (true, ScExistentialArgument(name, s.args, s.lower, s.upper), data)
      case (ex: ScExistentialType, _, data) =>
        (false, ex, data ++ ex.boundNames)
      case (t, _, data) => (false, t, data)
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

  /**
   * use 'update' to replace appropriate type part with another type
   * 'update' should return true if type changed, false otherwise.
   * To just collect info about types (see collectAbstracts) always return false
   *
   * default implementation for types, which don't contain other types.
   */
  def recursiveUpdate(update: ScType => (Boolean, ScType), visited: Set[ScType] = Set.empty): ScType = {
    val res = update(this)
    if (res._1) res._2
    else this
  }

  def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int = 1): ScType = {
    recursiveVarianceUpdateModifiable[Unit]((), (tp, v, _) => {
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

trait NamedType extends ScType {
  val name: String

  override def presentableText: String = name

  override def canonicalText: String = name
}
