package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeVisitor, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Signature, TypeAliasSignature}

/**
  * @author adkozlov
  */
case class DottyRefinedType(designator: ScType,
                            signatures: Set[Signature] = Set.empty,
                            typeAliasSignatures: Set[TypeAliasSignature] = Set.empty)
                           (override val typeArguments: Seq[ScType] = Seq.empty)
  extends ParameterizedType with DottyType {

  override protected def substitutorInner = ScSubstitutor.empty

  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case dottyVisitor: DottyTypeVisitor => dottyVisitor.visitRefinedType(this)
    case _ =>
  }

  //this is wrong, but dotty types are not used right now anyway
  def updateSubtypes(substitutor: ScSubstitutor, variance: Variance)(implicit visited: Set[ScType]): ScType = this
}

object DottyRefinedType {
  def apply(designator: ScType, refinement: ScRefinement): DottyRefinedType = {
    val signatures = refinement.holders.map {
      case function: ScFunction => Seq(Signature(function))
      case variable: ScVariable =>
        val elements = variable.declaredElements
        elements.map(Signature(_)) ++ elements.map(Signature.scalaSetter(_))
      case value: ScValue => value.declaredElements.map(Signature(_))
    }.foldLeft(Set[Signature]())(_ ++ _)

    DottyRefinedType(designator, signatures)()
  }
}