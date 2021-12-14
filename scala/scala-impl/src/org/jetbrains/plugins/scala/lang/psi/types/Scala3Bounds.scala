package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner}

final case class Scala3Bounds()(implicit val projectContext: ProjectContext)
    extends BoundsBase
    with ProjectContextOwner
    with BoundsUtil {

  override def glb(lhs: ScType, rhs: ScType, checkWeak: Boolean): ScType =
    if (lhs.isAny || rhs.isNothing)      rhs
    else if (rhs.isAny || rhs.isNothing) lhs
    else {
      val tp1 = dropIfSuper(lhs, rhs).getOrElse(return rhs)

      if (tp1 ne lhs) glb(tp1, rhs)
      else {
        val tp2 = dropIfSuper(rhs, lhs).getOrElse(return lhs)

        if (tp2 ne rhs) glb(lhs, tp2)
        else
          tp2 match {
            case ScOrType(l, r) => lub(glb(lhs, l), glb(lhs, r))
            case _ =>
              tp1 match {
                case ScOrType(l, r) => lub(glb(l, rhs), glb(r, rhs))
                case _              => normalizedAndType(lhs, rhs)
              }
          }
      }
    }

  override def lub(lhs: ScType, rhs: ScType, checkWeak: Boolean): ScType =
    if (lhs.isAny || rhs.isNothing)      lhs
    else if (rhs.isAny || lhs.isNothing) rhs
    else
      mergeIfSuper(lhs, rhs)
        .orElse(mergeIfSuper(rhs, lhs))
        .getOrElse(mergeIfTypeConstructors(lhs, rhs, ScOrType(_, _), Bound.Lub))

  override protected def lubInner(
    l:                    ScType,
    r:                    ScType,
    checkWeak:            Boolean,
    stopAddingUpperBound: Boolean
  ): ScType = lub(l, r)

  override protected def lubInner(
    t1:        ScType,
    t2:        ScType,
    depth:     Int,
    checkWeak: Boolean
  )(implicit
    stopAddingUpperBound: Boolean
  ): ScType = lub(t1, t2)

  private def normalizedAndType(lhs: ScType, rhs: ScType): ScType =
    distributeAnd(lhs, rhs)
      .getOrElse(
        mergeIfTypeConstructors(lhs, rhs, ScAndType(_, _), Bound.Glb)
      )

  private def distributeAnd(lhs: ScType, rhs: ScType): Option[ScType] =
    (lhs, rhs) match {
      case (ParameterizedType(des1, args1), ParameterizedType(des2, args2)) if checkEquiv(des1, des2) =>
        val typeParams = extractTypeParameters(des1)
        val jointArgs  = glbArgs(args1, args2, typeParams)
        jointArgs.map(ScParameterizedType(des1, _))
      case _ => None
    }

  private def checkEquiv(lhs: ScType, rhs: ScType): Boolean =
    lhs.equiv(rhs, ConstraintSystem.empty, falseUndef = false).isRight

  /**
   * Try to produce joint arguments for `A[T_1, ..., T_n] | A[U_1', ..., U_n']`
   * using the following rules:
   *  - if argumetns are the same, that argument
   *  - if corresponding type param is co/contravariant — lub/glb
   *  - otherwise fresh type parameter bounded by lub/glb
   */
  private[this] def lubArgs(
    args1:      Seq[ScType],
    args2:      Seq[ScType],
    typeParams: Seq[TypeParameter]
  ): Seq[ScType] = {
    val zippedArgs = args1.lazyZip(args2).lazyZip(typeParams).iterator
    val jointArgs  = Seq.newBuilder[ScType]

    while (zippedArgs.hasNext) {
      val (arg1, arg2, typeParam) = zippedArgs.next()

      jointArgs +=
        (if (checkEquiv(arg1, arg2))         arg1
         else if (typeParam.isCovariant)     lub(arg1, arg2)
         else if (typeParam.isContravariant) glb(arg1, arg2)
         else
          TypeParameterType(
            TypeParameter.light("SyntheticLub", Seq.empty, glb(arg1, arg2), lub(arg1, arg2))
          ))
    }

    jointArgs.result()
  }

  /**
   * Try to produce joint arguments for `A[T_1, ..., T_n] & A[U_1', ..., U_n']`
   * using the following rules:
   *  - if argumetns are the same, that argument
   *  - if corresponding type param is co/contravariant — glb/lub
   *  - if at least one of the arguments is a type parameter — fresh type parameter bounded by glb/lub
   *  - otherwise None
   */
  private[this] def glbArgs(
    args1:      Seq[ScType],
    args2:      Seq[ScType],
    typeParams: Seq[TypeParameter]
  ): Option[Seq[ScType]] = {
    val zippedArgs = args1.lazyZip(args2).lazyZip(typeParams).iterator
    val jointArgs  = Seq.newBuilder[ScType]

    while (zippedArgs.hasNext) {
      val (arg1, arg2, typeParam) = zippedArgs.next()

      if (checkEquiv(arg1, arg2))         jointArgs += arg1
      else if (typeParam.isCovariant)     jointArgs += glb(arg1, arg2)
      else if (typeParam.isContravariant) jointArgs += lub(arg1, arg2)
      else if (arg1.is[TypeParameterType] || arg2.is[TypeParameterType])
        jointArgs += TypeParameterType(TypeParameter.light("SyntheticGlb", Seq.empty, lub(arg1, arg2), glb(arg1, arg2)))
      else return None
    }

    Option(jointArgs.result())
  }

  private def mergeIfTypeConstructors(
    lhs:   ScType,
    rhs:   ScType,
    op:    (ScType, ScType) => ScType,
    bound: Bound
  ): ScType = {
    val lhsParams = extractTypeParameters(lhs)
    val rhsParams = extractTypeParameters(rhs)

    if (lhsParams.nonEmpty && lhsParams.length == rhsParams.length) {
      val newParams = typeParametersBound(lhsParams, rhsParams, bound.inverse)
      val lhsSubst  = ScSubstitutor.bind(lhsParams, newParams)(TypeParameterType(_))
      val rhsSubst  = ScSubstitutor.bind(rhsParams, newParams)(TypeParameterType(_))

      val original: (ScType, ScType) => ScType = bound match {
        case Bound.Lub => lub(_, _)
        case Bound.Glb => glb(_, _)
      }

      ScTypePolymorphicType(original(lhsSubst(lhs), rhsSubst(rhs)), newParams)
    } else op(lhs, rhs)
  }

  /**
   * Merge `lhs` into `rhs` if `lhs` is a super type of some |-summand in `rhs`.
   */
  private def mergeIfSuper(lhs: ScType, rhs: ScType): Option[ScType] =
    if (rhs.conforms(lhs)) Option(lhs)
    else
      rhs match {
        case ScOrType(l, r) =>
          val upperL = mergeIfSuper(lhs, l)
          upperL match {
            case Some(`l`)   => Option(rhs)
            case Some(other) => Option(lub(other, r))
            case None =>
              val upperR = mergeIfSuper(lhs, r)
              upperR.map { t =>
                if (t eq r) rhs
                else lub(l, t)
              }
          }
        case _ => None
      }

  /**
   * if some &-summand of `tp` is a supertype of `sub` — drop it.
   */
  private def dropIfSuper(tp: ScType, sub: ScType): Option[ScType] =
    if (sub.conforms(tp)) None
    else
      tp match {
        case ScAndType(l, r) =>
          val dropL = dropIfSuper(l, sub)
          val dropR = dropIfSuper(r, sub)

          if (dropL.isEmpty)      dropR
          else if (dropR.isEmpty) dropL
          else                    Option(ScAndType(dropL.get, dropR.get))
        case _ => Option(tp)
      }

  def orTypeJoin(orTp: ScType): ScType = {
    def widenToUpperBound(tp: ScType): ScType = tp match {
      case tpt: TypeParameterType                          => tpt.upperType
      case ParameterizedType(tpt: TypeParameterType, args) => ScParameterizedType(tpt.upperType, args)
      case _                                               => tp
    }

    def approximateOr(lhs: ScType, rhs: ScType): Option[ScType] = {
      val lhsW = widenToUpperBound(lhs)
      val rhsW = widenToUpperBound(rhs)

      Option.when((lhs ne lhsW) || (rhs ne rhsW))(
        if (rhs eq rhsW)              orTypeJoin(lub(lhsW, rhs))
        else if (lhs eq lhsW)         orTypeJoin(lub(lhs, rhsW))
        else if (lhsW.conforms(rhsW)) orTypeJoin(lub(lhsW, rhs))
        else if (rhsW.conforms(lhsW)) orTypeJoin(lub(lhs, rhsW))
        else if (lhs.conforms(rhsW))  rhsW
        else if (rhs.conforms(lhsW))  lhsW
        else                          orTypeJoin(lub(lhsW, rhs))
      )
    }

    orTp match {
      case ScOrType(ParameterizedType(des1, args1), ParameterizedType(des2, args2))
        if checkEquiv(des1, des2) => ParameterizedType(des1, lubArgs(args1, args2, extractTypeParameters(des1)))
      case ScOrType(lhs, rhs) =>
        approximateOr(lhs, rhs).getOrElse {
          val leftClasses  = extractBaseClassInfo(lhs)
          val rightClasses = extractBaseClassInfo(rhs)
          val supers       = getLeastSuperClasses(leftClasses, rightClasses)
          supers.map {
            case (baseCls, lhsIdx, rhsIdx) =>
              getTypeForAppending(
                leftClasses(lhsIdx),
                rightClasses(rhsIdx),
                baseCls,
                Int.MaxValue,
                checkWeak            = false,
                stopAddingUpperBound = false
              )
          }.foldLeft[ScType](projectContext.stdTypes.Any)(ScAndType(_, _))
        }
      case _ => orTp
    }
  }
}
