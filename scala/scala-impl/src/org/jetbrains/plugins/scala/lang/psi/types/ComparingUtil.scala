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
  def isNeverSubClass(subClass: PsiClass, supClass: PsiClass): Boolean = {
    val classes = Seq(subClass, supClass)
    val oneFinal = subClass.isEffectivelyFinal || supClass.isEffectivelyFinal
    val twoNonTraitsOrInterfaces = !classes.exists(_.isInterface)

    def inheritorsInSameFile(clazz: PsiClass) = {
      ClassInheritorsSearch.search(clazz, new LocalSearchScope(clazz.getContainingFile), true)
        .findAll()
        .asScala
        .collect {
          case x: ScTypeDefinition => x
        }
    }

    def sealedAndAllChildrenAreIrreconcilable(subClass: PsiClass, supClass: PsiClass): Boolean =
      subClass.isSealed && inheritorsInSameFile(subClass).forall(isNeverSubClass(_, supClass))

    def oneClazzIsSealedAndAllChildrenAreIrreconcilable: Boolean =
      sealedAndAllChildrenAreIrreconcilable(subClass, supClass) || sealedAndAllChildrenAreIrreconcilable(supClass, subClass)

    val areUnrelatedClasses =
      !areClassesEquivalent(subClass, supClass) && !(subClass.isInheritor(supClass, true) || supClass.isInheritor(subClass, true))

    areUnrelatedClasses && (oneFinal || twoNonTraitsOrInterfaces || oneClazzIsSealedAndAllChildrenAreIrreconcilable)
  }

  def isNeverSubType(sub: ScType, sup: ScType, sameType: Boolean = false): Boolean = {
    if (sup.weakConforms(sub) || sub.weakConforms(sup))
      return false

    // we already know that sup is not assignable to sub and vice versa
    // if sup is now also an AnyVal, sub cannot be a subtype of sup
    val anyVal = sup.projectContext.stdTypes.AnyVal
    if (sup.weakConforms(anyVal))
      return true

    val Seq(subClassOpt, supClassOpt) =
      Seq(sub, sup).map(_.widen.extractClass)
    if (subClassOpt.isEmpty || supClassOpt.isEmpty)
      return false
    val (subClass, supClass) = (subClassOpt.get, supClassOpt.get)

    def isNeverSameType(sub: ScType, sup: ScType) = isNeverSubType(sub, sup, sameType = true)

    def isNeverSubArgs(subTps: Iterable[ScType], supTps: Iterable[ScType], tparams: Iterable[PsiTypeParameter]): Boolean = {
      def isNeverSubArg(subTp: ScType, supTp: ScType, variance: Variance): Boolean = {

        variance match {
            // needed for type patterns like { case x: X[t] =>  }
            // t is ScExistentialArgument and we cannot abstractify this in ScPatternAnnotator.abstraction
            // TODO: implement correctly when TypePatterns are implemented
          case _ if subTp.is[ScExistentialArgument] => false
          case Covariant     => isNeverSubType(subTp, supTp)
          case Contravariant => isNeverSubType(supTp, subTp)
          case Invariant     => !subTp.equiv(supTp)
        }
      }
      def getVariance(tp: PsiTypeParameter) = tp match {
        case scParam: ScTypeParam => scParam.variance
        case _ => Invariant
      }
      subTps.zip(supTps).zip(tparams.map(getVariance)).exists {
        case ((subTp, supTp), vr) => isNeverSubArg(subTp, supTp, vr)
        case _ => false
      }
    }

    def neverSubArgs() = {
      (sub, sup) match {
        case (ParameterizedType(_, args1), ParameterizedType(_, args2)) =>
          isNeverSubArgs(args1, args2, supClass.getTypeParameters)
        case _ => false
      }
    }

    isNeverSubClass(subClass, supClass) ||
            ((areClassesEquivalent(subClass, supClass) || (!sameType) && (subClass.isInheritor(supClass, true) || supClass.isInheritor(subClass, true))) && neverSubArgs())
  }
}
