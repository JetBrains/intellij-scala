package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.psi.{PsiClass, PsiTypeParameter}
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isInheritorDeep
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.{LeafType, ScCompoundType, ScType}

import scala.annotation.tailrec

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
private case class ThisTypeSubstitution(target: ScType) extends LeafSubstitution {

  override def toString: String = s"`this` -> $target"

  override protected val subst: PartialFunction[LeafType, ScType] = {
    case th: ScThisType if !hasRecursiveThisType(target, th.element) => doUpdateThisType(th, target)
  }

  @tailrec
  private def doUpdateThisType(thisTp: ScThisType, target: ScType): ScType =
    if (isMoreNarrow(target, thisTp, Set.empty)) target
    else {
      containingClassType(target) match {
        case Some(targetContext) => doUpdateThisType(thisTp, targetContext)
        case _                   => thisTp
      }
    }

  private def hasRecursiveThisType(tp: ScType, clazz: ScTemplateDefinition): Boolean =
    tp.subtypeExists {
      case tpe: ScThisType => isSameOrInheritor(clazz, tpe)
      case _               => false
    }

  private def containingClassType(tp: ScType): Option[ScType] = tp match {
    case ScThisType(template) =>
      template.containingClass match {
        case td: ScTemplateDefinition => Some(ScThisType(td))
        case _                        => None
      }
    case ScProjectionType(newType, _)                       => Some(newType)
    case ParameterizedType(ScProjectionType(newType, _), _) => Some(newType)
    case _                                                  => None
  }

  private def isSameOrInheritor(clazz: PsiClass, thisTp: ScThisType): Boolean =
    clazz == thisTp.element || isInheritorDeep(clazz, thisTp.element)

  private def hasSameOrInheritor(compound: ScCompoundType, thisTp: ScThisType) = {
    compound.components
      .exists {
        _.extractDesignated(expandAliases = true)
          .exists {
            case tp: ScTypeParam =>
              (for {
                upper <- tp.upperBound.toOption
                cls   <- upper.extractClass
              } yield isSameOrInheritor(cls, thisTp)).getOrElse(false)
            case cls: PsiClass => isSameOrInheritor(cls, thisTp)
            case _             => false
          }
      }
  }

  @tailrec
  private def isMoreNarrow(target: ScType, thisTp: ScThisType, visited: Set[PsiClass]): Boolean = {
    target.extractDesignated(expandAliases = true) match {
      case Some(pat: ScBindingPattern) => isMoreNarrow(pat.`type`().getOrAny, thisTp, visited)
      case Some(param: ScParameter)    => isMoreNarrow(param.`type`().getOrAny, thisTp, visited)
      case Some(typeParam: PsiTypeParameter) =>
        if (visited.contains(typeParam)) false
        else target match {
          case t: TypeParameterType =>
            isMoreNarrow(t.upperType, thisTp, visited + typeParam)
          case p: ParameterizedType =>
            val pSubst = p.substitutor
            p.designator match {
              case tpt: TypeParameterType => isMoreNarrow(pSubst(tpt.upperType), thisTp, visited + typeParam)
              case _                      => isMoreNarrow(pSubst(TypeParameterType(typeParam)), thisTp, visited + typeParam)
            }
          case _ => isSameOrInheritor(typeParam, thisTp)
        }
      case Some(t: ScTypeDefinition) =>
        if (visited.contains(t)) false
        else if (isSameOrInheritor(t, thisTp)) true
        else
          t.selfType match {
            case Some(selfTp) => isMoreNarrow(selfTp, thisTp, visited + t)
            case _            => false
          }
      case Some(td: ScTypeAliasDeclaration) => isMoreNarrow(td.upperBound.getOrAny, thisTp, visited)
      case Some(cl: PsiClass)               => isSameOrInheritor(cl, thisTp)
      case Some(named: ScTypedDefinition)   => isMoreNarrow(named.`type`().getOrAny, thisTp, visited)
      case _ =>
        target match {
          case compound: ScCompoundType => hasSameOrInheritor(compound, thisTp)
          case _                        => false
        }
    }
  }
}
