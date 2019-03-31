package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TermSignature, TypeAliasSignature}

/**
  * @author adkozlov
  */
case class DottyRefinedType(designator: ScType,
                            signatures: Set[TermSignature] = Set.empty,
                            typeAliasSignatures: Set[TypeAliasSignature] = Set.empty)
                           (override val typeArguments: Seq[ScType] = Seq.empty)
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
      case function: ScFunction => Seq(TermSignature(function))
      case variable: ScVariable =>
        val elements = variable.declaredElements
        elements.map(TermSignature(_)) ++ elements.map(TermSignature.scalaSetter(_))
      case value: ScValue => value.declaredElements.map(TermSignature(_))
    }.foldLeft(Set[TermSignature]())(_ ++ _)

    DottyRefinedType(designator, signatures)()
  }
}