package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.psi.{PsiClass, PsiTypeParameter}
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isInheritorDeep
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}

import scala.annotation.tailrec

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
private case class ThisTypeSubstitution(target: ScType) extends Substitution {

  override def toString: String = s"`this` -> $target"

  override protected val subst: PartialFunction[ScType, ScType] = {
    case th: ScThisType if !hasRecursiveThisType(target, th.element) => doUpdateThisType(th, target)
  }

  @tailrec
  private def doUpdateThisType(thisTp: ScThisType, target: ScType): ScType = {
    if (isMoreNarrow(target, thisTp)) target
    else {
      containingClassType(target) match {
        case Some(targetContext) => doUpdateThisType(thisTp, targetContext)
        case _ => thisTp
      }
    }
  }

  private def hasRecursiveThisType(tp: ScType, clazz: ScTemplateDefinition): Boolean =
    tp.subtypeExists {
      case ScThisType(`clazz`) => true
      case _ => false
    }

  private def containingClassType(tp: ScType): Option[ScType] = tp match {
    case ScThisType(template) =>
      template.containingClass match {
        case td: ScTemplateDefinition => Some(ScThisType(td))
        case _ => None
      }
    case ScProjectionType(newType, _) => Some(newType)
    case ParameterizedType(ScProjectionType(newType, _), _) => Some(newType)
    case _ => None
  }

  private def isSameOrInheritor(clazz: PsiClass, thisTp: ScThisType): Boolean =
    clazz == thisTp.element || isInheritorDeep(clazz, thisTp.element)

  private def hasSameOrInheritor(compound: ScCompoundType, thisTp: ScThisType) = {
    compound.components
      .exists {
        _.extractClass
          .exists(isSameOrInheritor(_, thisTp))
      }
  }

  @tailrec
  private def isMoreNarrow(target: ScType, thisTp: ScThisType): Boolean =
    target.extractDesignated(expandAliases = true) match {
      case Some(typeParam: PsiTypeParameter) =>
        target match {
          case t: TypeParameterType =>
            isMoreNarrow(t.upperType, thisTp)
          case p: ParameterizedType =>
            p.designator match {
              case tpt: TypeParameterType =>
                isMoreNarrow(p.substitutor.subst(tpt.upperType), thisTp)
              case _ =>
                isSameOrInheritor(typeParam, thisTp)
            }
          case _ => isSameOrInheritor(typeParam, thisTp)
        }
      case Some(t: ScTypeDefinition) =>
        if (isSameOrInheritor(t, thisTp)) true
        else t.selfType match {
          case Some(selfTp) => isMoreNarrow(selfTp, thisTp)
          case _ => false
        }
      case Some(cl: PsiClass) =>
        isSameOrInheritor(cl, thisTp)
      case Some(named: ScTypedDefinition) =>
        isMoreNarrow(named.`type`().getOrAny, thisTp)
      case _ =>
        target match {
          case compound: ScCompoundType =>
            hasSameOrInheritor(compound, thisTp)
          case _ =>
            false
        }
    }
}
