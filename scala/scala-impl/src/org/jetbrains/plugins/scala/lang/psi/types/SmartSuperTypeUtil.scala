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
  sealed trait TraverseSupers
  object TraverseSupers {
    case object Stop           extends TraverseSupers
    case object Skip           extends TraverseSupers
    case object ProcessParents extends TraverseSupers
  }

  private[psi] def smartIsInheritor(
    leftClass:   PsiClass,
    substitutor: ScSubstitutor,
    rightClass:  PsiClass
  ): Option[ScType] =
    if (areClassesEquivalent(leftClass, rightClass))  None
    else if (!isInheritorDeep(leftClass, rightClass)) None
    else {
      var resTpe: ScType = null

      traverseSuperTypes(leftClass, substitutor, (tpe, cls, _) => {
        if (areClassesEquivalent(cls, rightClass)) {
          if ((resTpe eq null) || tpe.conforms(resTpe)) resTpe = tpe
        }

        TraverseSupers.ProcessParents
      }, Set.empty)

      Option(resTpe)
    }

  private[psi] def traverseSuperTypes(
    tpe:     ScType,
    f:       (ScType, PsiClass, ScSubstitutor) => TraverseSupers,
    visited: Set[PsiClass] = Set.empty
  ): Boolean =
    tpe.extractClassType.fold(false) { case (cls, subst) => traverseSuperTypes(cls, subst, f, visited) }

  private[psi] def traverseSuperTypes(
    cls:     PsiClass,
    subst:   ScSubstitutor,
    process: (ScType, PsiClass, ScSubstitutor) => TraverseSupers,
    visited: Set[PsiClass]
  ): Boolean = {
    ProgressManager.checkCanceled()

    val supers: Seq[ScType] = cls match {
      case td: ScTypeDefinition => td.superTypes.map(subst)
      case _ =>
        cls.getSuperTypes.map { tpe =>
          subst(tpe.toScType()(cls)) match {
            case exist: ScExistentialType => exist.quantified
            case other => other
          }
        }.toSeq
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
              process(superTpe, aClass, s) match {
                case TraverseSupers.Stop           => true
                case TraverseSupers.Skip           => false
                case TraverseSupers.ProcessParents =>
                  toBeProcessed += ((aClass, s))
                  false
              }
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
