package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner}

import scala.collection.mutable.ArrayBuffer

final case class Scala2Bounds()(implicit val projectContext: ProjectContext)
  extends ScalaBoundsBase
    with BoundsUtil
    with ProjectContextOwner {

  private def conforms(t1: ScType, t2: ScType, checkWeak: Boolean)(implicit context: Context) =
    t1.conforms(t2, ConstraintSystem.empty, checkWeak).isRight

  override def glb(t1: ScType, t2: ScType, checkWeak: Boolean = false)(implicit context: Context): ScType = {
    if (conforms(t1, t2, checkWeak)) t1
    else if (conforms(t2, t1, checkWeak)) t2
    else {
      (t1, t2) match {
        case (arg @ ScExistentialArgument(_, _, lower, upper), ScExistentialArgument(_, _, lower2, upper2)) =>
          arg.copyWithBounds(lub(lower, lower2, checkWeak), glb(upper, upper2, checkWeak))
        case (arg @ ScExistentialArgument(_, _, lower, upper), _) =>
          arg.copyWithBounds(lub(lower, t2, checkWeak), glb(upper, t2))
        case (_, arg @ ScExistentialArgument(_, _, lower, upper)) =>
          arg.copyWithBounds(lub(lower, t1, checkWeak), glb(upper, t1))
        case (ex: ScExistentialType, _) => glb(ex.quantified, t2, checkWeak).unpackedType
        case (_, ex: ScExistentialType) => glb(t1, ex.quantified, checkWeak).unpackedType
        case (TypeConstructor(poly), _) => glb(poly, t2, checkWeak)
        case (_, TypeConstructor(poly)) => glb(t1, poly, checkWeak)
        case (lhs: ScTypePolymorphicType, rhs: ScTypePolymorphicType) =>
          polymorphicTypesBound(lhs, rhs, Bound.Glb, checkWeak, 0)(stopAddingUpperBound = false, context = context)
        case _ => ScCompoundType(Seq(t1, t2), Map.empty, Map.empty)
      }
    }
  }

  private def polymorphicTypesBound(
    lhs:       ScTypePolymorphicType,
    rhs:       ScTypePolymorphicType,
    boundKind: Bound,
    checkWeak: Boolean,
    depth:     Int
  )(implicit
    stopAddingUpperBound: Boolean,
    context: Context
  ): ScTypePolymorphicType = {
    val ScTypePolymorphicType(lhsInt, lhsParams) = lhs
    val ScTypePolymorphicType(rhsInt, rhsParams) = rhs

    val newParams = typeParametersBound(lhsParams, rhsParams, boundKind.inverse, checkWeak, depth)
    val lhsSubst  = ScSubstitutor.bind(lhsParams, newParams)(TypeParameterType(_))
    val rhsSubst  = ScSubstitutor.bind(rhsParams, newParams)(TypeParameterType(_))

    val intTpe = boundKind match {
      case Bound.Glb => glb(lhsSubst(lhsInt), rhsSubst(rhsInt), checkWeak)
      case Bound.Lub => lubInner(lhsSubst(lhsInt), rhsSubst(rhsInt), depth, checkWeak)
    }

    ScTypePolymorphicType(intTpe, newParams)
  }

  override def lubInner(
    t1:        ScType,
    t2:        ScType,
    depth:     Int,
    checkWeak: Boolean
  )(implicit
    stopAddingUpperBound: Boolean,
    context:              Context
  ): ScType = {
    if (conforms(t1, t2, checkWeak)) t2
    else if (conforms(t2, t1, checkWeak)) t1
    else {
      def lubWithExpandedAliases(t1: ScType, t2: ScType): ScType = {
        (t1, t2) match {
          case (TypeConstructor(poly), _) =>
            lubInner(poly, t2, depth, checkWeak)
          case (_, TypeConstructor(poly)) =>
            lubInner(t1, poly, depth, checkWeak)
          case (ScDesignatorType(t: ScParameter), _) =>
            lubInner(t.insideParamType.getOrAny, t2, depth, checkWeak)
          case (ScDesignatorType(t: ScTypedDefinition), _) if !t.is[ScObject] =>
            lubInner(t.`type`().getOrAny, t2, depth, checkWeak)
          case (_, ScDesignatorType(t: ScParameter)) =>
            lubInner(t1, t.insideParamType.getOrAny, depth, checkWeak)
          case (_, ScDesignatorType(t: ScTypedDefinition)) if !t.is[ScObject] =>
            lubInner(t1, t.`type`().getOrAny, depth, checkWeak)
          case (ex: ScExistentialType, _) => lubInner(ex.quantified, t2, depth, checkWeak).unpackedType
          case (_, ex: ScExistentialType) => lubInner(t1, ex.quantified, depth, checkWeak).unpackedType
          case (tpt: TypeParameterType, _) if !stopAddingUpperBound => lubInner(tpt.upperType, t2, depth - 1, checkWeak)
          case (_, tpt: TypeParameterType) if !stopAddingUpperBound => lubInner(t1, tpt.upperType, depth - 1, checkWeak)
          case (arg @ ScExistentialArgument(_, _, lower, upper), ScExistentialArgument(_, _, lower2, upper2))
            if !stopAddingUpperBound =>
            arg.copyWithBounds(glb(lower, lower2, checkWeak), lubInner(upper, upper2, depth - 1, checkWeak))
          case (arg @ ScExistentialArgument(_, _, lower, upper), r) if !stopAddingUpperBound =>
            arg.copyWithBounds(glb(lower, r, checkWeak), lubInner(upper, t2, depth - 1, checkWeak))
          case (r, arg @ ScExistentialArgument(_, _, lower, upper)) if !stopAddingUpperBound =>
            arg.copyWithBounds(glb(lower, r, checkWeak), lubInner(upper, t2, depth - 1, checkWeak))
          case (_: ValType, _: ValType) => AnyVal
          case (lit1: ScLiteralType, lit2: ScLiteralType) => lubInner(lit1.wideType, lit2.wideType, depth, checkWeak = true)
          case (JavaArrayType(arg1), JavaArrayType(arg2)) =>
            val (v, ex) = calcForTypeParamWithoutVariance(arg1, arg2, checkWeak, stopAddingUpperBound)
            ex match {
              case Some(_) => ScExistentialType(JavaArrayType(v))
              case None    => JavaArrayType(v)
            }
          case (JavaArrayType(arg), ParameterizedType(des, args)) if args.length == 1 && (des.extractClass match {
            case Some(q) => q.qualifiedName == "scala.Array"
            case _ => false
          }) =>
            val (v, ex) = calcForTypeParamWithoutVariance(arg, args.head, checkWeak, stopAddingUpperBound)
            ex match {
              case Some(_) => ScExistentialType(ScParameterizedType(des, Seq(v)))
              case None    => ScParameterizedType(des, Seq(v))
            }
          case (ParameterizedType(des, args), JavaArrayType(arg)) if args.length == 1 && (des.extractClass match {
            case Some(q) => q.qualifiedName == "scala.Array"
            case _ => false
          }) =>
            val (v, ex) = calcForTypeParamWithoutVariance(arg, args.head, checkWeak, stopAddingUpperBound)
            ex match {
              case Some(_) => ScExistentialType(ScParameterizedType(des, Seq(v)))
              case None    => ScParameterizedType(des, Seq(v))
            }
          case (JavaArrayType(_), tp) =>
            if (tp.conforms(AnyRef)) AnyRef
            else Any
          case (tp, JavaArrayType(_)) =>
            if (tp.conforms(AnyRef)) AnyRef
            else Any
          case (lhs: ScTypePolymorphicType, rhs: ScTypePolymorphicType) =>
            polymorphicTypesBound(lhs, rhs, Bound.Lub, checkWeak, depth)
          case _ =>
            val leftClasses  = extractBaseClassInfo(t1)
            val rightClasses = extractBaseClassInfo(t2)
            val buf          = new ArrayBuffer[ScType]
            val supers       = getLeastSuperClasses(leftClasses, rightClasses)

            for (sup <- supers) {
              val tp = getTypeForAppending(
                leftClasses(sup._2),
                rightClasses(sup._3),
                sup._1,
                depth,
                checkWeak = checkWeak,
                stopAddingUpperBound = stopAddingUpperBound)

              if (tp != Any) buf += tp
            }

            buf.toArray match {
              case a: Array[ScType] if a.length == 0 => Any
              case a: Array[ScType] if a.length == 1 => a(0)
              case many                              => ScCompoundType(many.toSeq, Map.empty, Map.empty)
            }
          //todo: refinement for compound types
        }
      }
      lubWithExpandedAliases(t1, t2).unpackedType
    }
  }
}