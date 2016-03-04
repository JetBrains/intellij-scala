package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.{Computable, RecursionManager}
import com.intellij.util.containers.ConcurrentWeakHashMap
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

/**
 * User: Alexander Podkhalyuzin
 * Date: 28.04.2010
 */

object Equivalence {
  def equiv(l: ScType, r: ScType): Boolean =
    equivInner(l, r, new ScUndefinedSubstitutor)._1

  def undefinedSubst(l: ScType, r: ScType): ScUndefinedSubstitutor =
    equivInner(l, r, new ScUndefinedSubstitutor)._2

  val guard = RecursionManager.createGuard("equivalence.guard")
  val eval = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  val cache: ConcurrentWeakHashMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)] =
    new ConcurrentWeakHashMap[(ScType, ScType, Boolean), (Boolean, ScUndefinedSubstitutor)]()

  /**
   * @param falseUndef use false to consider undef type equals to any type
   */
  def equivInner(l: ScType, r: ScType, subst: ScUndefinedSubstitutor,
                 falseUndef: Boolean = true): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled()

    if (l == r) return (true, subst)

    val key = (l, r, falseUndef)

    val nowEval = eval.get()
    val tuple = if (nowEval) null else {
      try {
        eval.set(true)
        cache.get(key)
      } finally {
        eval.set(false)
      }
    }
    if (tuple != null) {
      if (subst.isEmpty) return tuple
      return tuple.copy(_2 = subst + tuple._2)
    }

    if (guard.currentStack().contains(key)) {
      return (false, new ScUndefinedSubstitutor())
    }

    val uSubst = new ScUndefinedSubstitutor()

    def comp(): (Boolean, ScUndefinedSubstitutor) = {
      l match {
        case designator: ScDesignatorType => designator.getValType match {
          case Some(valType) => return equivInner(valType, r, subst, falseUndef)
          case _ =>
        }
        case _ =>
      }

      r match {
        case designator: ScDesignatorType => designator.getValType match {
          case Some(valType) => return equivInner(l, valType, subst, falseUndef)
          case _ =>
        }
        case _ =>
      }

      r.isAliasType match {
        case Some(AliasType(ta: ScTypeAliasDefinition, _, _)) => return r.equivInner(l, subst, falseUndef)
        case _ =>
      }

      l.isAliasType match {
        case Some(AliasType(ta: ScTypeAliasDefinition, _, _)) => return l.equivInner(r, subst, falseUndef)
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
        case (_: ScParameterizedType, _: JavaArrayType) => r.equivInner(l, subst, falseUndef)
        case (_, proj: ScProjectionType) => r.equivInner(l, subst, falseUndef)
        case (_, proj: ScCompoundType) => r.equivInner(l, subst, falseUndef)
        case (_, ex: ScExistentialType) => r.equivInner(l, subst, falseUndef)
        case _ => l.equivInner(r, subst, falseUndef)
      }
    }
    val res = guard.doPreventingRecursion(key, false, new Computable[(Boolean, ScUndefinedSubstitutor)] {
      def compute(): (Boolean, ScUndefinedSubstitutor) = comp()
    })
    if (res == null) return (false, new ScUndefinedSubstitutor())
    if (!nowEval) {
      try {
        eval.set(true)
        cache.put(key, res)
      } finally {
        eval.set(false)
      }
    }
    if (subst.isEmpty) return res
    res.copy(_2 = subst + res._2)
  }
}