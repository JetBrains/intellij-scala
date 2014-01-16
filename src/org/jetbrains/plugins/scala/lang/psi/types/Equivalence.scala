package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.04.2010
 */

object Equivalence {
  def equiv(l: ScType, r: ScType): Boolean =
    equivInner(l, r, new ScUndefinedSubstitutor)._1

  def undefinedSubst(l: ScType, r: ScType): ScUndefinedSubstitutor =
    equivInner(l, r, new ScUndefinedSubstitutor)._2

  /**
   * @param falseUndef use false to consider undef type equals to any type
   */
  def equivInner(l: ScType, r: ScType, subst: ScUndefinedSubstitutor,
                 falseUndef: Boolean = true): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled()
    
    if (l == r) return (true, subst)

    if (l.isInstanceOf[ScDesignatorType] && l.getValType != None) {
      return equivInner(l.getValType.get, r, subst, falseUndef)
    }
    if (r.isInstanceOf[ScDesignatorType] && r.getValType != None) {
      return equivInner(l, r.getValType.get, subst, falseUndef)
    }

    r.isAliasType match {
      case Some(Conformance.AliasType(ta: ScTypeAliasDefinition, _, _)) => return r.equivInner(l, subst, falseUndef)
      case _ =>
    }

    l.isAliasType match {
      case Some(Conformance.AliasType(ta: ScTypeAliasDefinition, _, _)) => return l.equivInner(r, subst, falseUndef)
      case _ =>
    }

    (l, r) match {
      case (_, _: ScUndefinedType) => r.equivInner(l, subst, falseUndef)
      case (_: ScUndefinedType, _) => l.equivInner(r, subst, falseUndef)
      case (_, _: ScAbstractType) => r.equivInner(l, subst, falseUndef)
      case (_: ScAbstractType, _) => l.equivInner(r, subst, falseUndef)
      case (_, ScParameterizedType(_: ScAbstractType, _)) => r.equivInner(l, subst, falseUndef)
      case (ScParameterizedType(_: ScAbstractType, _), _) => l.equivInner(r, subst, falseUndef)
      case (_, AnyRef) => r.equivInner(l, subst, falseUndef)
      case (_: StdType, _: ScProjectionType) => r.equivInner(l, subst, falseUndef)
      case (_: ScDesignatorType, _: ScThisType) => r.equivInner(l, subst, falseUndef)
      case (p: ScParameterizedType, _: ScFunctionType) => r.equivInner(l, subst, falseUndef)
      case (p: ScParameterizedType, _: ScTupleType) => r.equivInner(l, subst, falseUndef)
      case (_: ScParameterizedType, _: JavaArrayType) => r.equivInner(l, subst, falseUndef)
      case (_, proj: ScProjectionType) => r.equivInner(l, subst, falseUndef)
      case (_, proj: ScCompoundType) => r.equivInner(l, subst, falseUndef)
      case (_, ex: ScExistentialType) => r.equivInner(l, subst, falseUndef)
      case _ => l.equivInner(r, subst, falseUndef)
    }
  }
}