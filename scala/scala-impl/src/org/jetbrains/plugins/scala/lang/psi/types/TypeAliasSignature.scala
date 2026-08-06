package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.util.HashBuilder._

abstract class TypeAliasSignature {
  def typeAlias: ScTypeAlias
  def name: String
  def typeParams: Seq[TypeParameter]
  def lowerBound: ScType
  def upperBound: ScType
  def isDefinition: Boolean
  def substitutor: ScSubstitutor

  override def equals(other: Any): Boolean = other match {
    case other: TypeAliasSignature =>
      name == other.name &&
        typeParams == other.typeParams &&
        lowerBound == other.lowerBound &&
        upperBound == other.upperBound &&
        isDefinition == other.isDefinition
    case _ => false
  }

  override def hashCode(): Int =
    name #+ typeParams #+ lowerBound #+ upperBound #+ isDefinition
}

object TypeAliasSignature {

  def apply(typeAlias: ScTypeAlias): TypeAliasSignature = new Simple(typeAlias)

  def apply(typeAlias: ScTypeAlias,
            name: String,
            typeParams: Seq[TypeParameter],
            lowerBound: ScType,
            upperBound: ScType,
            isDefinition: Boolean,
            substitutor: ScSubstitutor): TypeAliasSignature =
    Substituted(typeAlias, name, typeParams, lowerBound, upperBound, isDefinition, substitutor)

  private final class Simple(override val typeAlias: ScTypeAlias) extends TypeAliasSignature {

    override val name: String = typeAlias.name

    override val typeParams: Seq[TypeParameter] = typeAlias.typeParameters.map(TypeParameter(_))

    override val lowerBound: ScType = typeAlias.lowerBound.getOrNothing

    override val upperBound: ScType = typeAlias.upperBound.getOrAny

    override val isDefinition: Boolean = typeAlias.isDefinition

    override def substitutor: ScSubstitutor = ScSubstitutor.empty
  }

  private final case class Substituted(override val typeAlias: ScTypeAlias,
                                       override val name: String,
                                       override val typeParams: Seq[TypeParameter],
                                       override val lowerBound: ScType,
                                       override val upperBound: ScType,
                                       override val isDefinition: Boolean,
                                       override val substitutor: ScSubstitutor) extends TypeAliasSignature

}
