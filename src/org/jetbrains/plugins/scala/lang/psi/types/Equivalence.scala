package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Computable
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.04.2010
 */

object Equivalence extends api.Equivalence {
  override implicit lazy val typeSystem = ScalaTypeSystem

  override protected def computable(left: ScType, right: ScType, substitutor: ScUndefinedSubstitutor,
                                    falseUndef: Boolean) = new Computable[(Boolean, ScUndefinedSubstitutor)] {
    override def compute(): (Boolean, ScUndefinedSubstitutor) = {
      left match {
        case designator: ScDesignatorType => designator.getValType match {
          case Some(valType) => return equivInner(valType, right, substitutor, falseUndef)
          case _ =>
        }
        case _ =>
      }

      right match {
        case designator: ScDesignatorType => designator.getValType match {
          case Some(valType) => return equivInner(left, valType, substitutor, falseUndef)
          case _ =>
        }
        case _ =>
      }

      right.isAliasType match {
        case Some(AliasType(ta: ScTypeAliasDefinition, _, _)) => return right.equivInner(left, substitutor, falseUndef)
        case _ =>
      }

      left.isAliasType match {
        case Some(AliasType(ta: ScTypeAliasDefinition, _, _)) => return left.equivInner(right, substitutor, falseUndef)
        case _ =>
      }

      (left, right) match {
        case (_, _: ScUndefinedType) => right.equivInner(left, substitutor, falseUndef)
        case (_: ScUndefinedType, _) => left.equivInner(right, substitutor, falseUndef)
        case (_, _: ScAbstractType) => right.equivInner(left, substitutor, falseUndef)
        case (_: ScAbstractType, _) => left.equivInner(right, substitutor, falseUndef)
        case (_, ScParameterizedType(_: ScAbstractType, _)) => right.equivInner(left, substitutor, falseUndef)
        case (ScParameterizedType(_: ScAbstractType, _), _) => left.equivInner(right, substitutor, falseUndef)
        case (_, AnyRef) => right.equivInner(left, substitutor, falseUndef)
        case (_: StdType, _: ScProjectionType) => right.equivInner(left, substitutor, falseUndef)
        case (_: ScDesignatorType, _: ScThisType) => right.equivInner(left, substitutor, falseUndef)
        case (_: ScParameterizedType, _: JavaArrayType) => right.equivInner(left, substitutor, falseUndef)
        case (_, proj: ScProjectionType) => right.equivInner(left, substitutor, falseUndef)
        case (_, proj: ScCompoundType) => right.equivInner(left, substitutor, falseUndef)
        case (_, ex: ScExistentialType) => right.equivInner(left, substitutor, falseUndef)
        case _ => left.equivInner(right, substitutor, falseUndef)
      }
    }
  }
}