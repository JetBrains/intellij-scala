package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, Signature, TypeAliasSignature}

/**
  * @author adkozlov
  */
case class DottyRefinedType private(designator: ScType,
                                    signatures: Set[Signature],
                                    typeAliasSignatures: Set[TypeAliasSignature]) extends ParameterizedType with DottyType {
  override val typeArguments = typeAliasSignatures.toSeq.flatMap(_.getType)

  override protected def substitutorInner = ScSubstitutor.empty

  override def visitType(visitor: TypeVisitor) = visitor match {
    case dottyVisitor: DottyTypeVisitor => dottyVisitor.visitRefinedType(this)
    case _ =>
  }
}

object DottyRefinedType {
  implicit val typeSystem = DottyTypeSystem

  def apply(designator: ScType, refinement: ScRefinement): DottyRefinedType = {
    val signatures = refinement.holders.map {
      case function: ScFunction => Seq(Signature(function))
      case variable: ScVariable =>
        val elements = variable.declaredElements
        elements.map(Signature.getter) ++ elements.map(Signature.setter)
      case value: ScValue => value.declaredElements.map(Signature.getter)
    }.foldLeft(Set[Signature]())(_ ++ _)

    val typeAliasSignatures = refinement.types.map(TypeAliasSignature(_)).toSet

    val (newType, newSinatures, newTypeAliasSignatures) = designator match {
      case DottyRefinedType(refinedType, refinedSignatures, refinedTypeAliasSignatures) =>
        (refinedType, refinedSignatures, refinedTypeAliasSignatures)
      case notRefinedType =>
        (notRefinedType, Set.empty, Set.empty)
    }
    DottyRefinedType(newType,
      signatures ++ newSinatures,
      typeAliasSignatures ++ newTypeAliasSignatures)
  }

  def apply(designator: ScType, typeArguments: Seq[ScType]): DottyRefinedType = {
    DottyRefinedType(designator, Set.empty, Set.empty)
  }
}