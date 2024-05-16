package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScalaConformance.Bound
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.util.HashBuilder._

import scala.annotation.tailrec

final class ScParameterizedType private (override val designator: ScType, override val typeArguments: Seq[ScType])
    extends ParameterizedType
    with ScalaType {

  override protected def calculateAliasType: Option[AliasType] =
    designator match {
      case ScDesignatorType(ta: ScTypeAlias)                   => computeAliasType(ta, ta.lowerBound, ta.upperBound)
      case ScProjectionType.withActual(ta: ScTypeAlias, subst) => computeAliasType(ta, ta.lowerBound, ta.upperBound, subst)
      case p: ScParameterizedType =>
        //@TODO: scala 3 only
        p.aliasType.flatMap {
          case AliasType(ta, lower, upper) => computeAliasType(ta, lower, upper)
        }
      case _ => None
    }

  private def computeAliasType(
    ta:                         ScTypeAlias,
    lower:                      TypeResult,
    upper:                      TypeResult,
    subst:                      ScSubstitutor = ScSubstitutor.empty,
  ): Option[AliasType] = {
    @tailrec
    def stripParamsFromTypeLambdas(t: ScType): (ScType, Seq[TypeParameter]) =
      t match {
        case ScTypePolymorphicType(internal, tps) => (internal, tps)
        case AliasType(_, Right(lower), _)        => stripParamsFromTypeLambdas(lower)
        case t                                    => (t, Seq.empty)
      }

    val newLower = lower.map { l =>
      val (t, tps) = stripParamsFromTypeLambdas(l)

      val actualTypeParameters =
        if (tps.isEmpty) ta.typeParameters.map(TypeParameter(_))
        else             tps

      val typeArgsSubst = ScSubstitutor.bind(actualTypeParameters, typeArguments)
      val s             = subst.followed(typeArgsSubst)
      s(t)
    }

    val newUpper =
      if (ta.isDefinition) newLower
      else
        upper.map { u =>
          val (t, tps) = stripParamsFromTypeLambdas(u)

          val actualTypeParameters =
            if (tps.isEmpty) ta.typeParameters.map(TypeParameter(_))
            else             tps

          val typeArgsSubst = ScSubstitutor.bind(actualTypeParameters, typeArguments)
          val s             = subst.followed(typeArgsSubst)
          s(t)
        }

    Option(AliasType(ta, newLower, newUpper))
  }

  private var hash: Int = -1

  //noinspection HashCodeUsesVar
  override def hashCode: Int = {
    if (hash == -1)
      hash = designator #+ typeArguments

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
        this match {
          case AliasType(_: ScTypeAliasDefinition, lower, _) =>
            (lower match {
              case Right(tp) => tp
              case _         => return ConstraintsResult.Left
            }).equiv(r, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
      case (ParameterizedType(ScDesignatorType(_: ScTypeAliasDefinition), _), _) =>
        this match {
          case AliasType(_: ScTypeAliasDefinition, lower, _) =>
            (lower match {
              case Right(tp) => tp
              case _         => return ConstraintsResult.Left
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


  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitParameterizedType(this)

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
  private val andOrOrTypeDesignator = Set("&", "|")

  def apply(designator: ScType, typeArgs: Seq[ScType]): ScType =
    CompileTimeOps(designator, typeArgs).getOrElse {
      def simple = new ScParameterizedType(designator, typeArgs)

      designator match {
        case ScDesignatorType(alias: ScTypeAlias)
          if andOrOrTypeDesignator.contains(alias.name) &&
            alias.topLevelQualifier.contains("scala") =>

          val name = alias.name

          if (typeArgs.size != 2) Any(alias.projectContext)
          else if (name == "&")   ScAndType(typeArgs.head, typeArgs(1))
          else                    ScOrType(typeArgs.head, typeArgs(1))
        // Any and Nothing can take type parameter but will always produce themselves ignoring the arguments
        case anyOrNothing@StdType(StdType.Name.Any | StdType.Name.Nothing, _) => anyOrNothing
        // Simplify application of "type-lambda-like" types
        // ((Compound {type S[x] = Type[x]}))#S[A] is replaced with Type[A]
        case ScProjectionType(ScCompoundType(_, _, aliasMap), _) & ScProjectionType.withActual(alias: ScTypeAliasDefinition, _)
          if aliasMap.contains(alias.name) =>

          val subst = ScSubstitutor.bind(alias.typeParameters, typeArgs)
          val typeAliasSignature = aliasMap(alias.name)

          subst(typeAliasSignature.upperBound) match {
            case v: ValueType => v
            case _ => simple
          }

        // Simplify application of ScTypePolymorphicType encoding of type lambdas
        case ScTypePolymorphicType(internal: ScParameterizedType, typeParameters) =>
          val subst = ScSubstitutor.bind(typeParameters, typeArgs)

          ScParameterizedType(
            subst(internal.designator),
            internal.typeArguments.map(subst)
          )
        case _ => simple
      }
    }
}
