package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType

/**
 * The equivalence of intersection and union types is not structural: `A & (A & B)`, `(A & B) & A`, `B & A` and
 * `A & B` are all the same type.
 */
private[types] object AndOrTypeEquivalence {

  /**
   * Whether `tp1` and `tp2`, at least one of which is an intersection or a union, are the same type because each of
   * them is a subtype of the other, as `TypeComparer.isSameType` checks.
   *
   * The top-level intersections and unions are split in the order of `TypeComparer.isSubType`, and only the remaining
   * operands are checked for conformance. That way, the conformance of `tp1` and `tp2` themselves is never asked for,
   * which might be the very check that asks for their equivalence, see `checkEquiv` in [[ScalaConformance]].
   *
   * @param falseUndef whether an undefined type differs from any other type, in which case types with undefined
   *                   types are not compared, as conformance would constrain them
   */
  def equivBySubtyping(
    tp1:         ScType,
    tp2:         ScType,
    constraints: ConstraintSystem,
    falseUndef:  Boolean
  )(implicit
    context: Context
  ): ConstraintsResult =
    if (falseUndef && (hasUndefinedTypes(tp1) || hasUndefinedTypes(tp2))) ConstraintsResult.Left
    else
      isSubtype(tp1, tp2, constraints) match {
        case cs: ConstraintSystem   => isSubtype(tp2, tp1, cs)
        case ConstraintsResult.Left => ConstraintsResult.Left
      }

  private def hasUndefinedTypes(tpe: ScType): Boolean = tpe.subtypeExists(_.is[UndefinedType])

  private def isSubtype(sub: ScType, sup: ScType, constraints: ConstraintSystem)(implicit context: Context): ConstraintsResult = {
    def both(first: ConstraintSystem => ConstraintsResult, second: ConstraintSystem => ConstraintsResult): ConstraintsResult =
      first(constraints) match {
        case cs: ConstraintSystem   => second(cs)
        case ConstraintsResult.Left => ConstraintsResult.Left
      }

    // Like `TypeComparer.sufficientEither`, an alternative that doesn't constrain is preferred, e.g. for `?T & A <: A`
    def isUnconstrained(cs: ConstraintSystem): Boolean = cs == constraints

    def either(first: ConstraintsResult, second: => ConstraintsResult): ConstraintsResult =
      first match {
        case ConstraintsResult.Left                     => second
        case cs: ConstraintSystem if isUnconstrained(cs) => cs
        case constrained: ConstraintSystem =>
          second match {
            case cs: ConstraintSystem if isUnconstrained(cs) => cs
            case _                                          => constrained
          }
      }

    def eitherOperand(l: ScType, r: ScType): ConstraintsResult =
      either(isSubtype(l, sup, constraints), isSubtype(r, sup, constraints))

    (sub, sup) match {
      case (_, ScAndType(l, r)) => both(isSubtype(sub, l, _), isSubtype(sub, r, _))
      case (ScOrType(l, r), _)  => both(isSubtype(l, sup, _), isSubtype(r, sup, _))
      // a type variable is constrained by the other type as a whole, e.g. `?T >: A & B` rather than `?T >: A`
      case (_: UndefinedType, _) | (_, _: UndefinedType) => sub.conforms(sup, constraints)
      case (_, ScOrType(l, r))  =>
        either(
          either(isSubtype(sub, l, constraints), isSubtype(sub, r, constraints)),
          sub match {
            case ScAndType(subL, subR) => eitherOperand(subL, subR)
            case _                     => ConstraintsResult.Left
          }
        )
      case (ScAndType(l, r), _) => eitherOperand(l, r)
      case _                    => sub.conforms(sup, constraints)
    }
  }
}
