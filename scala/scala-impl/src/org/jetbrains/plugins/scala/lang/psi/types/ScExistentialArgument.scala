package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType, TypeVisitor, ValueType}
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

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    r match {
      case arg: ScExistentialArgument =>
        val s = ScSubstitutor.bind(arg.typeParameters, typeParameters)(TypeParameterType(_))
        val t = lower.equiv(s(arg.lower), constraints, falseUndef)

        if (t.isLeft) return ConstraintsResult.Left

        upper.equiv(s(arg.upper), t.constraints, falseUndef)
      case _ => ConstraintsResult.Left
    }
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitExistentialArgument(this)
    case _ =>
  }
}

object ScExistentialArgument {

  //quantification may have a reference to itself in it's bound, so lower and upper calculation should be deferred
  private case class Lazy(ta: ScTypeAlias) extends ScExistentialArgument {
    override val name: String = ta.name

    def typeParameters: Seq[TypeParameter] = ta.typeParameters.map(TypeParameter(_))
    lazy val lower: ScType = ta.lowerBound.getOrNothing
    lazy val upper: ScType = ta.upperBound.getOrAny

    override def isLazy: Boolean = true

    def copyWithBounds(newLower: ScType, newUpper: ScType): ScExistentialArgument = {
      if (newLower != lower || newUpper != upper)
        Complete(name, typeParameters, newLower, newUpper)
      else this //we shouldn't create `Complete` instance, because it'll break equals/hashcode
    }
  }

  private case class Complete(name: String,
                              typeParameters: Seq[TypeParameter],
                              lower: ScType,
                              upper: ScType)

    extends ScExistentialArgument {

    override def isLazy: Boolean = false

    def copyWithBounds(newLower: ScType, newUpper: ScType): ScExistentialArgument =
      Complete(name, typeParameters, newLower, newUpper)
  }

  def apply(ta: ScTypeAlias): ScExistentialArgument = Lazy(ta)

  def apply(name: String, typeParameters: Seq[TypeParameter], lower: ScType, upper: ScType): ScExistentialArgument =
    Complete(name, typeParameters, lower, upper)

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