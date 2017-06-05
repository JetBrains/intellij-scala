package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, Signature, TypeAliasSignature}

/**
  * @author adkozlov
  */
case class DottyRefinedType(designator: ScType,
                            signatures: Set[Signature] = Set.empty,
                            typeAliasSignatures: Set[TypeAliasSignature] = Set.empty)
                           (override val typeArguments: Seq[ScType] = typeAliasSignatures.toSeq.flatMap(_.getType))
  extends ParameterizedType with DottyType {

  override protected def substitutorInner = ScSubstitutor.empty

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case dottyVisitor: DottyTypeVisitor => dottyVisitor.visitRefinedType(this)
    case _ =>
  }
}

object DottyRefinedType {
  def apply(designator: ScType, refinement: ScRefinement): DottyRefinedType = {
    val signatures = refinement.holders.map {
      case function: ScFunction => Seq(Signature(function))
      case variable: ScVariable =>
        val elements = variable.declaredElements
        elements.map(Signature.getter) ++ elements.map(Signature.setter)
      case value: ScValue => value.declaredElements.map(Signature.getter)
    }.foldLeft(Set[Signature]())(_ ++ _)

    val typeAliasSignatures = refinement.types.map(TypeAliasSignature(_)).toSet

    DottyRefinedType(designator, signatures, typeAliasSignatures)()
  }
}