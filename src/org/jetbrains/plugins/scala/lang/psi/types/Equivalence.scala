package org.jetbrains.plugins.scala.lang.psi.types

import nonvalue.{ScTypePolymorphicType, ScMethodType}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import result.Success
import annotation.tailrec

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.04.2010
 */

object Equivalence {
  def equiv(l: ScType, r: ScType): Boolean =
    equivInner(l, r, new ScUndefinedSubstitutor)._1

  def undefinedSubst(l: ScType, r: ScType): ScUndefinedSubstitutor =
    equivInner(l, r, new ScUndefinedSubstitutor)._2

  def equivInner(l: ScType, r: ScType, subst: ScUndefinedSubstitutor, falseUndef: Boolean = true): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled
    (l, r) match {
      case (_, _: ScUndefinedType) => r.equivInner(l, subst, falseUndef)
      case (_, AnyRef) => r.equivInner(l, subst, falseUndef)
      case (_: StdType, _: ScProjectionType) => r.equivInner(l, subst, falseUndef)
      case (_: ScDesignatorType, _: ScThisType) => r.equivInner(l, subst, falseUndef)
      case (p: ScParameterizedType, _: ScFunctionType) => r.equivInner(l, subst, falseUndef)
      case (p: ScParameterizedType, _: ScTupleType) => r.equivInner(l, subst, falseUndef)
      case (_, ScParameterizedType(proj: ScProjectionType, _))
        if proj.actualElement.isInstanceOf[ScTypeAliasDefinition] => r.equivInner(l, subst, falseUndef)
      case (_, ScParameterizedType(ScDesignatorType(_: ScTypeAliasDefinition), _)) =>
        r.equivInner(l, subst, falseUndef)
      case (_, ScDesignatorType(_: ScTypeAliasDefinition)) => r.equivInner(l, subst, falseUndef)
      case (_: ScParameterizedType, _: JavaArrayType) => r.equivInner(l, subst, falseUndef)
      case (_, proj: ScProjectionType) => r.equivInner(l, subst, falseUndef)
      case _ => l.equivInner(r, subst, falseUndef)
    }
  }
}