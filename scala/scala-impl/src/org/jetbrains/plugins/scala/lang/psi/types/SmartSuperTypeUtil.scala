package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

import scala.collection.mutable.ArrayBuffer

/**
  * A collection of utility methods for finding a super-type,
  * which satisfies some predicate (e.g. has some fixed kind,
  * is equivalent to some class, has FunctionN type etc).
  */
object SmartSuperTypeUtil {
  private[psi] def smartIsInheritor(
    leftClass:   PsiClass,
    substitutor: ScSubstitutor,
    rightClass:  PsiClass
  ): Option[ScType] =
    if (areClassesEquivalent(leftClass, rightClass))  None
    else if (!isInheritorDeep(leftClass, rightClass)) None
    else {
      var resTpe: ScType = null

      traverseSuperTypes(leftClass, substitutor, (cls, tpe) => {
        if (areClassesEquivalent(cls, rightClass)) {
          if ((resTpe eq null) || tpe.conforms(resTpe)) resTpe = tpe
        }
        false
      }, Set.empty)

      Option(resTpe)
    }

  private[psi] def traverseSuperTypes(
    tpe:     ScType,
    f:       (PsiClass, ScType) => Boolean,
    visited: Set[PsiClass] = Set.empty
  ): Boolean =
    tpe.extractClassType.fold(false) { case (cls, subst) => traverseSuperTypes(cls, subst, f, visited) }

  private[psi] def traverseSuperTypes(
    cls:     PsiClass,
    subst:   ScSubstitutor,
    process: (PsiClass, ScType) => Boolean,
    visited: Set[PsiClass]
  ): Boolean = {
    ProgressManager.checkCanceled()

    val supers: Seq[ScType] = cls match {
      case td: ScTypeDefinition => td.superTypes.map(subst)
      case _ =>
        cls.getSuperTypes.map(
          tpe =>
            subst(tpe.toScType()(cls)) match {
              case exist: ScExistentialType => exist.quantified
              case other                    => other
        }
      )
    }

    val toBeProcessed = ArrayBuffer.empty[(PsiClass, ScSubstitutor)]

    @scala.annotation.tailrec
    def drainIteratorRecursively[B](iterator: Iterator[B])(f: B => Boolean): Boolean =
      if (!iterator.hasNext) false
      else {
        val next = iterator.next()
        if (f(next)) true
        else         drainIteratorRecursively(iterator)(f)
      }

    val inImmediateSupers =
      drainIteratorRecursively(supers.iterator)(
        superTpe =>
          superTpe.extractClassType match {
            case Some((aClass, s)) if !visited.contains(aClass) =>
              toBeProcessed += ((aClass, s))
              process(aClass, superTpe)
            case _ => false
          }
      )

    if (inImmediateSupers) true
    else {
      drainIteratorRecursively(toBeProcessed.iterator) {
        case (aClass, s) => traverseSuperTypes(aClass, s, process, visited + cls)
      }
    }
  }
}
