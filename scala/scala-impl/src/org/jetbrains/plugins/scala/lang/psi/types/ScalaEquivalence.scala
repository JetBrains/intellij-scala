package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType

import java.util.function.Supplier

trait ScalaEquivalence extends api.Equivalence {
  typeSystem: api.TypeSystem =>

  override protected def equivComputable(key: Key): Supplier[ConstraintsResult] = new Supplier[ConstraintsResult] {
    import ConstraintSystem.empty

    override def get(): ConstraintsResult = {
      val Key(left, right, falseUndef) = key
      left match {
        case designator: ScDesignatorType =>
          designator.getValType match {
            case Some(valType) => return equivInner(valType, right, falseUndef = falseUndef)
            case _             =>
          }
        case _ =>
      }

      right match {
        case designator: ScDesignatorType =>
          designator.getValType match {
            case Some(valType) => return equivInner(left, valType, falseUndef = falseUndef)
            case _             =>
          }
        case _ =>
      }

      def containsUndefinedTypes(tpe: ScType): Boolean = tpe.subtypeExists {
        case UndefinedType(_, _) => true
        case _                   => false
      }

      if (right.isAliasType && containsUndefinedTypes(left)) {
        val t = left.equivInner(right, empty, falseUndef)
        if (t.isRight) return t
      } else if (left.isAliasType && containsUndefinedTypes(right)) {
        val t = right.equivInner(left, empty, falseUndef)
        if (t.isRight) return t
      }

      (left, right) match {
        /** It is important to handle the following cases here, because
         * dealising type might be the wrong thing to do in a higher-kinded
         * scenario, e.g. for `type F[A] = A` type `F` in `Functor[F]` is not equivalent
         * to just `A`, but is equivalent to `[A] F[A]`.
         * */
        case (tpt: ScTypePolymorphicType, des: DesignatorOwner) =>
          return des.equivInner(tpt, empty, falseUndef)
        case (des: ScDesignatorType, tpt: ScTypePolymorphicType) =>
          return des.equivInner(tpt, empty, falseUndef)
        case (AliasType(_, Right(tpt: ScTypePolymorphicType), _), des: DesignatorOwner) =>
          return des.equivInner(tpt, empty, falseUndef)
        case (des: DesignatorOwner, AliasType(_, Right(tpt: ScTypePolymorphicType), _)) =>
          return des.equivInner(tpt, empty, falseUndef)
        /**
         * A workaround for `type Foo <: Nothing`, if an abstract type has `Nothing` as its upper bound
         * it can be no other type, but `Nothing` itself. A big of a weird edge-case, but it affects zio users.
         * See: https://youtrack.jetbrains.com/issue/SCL-22598/Extension-methods-are-not-resolved-for-abstract-type-ZNothing-Nothing
         */
        case (AliasType(_, _, Right(upper)), nothing) if nothing.isNothing && upper.isNothing =>
          return ConstraintSystem.empty
        case (nothing, AliasType(_, _, Right(upper))) if nothing.isNothing && upper.isNothing =>
          return ConstraintSystem.empty
        case _ =>
      }

      right match {
        case AliasType(_: ScTypeAliasDefinition, Right(right), _) =>
          return equivInner(left, right, falseUndef = falseUndef)
        case _ =>
      }

      left match {
        case AliasType(_: ScTypeAliasDefinition, Right(left), _) =>
          return equivInner(left, right, falseUndef = falseUndef)
        case _ =>
      }

      (left, right) match {
        case (_, _: UndefinedType)  => right.equivInner(left, empty, falseUndef)
        case (_: UndefinedType, _)  => left.equivInner(right, empty, falseUndef)
        case (_, _: ScAbstractType) => right.equivInner(left, empty, falseUndef)
        case (_: ScAbstractType, _) => left.equivInner(right, empty, falseUndef)
        case (_, ParameterizedType(_: ScAbstractType, _)) =>
          right.equivInner(left, empty, falseUndef)
        case (ParameterizedType(_: ScAbstractType, _), _) =>
          left.equivInner(right, empty, falseUndef)
        case (_, t) if t.isAnyRef                 => right.equivInner(left, empty, falseUndef)
        case (_: StdType, _: ScProjectionType)    => right.equivInner(left, empty, falseUndef)
        case (_: ScDesignatorType, _: ScThisType) => right.equivInner(left, empty, falseUndef)
        case (_: ScParameterizedType, _: JavaArrayType) =>
          right.equivInner(left, empty, falseUndef)
        case (_, _: ScExistentialType) => right.equivInner(left, empty, falseUndef)
        case (_, _: ScProjectionType)  => right.equivInner(left, empty, falseUndef)
        case (_, _: ScCompoundType)    => right.equivInner(left, empty, falseUndef)
        case _                         => left.equivInner(right, empty, falseUndef)
      }
    }
  }
}
