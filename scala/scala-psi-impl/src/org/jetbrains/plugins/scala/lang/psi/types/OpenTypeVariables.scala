package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.light.DummyLightTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter, UndefinedType}
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
 * An open type variable is represented by an [[UndefinedType]] of a fresh type parameter, see [[isOpen]].
 * Every one is distinct, even if two of them are created for the same type parameter of a method,
 * so that the type variables of two invocations of the method are solved independently.
 * A type with open type variables must not be compared or joined with other types, the undefined types would
 * conform to anything without being constrained. It is only passed on to where the branches are joined.
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

        val subst = ScSubstitutor.bind(nonVariant, variables.map(UndefinedType(_)))
        val others = typeParameters.filterNot(nonVariant.contains)

        ScTypePolymorphicType(subst(internalType), others).inferValueType
      }
    case _ => tpe.inferValueType
  }

  /**
   * Whether the branches of an `if`, a `match` or a `try` typed against `expectedType` leave the type variables
   * that occur non-variantly in their types open: if there is no expected type, or if it is an undetermined type
   * parameter (e.g. the parameter type of a polymorphic method, for an argument), which the compiler represents
   * by a type variable `?T`, that the types of the branches only bound from below.
   */
  def leftOpenBy(expectedType: Option[ScType]): Boolean = expectedType match {
    case None                                  => true
    case Some(ScAbstractType(_, lower, upper)) => lower.isNothing && upper.isAny
    case Some(_: UndefinedType)                => true
    case _                                     => false
  }

  /** The open type variables in `tpe`. */
  def in(tpe: ScType): Seq[TypeParameter] = {
    val variables = Seq.newBuilder[TypeParameter]
    var seen = Set.empty[Long]

    tpe.visitRecursively {
      case UndefinedType(tp, _) if isOpen(tp) && !seen.contains(tp.typeParamId) =>
        seen += tp.typeParamId
        variables += tp
      case _ =>
    }

    variables.result()
  }

  /** Whether there are open type variables in `tpe`. */
  def existIn(tpe: ScType): Boolean =
    tpe.subtypeExists {
      case UndefinedType(tp, _) => isOpen(tp)
      case _                    => false
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

      def withRepresentatives(tpe: ScType): ScType =
        ScSubstitutor.bind(variables)(tp => UndefinedType(representative(tp)))(tpe)

      // As in the compiler, the constraint of a type variable starts out as its declared bounds, so that they are
      // passed on when it is unified with another one. Only the lower bounds are recorded: a type variable that is
      // only bounded from above is solved as its upper bound, but an open one is instantiated to its lower bound
      var constraints = variables.foldLeft(ConstraintSystem.empty)((cs, tp) => cs.withLower(tp, tp.lowerType))

      // The constraint system doesn't relate two undefined types of the same level, so the open type variables at the
      // same position of two applications of the same type constructor are unified before the types are compared
      def unify(tp1: ScType, tp2: ScType): Unit = (tp1, tp2) match {
        case (UndefinedType(v1, _), UndefinedType(v2, _)) if isOpen(v1) && isOpen(v2) =>
          val (r1, r2) = (representative(v1), representative(v2))
          if (r1.typeParamId != r2.typeParamId) {
            unified = unified.updated(r2.typeParamId, r1)
            constraints = constraints.withUnified(r2, into = r1)
          }
        case (ParameterizedType(d1, args1), ParameterizedType(d2, args2))
          if args1.length == args2.length && d1.equiv(d2) =>
          args1.lazyZip(args2).foreach(unify)
        case _ =>
      }

      // `isSubType(sub, sup, whenFrozen = false)`: whether `sub` is a subtype of `sup`,
      // possibly after constraining the open type variables
      def isSubtype(sub: ScType, sup: ScType): Boolean = {
        val (unifiedBefore, constraintsBefore) = (unified, constraints)
        unify(sup, sub)

        withRepresentatives(sub).conforms(withRepresentatives(sup), constraints) match {
          case cs: ConstraintSystem =>
            constraints = cs
            true
          case _ =>
            unified = unifiedBefore
            constraints = constraintsBefore
            false
        }
      }

      val bounds = projectContext.project.getService(classOf[Scala3Bounds])

      // `OrType.widenUnionWithoutNull`, only for the constraints it takes
      def widenUnion(tpe: ScType): ScType = tpe match {
        case ScOrType(l, r) => bounds.lubConstraining(widenUnion(l), widenUnion(r))(isSubtype)
        case _              => tpe
      }

      //records constraints
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
          case _                                                                     => rep.lowerType
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
}
