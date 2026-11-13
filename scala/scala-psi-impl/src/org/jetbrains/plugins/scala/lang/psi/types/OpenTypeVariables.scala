package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.light.DummyLightTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.immutable.LongMap

/**
 * The type variables that the Scala 3 compiler leaves uninstantiated in the type of an expression.
 *
 * Right after an expression is typed, `Inferencing.interpolateTypeVars` instantiates the type variables of its type
 * that occur co- or contravariantly in it, but it leaves the ones that occur non-variantly (invariantly, or both co-
 * and contravariantly) untouched. Such a type variable can still be constrained by a later comparison, in particular
 * when the types of the branches of an `if`, a `match` or a `try` are joined
 * (`Namer.inferredResultType` → `ConstraintHandling.widenInferred` → `OrType.widenUnionWithoutNull` →
 * `TypeComparer.lub(canConstrain = true)`):
 * {{{
 *   val xs: Set[Foo] = ???
 *   val a = if (c) xs else Set.empty // Set[Foo], the type argument of Set.empty is Foo
 * }}}
 *
 * An open type variable is represented by a [[TypeParameterType]] of a fresh type parameter, see [[isOpen]].
 * Every one is distinct, even if two of them are created for the same type parameter of a method,
 * so that the type variables of two invocations of the method are solved independently.
 */
object OpenTypeVariables {

  /** The type parameter of an open type variable. */
  private final class OpenTypeVariableParam(name: String)(implicit pc: ProjectContext) extends DummyLightTypeParam(name)

  def isOpen(tp: TypeParameter): Boolean = tp.psiTypeParameter.isInstanceOf[OpenTypeVariableParam]

  /**
   * Instantiates the type parameters of the polymorphic type `tpe` of an expression, as
   * `Inferencing.interpolateTypeVars` does, except for the ones that occur non-variantly in the type,
   * which are replaced by open type variables. Any other type is just converted to a value type.
   */
  def interpolate(tpe: ScType): ScType = tpe match {
    case ScTypePolymorphicType(internalType, typeParameters) if internalType.isValue && typeParameters.nonEmpty =>
      val nonVariant = typeParameters.filter(_.varianceInType(internalType).isInvariant)

      if (nonVariant.isEmpty) tpe.inferValueType
      else {
        implicit val projectContext: ProjectContext = tpe.projectContext

        val ids = typeParameters.map(_.typeParamId).toSet

        def bound(tpe: ScType, ifRecursive: ScType): ScType =
          if (tpe.hasRecursiveTypeParameters(ids)) ifRecursive else tpe

        val variables = nonVariant.map { tp =>
          TypeParameter(
            new OpenTypeVariableParam(tp.name),
            tp.typeParameters,
            bound(tp.lowerType, api.Nothing),
            bound(tp.upperType, api.Any)
          )
        }

        val subst = ScSubstitutor.bind(nonVariant, variables.map(TypeParameterType(_)))
        val others = typeParameters.filterNot(nonVariant.contains)

        ScTypePolymorphicType(subst(internalType), others).inferValueType
      }
    case _ => tpe.inferValueType
  }

  /** The open type variables in `tpe`. */
  def in(tpe: ScType): Seq[TypeParameter] = {
    val variables = Seq.newBuilder[TypeParameter]
    var seen = Set.empty[Long]

    tpe.visitRecursively {
      case TypeParameterType(tp) if isOpen(tp) && !seen.contains(tp.typeParamId) =>
        seen += tp.typeParamId
        variables += tp
      case _ =>
    }

    variables.result()
  }

