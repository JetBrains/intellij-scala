package org.jetbrains.plugins.scala.lang.psi.types

import java.util.Objects

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

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

  override def hashCode(): Int = Objects.hash(
    name, typeParams, lowerBound, upperBound, Boolean.box(isDefinition)
  )
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

  private final class Simple(val typeAlias: ScTypeAlias) extends TypeAliasSignature {

    val name: String = typeAlias.name

    val typeParams: Seq[TypeParameter] = typeAlias.typeParameters.map(TypeParameter(_))

    val lowerBound: ScType = typeAlias.lowerBound.getOrNothing

    val upperBound: ScType = typeAlias.upperBound.getOrAny

    val isDefinition: Boolean = typeAlias.isDefinition

    def substitutor: ScSubstitutor = ScSubstitutor.empty
  }

  private final case class Substituted(typeAlias: ScTypeAlias,
                                       name: String,
                                       typeParams: Seq[TypeParameter],
                                       lowerBound: ScType,
                                       upperBound: ScType,
                                       isDefinition: Boolean,
                                       substitutor: ScSubstitutor) extends TypeAliasSignature

}
