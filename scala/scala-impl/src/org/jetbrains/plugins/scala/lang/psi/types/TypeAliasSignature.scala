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

  def updateTypes(substitutor: ScSubstitutor): TypeAliasSignature = new TypeAliasSignature.Substituted(this, substitutor)
}

object TypeAliasSignature {

  def apply(typeAlias: ScTypeAlias): TypeAliasSignature = new Simple(typeAlias)

  private final class Simple(val typeAlias: ScTypeAlias) extends TypeAliasSignature {

    val name: String = typeAlias.name

    val typeParams: Seq[TypeParameter] = typeAlias.typeParameters.map(TypeParameter(_))

    val lowerBound: ScType = typeAlias.lowerBound.getOrNothing

    val upperBound: ScType = typeAlias.upperBound.getOrAny

    val isDefinition: Boolean = typeAlias.isDefinition

    def substitutor: ScSubstitutor = ScSubstitutor.empty
  }

  private final class Substituted(other: TypeAliasSignature, nextSubstitutor: ScSubstitutor) extends TypeAliasSignature {
    val typeAlias: ScTypeAlias = other.typeAlias

    val name: String = other.name

    val typeParams: Seq[TypeParameter] = other.typeParams.map(_.update(nextSubstitutor))

    val lowerBound: ScType = nextSubstitutor(other.lowerBound)

    val upperBound: ScType = nextSubstitutor(other.upperBound)

    val isDefinition: Boolean = other.isDefinition

    val substitutor: ScSubstitutor = other.substitutor.followed(nextSubstitutor)
  }
}
