package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.types.api.{Covariant, TypeSystem, TypeVisitor, ValueType, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.Update
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.project.ProjectContextOwner

trait ScType extends ProjectContextOwner {

  def typeSystem: TypeSystem = projectContext.typeSystem

  private var aliasType: Option[AliasType] = null

  final def isAliasType: Option[AliasType] = {
    if (aliasType == null) {
      ProgressManager.checkCanceled()
      aliasType = isAliasTypeInner
    }
    aliasType
  }

  private var unpacked: ScType = null

  final def unpackedType: ScType = {
    if (unpacked == null) {
      ProgressManager.checkCanceled()
      unpacked = unpackedTypeInner
    }
    unpacked
  }

  protected def isAliasTypeInner: Option[AliasType] = None

  override final def toString: String = ifReadAllowed(presentableText)(getClass.getSimpleName)

  def isValue: Boolean

  def isFinalType: Boolean = false

  def inferValueType: ValueType

  protected def unpackedTypeInner: ScType = ScExistentialType(this) match {
    case ScExistentialType(q, Seq())                                       => q
    case ScExistentialType(arg: ScExistentialArgument, Seq(w)) if w == arg => arg.upper
    case ex                                                                => ex
  }


  /**
   * This method is important for parameters expected type.
   * There shouldn't be any abstract type in this expected type.
   * todo rewrite with recursiveUpdate method
   */
  def removeAbstracts: ScType = this

  def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (false, uSubst)
  }

  def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): ScType = this

  def recursiveVarianceUpdate(update: (ScType, Variance) => (Boolean, ScType),
                              variance: Variance = Covariant,
                              revertVariances: Boolean = false): ScType = {
    update(this, variance) match {
      case (true, res) => res
      case _ => this
    }
  }

  def visitType(visitor: TypeVisitor)

  def typeDepth: Int = 1

  def presentableText(implicit context: TypePresentationContext): String =
    typeSystem.presentableText(this, withPrefix = true)

  def canonicalText: String = typeSystem.canonicalText(this)
}

object ScType extends recursiveUpdate.Extensions

trait NamedType extends ScType {
  val name: String

  override def presentableText(implicit context: TypePresentationContext): String = name

  override def canonicalText: String = name
}
