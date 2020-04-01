package org.jetbrains.plugins.scala
package lang
package psi
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Nikolay.Tropin
  * 26-Apr-18
  */

trait ScExistentialArgument extends NamedType with ValueType {
  override implicit def projectContext: ProjectContext = lower.projectContext

  def typeParameters: Seq[TypeParameter]
  def lower: ScType
  def upper: ScType

  def isLazy: Boolean

  def copyWithBounds(newLower: ScType, newUpper: ScType): ScExistentialArgument

  def initialize(): Unit = {}

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    r match {
      case arg: ScExistentialArgument =>
        val s = ScSubstitutor.bind(arg.typeParameters, typeParameters)(TypeParameterType(_))

        def update(bound: ScType): ScType = s(bound).updateRecursively {
          case `arg` => this
        }

        val updatedArgLower = update(arg.lower)
        val t = lower.equiv(updatedArgLower, constraints, falseUndef)

        if (t.isLeft)
          return ConstraintsResult.Left

        val updatedArgUpper = update(arg.upper)
        upper.equiv(updatedArgUpper, t.constraints, falseUndef)
      case _ => ConstraintsResult.Left
    }
  }

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitExistentialArgument(this)
}

object ScExistentialArgument {

  //used for representing type parameters of a java raw class type
  //it may have a reference to itself in it's bounds, so it cannot be fully initialized in constructor
  private class Deferred(override val name: String,
                         override val typeParameters: Seq[TypeParameter],
                         var lowerBound: () => ScType,
                         var upperBound: () => ScType) extends ScExistentialArgument {
    @volatile
    private var isInitialized: Boolean = false

    private def assertInitialized(): Unit = {
      if (!isInitialized)
        throw new IllegalStateException("Access to existential argument methods before initialization")
    }

    private var _lower: ScType = _
    private var _upper: ScType = _

    override def initialize(): Unit = if (!isInitialized) {
      _lower = lowerBound()
      _upper = upperBound()

      lowerBound = null
      upperBound = null

      isInitialized = true
    }

    override def lower: ScType = {
      assertInitialized()
      _lower
    }

    override def upper: ScType = {
      assertInitialized()
      _upper
    }

    override def isLazy: Boolean = true

    override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
      assertInitialized()
      super.equivInner(r, constraints, falseUndef)
    }

    //todo: how to properly implement it without introducing recursion and breaking hashCode/equals ?
    override def copyWithBounds(newLower: ScType, newUpper: ScType): ScExistentialArgument = this
  }

  private case class FromTypeAlias(ta: ScTypeAlias) extends ScExistentialArgument {
    override val name: String = ta.name

    override def typeParameters: Seq[TypeParameter] = ta.typeParameters.map(TypeParameter(_))
    override lazy val lower: ScType = ta.lowerBound.getOrNothing
    override lazy val upper: ScType = ta.upperBound.getOrAny

    override def isLazy: Boolean = true

    override def copyWithBounds(newLower: ScType, newUpper: ScType): ScExistentialArgument = {
      if (newLower != lower || newUpper != upper)
        Complete(name, typeParameters, newLower, newUpper)
      else this //we shouldn't create `Complete` instance, because it'll break equals/hashcode
    }
  }

  private case class Complete(override val name: String,
                              override val typeParameters: Seq[TypeParameter],
                              override val lower: ScType,
                              override val upper: ScType)

    extends ScExistentialArgument {

    override def isLazy: Boolean = false

    override def copyWithBounds(newLower: ScType, newUpper: ScType): ScExistentialArgument =
      Complete(name, typeParameters, newLower, newUpper)
  }

  def apply(ta: ScTypeAlias): ScExistentialArgument = FromTypeAlias(ta)

  def apply(name: String, typeParameters: Seq[TypeParameter], lower: ScType, upper: ScType): ScExistentialArgument =
    Complete(name, typeParameters, lower, upper)

  def deferred(name: String,
               typeParameters: Seq[TypeParameter],
               lower: () => ScType,
               upper: () => ScType): ScExistentialArgument = new Deferred(name, typeParameters, lower, upper)

  def unapply(arg: ScExistentialArgument): Option[(String, Seq[TypeParameter], ScType, ScType)] =
    Some((arg.name, arg.typeParameters, arg.lower, arg.upper))

  def usedMoreThanOnce(tp: ScType): Set[ScExistentialArgument] = {
    var used = Set.empty[ScExistentialArgument]
    var result = Set.empty[ScExistentialArgument]
    tp.recursiveUpdate {
      case arg: ScExistentialArgument =>
        if (used(arg)) {
          result += arg
          Stop
        }
        else {
          used += arg
          ProcessSubtypes
        }
      case _: ScExistentialType =>
        Stop
      case _ =>
        ProcessSubtypes
    }
    result
  }

}