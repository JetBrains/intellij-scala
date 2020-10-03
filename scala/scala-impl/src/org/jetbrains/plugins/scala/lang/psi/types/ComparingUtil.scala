package org.jetbrains.plugins.scala
package lang.psi.types

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.{PsiClass, PsiTypeParameter}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.{Contravariant, Covariant, Invariant, ParameterizedType, Variance}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

import scala.jdk.CollectionConverters._

/**
 * Nikolay.Tropin
 * 2014-04-03
 */
object ComparingUtil {
  //this relation is actually symmetric
  def isNeverSubClass(clazz1: PsiClass, clazz2: PsiClass): Boolean = {
    val classes = Seq(clazz1, clazz2)
    val oneFinal = clazz1.isEffectivelyFinal || clazz2.isEffectivelyFinal
    val twoNonTraitsOrInterfaces = !classes.exists(_.isInterface)

    def inheritorsInSameFile(clazz: PsiClass) = {
      ClassInheritorsSearch.search(clazz, new LocalSearchScope(clazz.getContainingFile), true)
        .findAll()
        .asScala
        .collect {
          case x: ScTypeDefinition => x
        }
    }

    def sealedAndAllChildrenAreIrreconcilable(clazz1: PsiClass, clazz2: PsiClass): Boolean =
      clazz1.isSealed && inheritorsInSameFile(clazz1).forall(isNeverSubClass(_, clazz2))

    def oneClazzIsSealedAndAllChildrenAreIrreconcilable: Boolean =
      sealedAndAllChildrenAreIrreconcilable(clazz1, clazz2) || sealedAndAllChildrenAreIrreconcilable(clazz2, clazz1)

    val areUnrelatedClasses =
      !areClassesEquivalent(clazz1, clazz2) && !(clazz1.isInheritor(clazz2, true) || clazz2.isInheritor(clazz1, true))

    areUnrelatedClasses && (oneFinal || twoNonTraitsOrInterfaces || oneClazzIsSealedAndAllChildrenAreIrreconcilable)
  }

  def isNeverSubType(tp1: ScType, tp2: ScType, sameType: Boolean = false): Boolean = {
    if (tp2.weakConforms(tp1) || tp1.weakConforms(tp2))
      return false

    // we already know that tp2 is not assignable to tp1 and vice versa
    // if tp2 is now also an anyval, tp1 cannot be a subtype of tp2
    val anyVal = tp2.projectContext.stdTypes.AnyVal
    if (tp2.weakConforms(anyVal))
      return true

    val Seq(clazzOpt1, clazzOpt2) =
      Seq(tp1, tp2).map(_.widen.extractClass)
    if (clazzOpt1.isEmpty || clazzOpt2.isEmpty)
      return false
    val (clazz1, clazz2) = (clazzOpt1.get, clazzOpt2.get)

    def isNeverSameType(tp1: ScType, tp2: ScType) = isNeverSubType(tp1, tp2, sameType = true)

    def isNeverSubArgs(tps1: Iterable[ScType], tps2: Iterable[ScType], tparams: Iterable[PsiTypeParameter]): Boolean = {
      def isNeverSubArg(t1: ScType, t2: ScType, variance: Variance) = {
        variance match {
          case Covariant     => isNeverSubType(t2, t1)
          case Contravariant => isNeverSubType(t1, t2)
          case Invariant     => isNeverSameType(t1, t2)
        }
      }
      def getVariance(tp: PsiTypeParameter) = tp match {
        case scParam: ScTypeParam => scParam.variance
        case _ => Invariant
      }
      tps1.zip(tps2).zip(tparams.map(getVariance)).exists {
        case ((t1, t2), vr) => isNeverSubArg(t1, t2, vr)
        case _ => false
      }
    }

    def neverSubArgs() = {
      (tp1, tp2) match {
        case (ParameterizedType(_, args1), ParameterizedType(_, args2)) =>
          isNeverSubArgs(args1, args2, clazz2.getTypeParameters)
        case _ => false
      }
    }

    isNeverSubClass(clazz1, clazz2) ||
            ((areClassesEquivalent(clazz1, clazz2) || (!sameType) && clazz1.isInheritor(clazz2, true)) && neverSubArgs())
  }
}