  /**
   * Instantiates the open type variables in `tpe`.
   *
   * They are constrained as `OrType.widenUnionWithoutNull` constrains them, by joining the operands of the
   * union `tpe` with `TypeComparer.lub(canConstrain = true)`, which merges an operand into another one, if
   * it is a subtype of that one, possibly after constraining the type variables. Then they are instantiated
   * from the constraints, the ones that weren't constrained to their lower bounds, as `Inferencing.fullyDefinedType`
   * does for a type variable that occurs non-variantly.
   *
   * Note that the result is not the join itself: like the type of an `if` in the Scala 3 compiler,
   * it is the union with the type variables instantiated, which is joined when it is widened.
   */
  def solve(tpe: ScType)(implicit context: Context): ScType = {
    val variables = in(tpe)

    if (variables.isEmpty) tpe
    else {
      implicit val projectContext: ProjectContext = tpe.projectContext

      // The type variables unified with another one (`ConstraintHandling.unify`), mapped to that one
      var unified = LongMap.empty[TypeParameter]

      def representative(tp: TypeParameter): TypeParameter =
        unified.get(tp.typeParamId).fold(tp)(representative)

      def undefine(tpe: ScType): ScType =
        ScSubstitutor.bind(variables)(tp => UndefinedType(representative(tp)))(tpe)

      // The constraint system doesn't relate two undefined types, so the open type variables at the same
      // position of two applications of the same type constructor are unified before the types are compared
      def unify(tp1: ScType, tp2: ScType): Unit = (tp1, tp2) match {
        case (TypeParameterType(v1), TypeParameterType(v2)) if isOpen(v1) && isOpen(v2) =>
          val (r1, r2) = (representative(v1), representative(v2))
          if (r1.typeParamId != r2.typeParamId) unified = unified.updated(r2.typeParamId, r1)
        case (ParameterizedType(d1, args1), ParameterizedType(d2, args2))
          if args1.length == args2.length && d1.equiv(d2) =>
          args1.lazyZip(args2).foreach(unify)
        case _ =>
      }

      var constraints = ConstraintSystem.empty

      // whether `tp2` is a subtype of `tp1`, possibly after constraining the open type variables
      def constrainSubtype(tp2: ScType, tp1: ScType): Boolean = {
        val unifiedBefore = unified
        unify(tp1, tp2)

        undefine(tp2).conforms(undefine(tp1), constraints) match {
          case cs: ConstraintSystem => constraints = cs; true
          case _                    => unified = unifiedBefore; false
        }
      }

      // `TypeComparer.mergeIfSuper`: `tp1`, if `tp2` is a subtype of it or of one of its operands
      def mergeIfSuper(tp1: ScType, tp2: ScType): Option[ScType] =
        if (constrainSubtype(tp2, tp1)) Option(tp1)
        else
          tp1 match {
            case ScOrType(l, r) => mergeIfSuper(l, tp2).orElse(mergeIfSuper(r, tp2)).map(_ => tp1)
            case _              => None
          }

      def lub(tp1: ScType, tp2: ScType): ScType =
        mergeIfSuper(tp1, tp2)
          .orElse(mergeIfSuper(tp2, tp1))
          .getOrElse(ScOrType(tp1, tp2))

      def widenUnion(tpe: ScType): ScType = tpe match {
        case ScOrType(l, r) => lub(widenUnion(l), widenUnion(r))
        case _              => tpe
      }

      widenUnion(tpe)

      val solved: LongMap[ScType] =
        if (constraints.isEmpty) LongMap.empty
        else
          constraints
            .substitutionBounds(canThrowSCE = false, widenInferredTypeArguments = true)
            .fold(LongMap.empty[ScType])(_.tvMap)

      val instantiate = ScSubstitutor.bind(variables) { tp =>
        val rep = representative(tp)
        solved.get(rep.typeParamId) match {
          case Some(solution) if in(solution).isEmpty && !solution.is[UndefinedType] => solution
          case _                                                                   => rep.lowerType
        }
      }

      // The operands of the union that became equal are merged
      def rejoin(tpe: ScType): ScType = tpe match {
        case ScOrType(l, r) => rejoin(l).lub(rejoin(r))
        case _              => tpe
      }

      rejoin(instantiate(tpe))
    }
  }

  /**
   * Instantiates the open type variables in `tpe` to their lower bounds, without constraining them,
   * as `Inferencing.fullyDefinedType` does for type variables that occur non-variantly.
   */
  def instantiate(tpe: ScType): ScType = {
    val variables = in(tpe)

    if (variables.isEmpty) tpe
    else ScSubstitutor.bind(variables)(_.lowerType)(tpe)
  }
}
