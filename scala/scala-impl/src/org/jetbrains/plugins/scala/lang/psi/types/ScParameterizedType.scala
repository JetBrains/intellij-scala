package org.jetbrains.plugins.scala
package lang
package psi
package types

/**
 * @author ilyas
 */

import java.util.Objects

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScalaConformance.Bound
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType, TypeVisitor, UndefinedType, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

final class ScParameterizedType private(val designator: ScType, val typeArguments: Seq[ScType]) extends ParameterizedType with ScalaType {

  override protected def isAliasTypeInner: Option[AliasType] = {
    designator match {
      case ScDesignatorType(ta: ScTypeAlias) =>
        computeAliasType(ta, ScSubstitutor.empty)
      case ScProjectionType.withActual(ta: ScTypeAlias, subst) =>
        computeAliasType(ta, subst)
      case _ => None
    }
  }

  private def computeAliasType(ta: ScTypeAlias, subst: ScSubstitutor): Some[AliasType] = {
    val genericSubst = ScSubstitutor.bind(ta.typeParameters, typeArguments)

    val s = subst.followed(genericSubst)
    val lowerBound = ta.lowerBound.map(s)
    val upperBound =
      if (ta.isDefinition) lowerBound
      else ta.upperBound.map(s)
    Some(AliasType(ta, lowerBound, upperBound))
  }

  private var hash: Int = -1

  //noinspection HashCodeUsesVar
  override def hashCode: Int = {
    if (hash == -1)
      hash = Objects.hash(designator, typeArguments)

    hash
  }

  protected override def substitutorInner: ScSubstitutor = {
    designator match {
      case tpt: TypeParameterType =>
        ScSubstitutor.bind(tpt.typeParameters, typeArguments)
      case _ => designator.extractDesignatedType(expandAliases = false) match {
        case Some((owner: ScTypeParametersOwner, s)) =>
          s.followed(ScSubstitutor.bind(owner.typeParameters, typeArguments))
        case Some((owner: PsiTypeParameterListOwner, s)) =>
          s.followed(ScSubstitutor.bind(owner.getTypeParameters, typeArguments))
        case _ => ScSubstitutor.empty
      }
    }
  }

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    val Conformance: ScalaConformance = typeSystem
    val Nothing = projectContext.stdTypes.Nothing

    (this, r) match {
      case (ParameterizedType(Nothing, _), Nothing) => constraints
      case (ParameterizedType(Nothing, _), ParameterizedType(Nothing, _)) => constraints
      case (ParameterizedType(ScAbstractType(tp, lower, upper), args), _) =>
        if (falseUndef) return ConstraintsResult.Left

        val subst = ScSubstitutor.bind(tp.typeParameters, args)
        var conformance = r.conforms(subst(upper), constraints)
        if (conformance.isLeft) return ConstraintsResult.Left

        conformance = subst(lower).conforms(r, conformance.constraints)
        if (conformance.isLeft) return ConstraintsResult.Left

        conformance
      case (ParameterizedType(proj@ScProjectionType(_, _), _), _) if proj.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
            (lower match {
              case Right(tp) => tp
              case _ => return ConstraintsResult.Left
            }).equiv(r, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
      case (ParameterizedType(ScDesignatorType(_: ScTypeAliasDefinition), _), _) =>
        isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
            (lower match {
              case Right(tp) => tp
              case _ => return ConstraintsResult.Left
            }).equiv(r, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
      case (ParameterizedType(UndefinedType(_, _), _), rhs @ ParameterizedType(_, _)) if !falseUndef =>
        Conformance.unifyHK(this, rhs, constraints, Bound.Equivalence, Set.empty, checkWeak = false)
      case (ParameterizedType(_, _), rhs @ ParameterizedType(UndefinedType(_, _), _)) if !falseUndef =>
        Conformance.unifyHK(rhs, this, constraints, Bound.Equivalence, Set.empty, checkWeak = false)
      case (ParameterizedType(_, _), ParameterizedType(designator1, typeArgs1)) =>
        var lastConstraints = constraints
        var t = designator.equiv(designator1, constraints, falseUndef)
        if (t.isLeft) return ConstraintsResult.Left
        lastConstraints = t.constraints
        if (typeArguments.length != typeArgs1.length) return ConstraintsResult.Left
        val iterator1 = typeArguments.iterator
        val iterator2 = typeArgs1.iterator
        while (iterator1.hasNext && iterator2.hasNext) {
          t = iterator1.next().equiv(iterator2.next(), lastConstraints, falseUndef)

          if (t.isLeft) return ConstraintsResult.Left

          lastConstraints = t.constraints
        }
        lastConstraints
      case _ => ConstraintsResult.Left
    }
  }


  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitParameterizedType(this)
    case _ =>
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[ScParameterizedType]

  override def equals(other: Any): Boolean = other match {
    case that: ScParameterizedType =>
      (that canEqual this) &&
        designator == that.designator &&
        typeArguments == that.typeArguments
    case _ => false
  }
}

object ScParameterizedType {

  def apply(designator: ScType, typeArgs: Seq[ScType]): ValueType = {

    val simple = new ScParameterizedType(designator, typeArgs)
    designator match {

      // Simplify application of "type-lambda-like" types
      // ((Compound {type S[x] = Type[x]}))#S[A] is replaced with Type[A]
      case ScProjectionType(ScCompoundType(_, _, aliasMap), _) && ScProjectionType.withActual(alias: ScTypeAliasDefinition, _)
        if aliasMap.contains(alias.name) =>

        val subst = ScSubstitutor.bind(alias.typeParameters, typeArgs)
        val typeAliasSignature = aliasMap(alias.name)

        subst(typeAliasSignature.upperBound) match {
          case v: ValueType => v
          case _ => simple
        }

      // Simplify application of ScTypePolymorphicType encoding of type lambdas
      case ScTypePolymorphicType(internalType, typeParameters) if internalType.isInstanceOf[ScParameterizedType] =>
        val internal = internalType.asInstanceOf[ScParameterizedType]
        new ScParameterizedType(internal.designator, internal.typeArguments.map {
          case pType: TypeParameterType =>
            typeParameters.zip(typeArgs).find{case (tParam, _) => TypeParameterType(tParam).equiv(pType)}.map(_._2).getOrElse(pType)
          case aType =>
            aType
        })
      case _ => simple
    }
  }
}
