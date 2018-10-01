package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Computable
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.04.2010
 */

trait ScalaEquivalence extends api.Equivalence {
  typeSystem: api.TypeSystem =>

  override protected def equivComputable(left: ScType, right: ScType, constraints: ConstraintSystem,
                                         falseUndef: Boolean): Computable[ConstraintsResult] = new Computable[ConstraintsResult] {
    override def compute(): ConstraintsResult = {
      left match {
        case designator: ScDesignatorType => designator.getValType match {
          case Some(valType) => return equivInner(valType, right, constraints, falseUndef)
          case _ =>
        }
        case _ =>
      }

      right match {
        case designator: ScDesignatorType => designator.getValType match {
          case Some(valType) => return equivInner(left, valType, constraints, falseUndef)
          case _ =>
        }
        case _ =>
      }

      (left, right) match {
        case (UndefinedType(_, _), _) if right.isAliasType.isDefined =>
          val t = left.equivInner(right, constraints, falseUndef)
          if (t.isRight) return t
        case (_, UndefinedType(_, _)) if left.isAliasType.isDefined =>
          val t = left.equivInner(right, constraints, falseUndef)
          if (t.isRight) return t
        case (ParameterizedType(UndefinedType(_, _), _), _) if right.isAliasType.isDefined =>
          val t = left.equivInner(right, constraints, falseUndef)
          if (t.isRight) return t
        case (_, ParameterizedType(UndefinedType(_, _), _)) if left.isAliasType.isDefined =>
          val t = right.equivInner(left, constraints, falseUndef)
          if (t.isRight) return t
        case _ =>
      }

      right.isAliasType match {
        case Some(AliasType(_: ScTypeAliasDefinition, Right(right), _)) => return equivInner(left, right, constraints, falseUndef)
        case _ =>
      }

      left.isAliasType match {
        case Some(AliasType(_: ScTypeAliasDefinition, Right(left), _)) => return equivInner(left, right, constraints, falseUndef)
        case _ =>
      }

      (left, right) match {
        case (_, _: UndefinedType) => right.equivInner(left, constraints, falseUndef)
        case (_: UndefinedType, _) => left.equivInner(right, constraints, falseUndef)
        case (_, _: ScAbstractType) => right.equivInner(left, constraints, falseUndef)
        case (_: ScAbstractType, _) => left.equivInner(right, constraints, falseUndef)
        case (_, ParameterizedType(_: ScAbstractType, _)) => right.equivInner(left, constraints, falseUndef)
        case (ParameterizedType(_: ScAbstractType, _), _) => left.equivInner(right, constraints, falseUndef)
        case (_, t) if t.isAnyRef => right.equivInner(left, constraints, falseUndef)
        case (_: StdType, _: ScProjectionType) => right.equivInner(left, constraints, falseUndef)
        case (_: ScDesignatorType, _: ScThisType) => right.equivInner(left, constraints, falseUndef)
        case (_: ScParameterizedType, _: JavaArrayType) => right.equivInner(left, constraints, falseUndef)
        case (_, _: ScExistentialType) => right.equivInner(left, constraints, falseUndef)
        case (_, _: ScProjectionType) => right.equivInner(left, constraints, falseUndef)
        case (_, _: ScCompoundType) => right.equivInner(left, constraints, falseUndef)
        case _ => left.equivInner(right, constraints, falseUndef)
      }
    }
  }
}
