package org.jetbrains.plugins.scala
package lang.psi.types

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.{PsiClass, PsiTypeParameter}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

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

    def inheritorsInSameFile(clazz: PsiClass) =
      ClassInheritorsSearch.search(clazz, new LocalSearchScope(clazz.getContainingFile), false).toArray(PsiClass.EMPTY_ARRAY).collect {
        case x: ScTypeDefinition => x
      }

    def sealedAndAllChildrenAreIrreconcilable = {
      val areSealed = classes.forall{
        case modOwner: ScModifierListOwner => modOwner.hasModifierProperty("sealed")
        case _ => false
      }
      def childrenAreIrreconcilable =
        inheritorsInSameFile(clazz1).forall {
          c1 => inheritorsInSameFile(clazz2).forall {
            c2 => isNeverSubClass(c1, c2)
          }
        }
      areSealed && childrenAreIrreconcilable
    }

    val areUnrelatedClasses =
      !areClassesEquivalent(clazz1, clazz2) && !(clazz1.isInheritor(clazz2, true) || clazz2.isInheritor(clazz1, true))

    areUnrelatedClasses && (oneFinal || twoNonTraitsOrInterfaces || sealedAndAllChildrenAreIrreconcilable)
  }

  def isNeverSubType(tp1: ScType, tp2: ScType, sameType: Boolean = false)
                    (implicit typeSystem: TypeSystem): Boolean = {
    if (tp2.weakConforms(tp1) || tp1.weakConforms(tp2)) return false

    val Seq(clazzOpt1, clazzOpt2) =
      Seq(tp1, tp2).map(t => ScType.extractDesignatorSingletonType(t).getOrElse(t))
        .map(ScType.extractClass(_))
    if (clazzOpt1.isEmpty || clazzOpt2.isEmpty) return false
    val (clazz1, clazz2) = (clazzOpt1.get, clazzOpt2.get)

    def isNeverSameType(tp1: ScType, tp2: ScType) = isNeverSubType(tp1, tp2, sameType = true)

    def isNeverSubArgs(tps1: Seq[ScType], tps2: Seq[ScType], tparams: Seq[PsiTypeParameter]): Boolean = {
      def isNeverSubArg(t1: ScType, t2: ScType, variance: Int) = {
        if (variance > 0) isNeverSubType(t2, t1)
        else if (variance < 0) isNeverSubType(t1, t2)
        else isNeverSameType(t1, t2)
      }
      def getVariance(tp: PsiTypeParameter) = tp match {
        case scParam: ScTypeParam =>
          if (scParam.isCovariant) 1
          else if (scParam.isContravariant) -1
          else 0
        case _ => 0
      }
      tps1.zip(tps2).zip(tparams.map(getVariance)) exists {
        case ((t1, t2), vr) => isNeverSubArg(t1, t2, vr)
        case _ => false
      }
    }

    def neverSubArgs() = {
      (tp1, tp2) match {
        case (ScParameterizedType(_, args1), ScParameterizedType(_, args2)) =>
          isNeverSubArgs(args1, args2, clazz2.getTypeParameters)
        case _ => false
      }
    }


    isNeverSubClass(clazz1, clazz2) ||
            ((areClassesEquivalent(clazz1, clazz2) || (!sameType) && clazz1.isInheritor(clazz2, true)) && neverSubArgs())
  }
}
