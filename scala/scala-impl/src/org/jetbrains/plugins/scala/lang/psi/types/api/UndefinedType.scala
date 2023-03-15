package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, LeafType, ScType, ScalaTypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Use this type if you want to resolve type parameter or implicit parameter.
  * In conformance using ConstraintSystem you can accumulate information
  * about possible generic type.
  */
trait UndefinedType extends NonValueType with LeafType {

  def typeParameter: TypeParameter

  val level: Int

  val isWrappedExistential: Boolean = false

  override implicit def projectContext: ProjectContext = typeParameter.psiTypeParameter

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitUndefinedType(this)

  override def inferValueType: TypeParameterType = TypeParameterType(typeParameter)

  def dependentParameterType: Option[ValueType] = None

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult =
    if (falseUndef) ConstraintsResult.Left
    else
      `type` match {
        case _ if falseUndef                                   => constraints
        case UndefinedType(_, thatLevel) if thatLevel == level => constraints
        case UndefinedType(tp, thatLevel) if thatLevel > level =>
          constraints.withUpper(tp.typeParamId, this)
        case that: UndefinedType if that.level < level =>
          constraints.withUpper(typeParameter.typeParamId, that)
        case that =>
          val id = typeParameter.typeParamId
          constraints.withLower(id, that).withUpper(id, that)
      }
}

object UndefinedType {
  //todo undefined type should store only typeParamId
  private case class FromTypeParameter(override val typeParameter: TypeParameter, override val level: Int = 0) extends UndefinedType

  private case class WrappedExistential(
    override val typeParameter: TypeParameter,
    override val level:         Int = 0
  ) extends UndefinedType {
    override val isWrappedExistential: Boolean = true
  }

  private case class FromParameter(p: ScParameter, original: ValueType) extends UndefinedType {
    override implicit def projectContext: ProjectContext = p.projectContext

    override val level: Int = 0

    override val typeParameter: TypeParameter = TypeParameter.light(s"`${p.name}.type`", Seq.empty, Nothing, Any)

    override def dependentParameterType: Option[ValueType] = Some(original)
  }

  def unapply(arg: UndefinedType): Option[(TypeParameter, Int)] = Some(arg.typeParameter, arg.level)

  def apply(typeParameter: TypeParameter, level: Int = 0): UndefinedType = FromTypeParameter(typeParameter, level)

  def wrapExistential(typeParameter: TypeParameter): UndefinedType = WrappedExistential(typeParameter)

  def apply(psiTypeParameter: PsiTypeParameter): UndefinedType =
    FromTypeParameter(TypeParameter(psiTypeParameter))

  //only one overload can have default arguments :(
  def apply(psiTypeParameter: PsiTypeParameter, level: Int): UndefinedType =
    FromTypeParameter(TypeParameter(psiTypeParameter), level)

  def apply(typeParameterType: TypeParameterType): UndefinedType =
    FromTypeParameter(typeParameterType.typeParameter)

  def apply(parameter: ScParameter, original: ValueType): UndefinedType =
    FromParameter(parameter, original)

  def revertDependentTypes(tp: ScType): ScType = tp.updateLeaves {
    case FromParameter(_, original) => original
  }
}
