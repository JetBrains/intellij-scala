package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.psi._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements._, params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._, typedef._
import org.jetbrains.plugins.scala.lang.psi.types._, api._, designator._, nonvalue._

import scala.annotation.tailrec

private case class ThisTypeSubstitution(target: ScType, @Nullable seenFromClass: PsiClass) extends LeafSubstitution {

  override def toString: String = seenFromClass match {
    case null => s"`this` -> $target"
    case _    => s"`this` -> $target asSeenFrom $seenFromClass"
  }

  override protected val subst: PartialFunction[LeafType, ScType] = {
    case th: ScThisType if !hasRecursiveThisType(target, th.element) => doUpdateThisTypeFromClass(th, target, seenFromClass)
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

  private def doUpdateThisTypeFromClass(thisTp: ScThisType, target: ScType, @Nullable clazz: PsiClass): ScType =
    if (clazz == null || clazz == thisTp.element || clazz.containingClass == null)
      doUpdateThisType(thisTp, target)
    else {
      BaseTypes.iterator(target).find(_.extractClass.contains(clazz)).flatMap(containingClassType) match {
        case Some(targetContext) => doUpdateThisTypeFromClass(thisTp, targetContext, clazz.containingClass)
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

  private def hasSameOrInheritor(compound: ScCompoundType, thisTp: ScThisType): Boolean = {
    compound.components
      .exists {
        extractAll(_)
          .exists {
            case tp: ScCompoundType => hasSameOrInheritor(tp, thisTp)
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
  private def isMoreNarrow(target: ScType, thisTp: ScThisType, visited: Set[PsiElement]): Boolean = {
    extractAll(target) match {
      case Some(pat: ScBindingPattern) =>
        if (visited.contains(pat)) false
        else isMoreNarrow(pat.`type`().getOrAny, thisTp, visited + pat)
      case Some(param: ScParameter)    => isMoreNarrow(param.`type`().getOrAny, thisTp, visited)
      case Some(typeParam: PsiTypeParameter) =>
        if (visited.contains(typeParam)) false
        else target match {
          case t: TypeParameterType => isMoreNarrow(t.upperType, thisTp, visited + typeParam)
          case p: ParameterizedType =>
            p.designator match {
              case tpt: TypeParameterType =>
                val upperType = ParameterizedType(tpt.upperType, p.typeArguments)
                isMoreNarrow(upperType, thisTp, visited + typeParam)
              case _                      =>
                isMoreNarrow(p.substitutor(TypeParameterType(typeParam)), thisTp, visited + typeParam)
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
      case Some(compound: ScCompoundType)   => hasSameOrInheritor(compound, thisTp)
      case _                                => false
    }
  }

  private def extractAll(tp: ScType) = {
    // Like tp.extractDesignated(expandAliases = true)
    // But also return non-designator returns
    // Such as ScCompoundType, after dealiasing and dereferencing.

    def elem1(tp: DesignatorOwner) = tp match { case tp: ScProjectionType => tp.actualElement case _ => tp.element }
    def subst(tp: DesignatorOwner) = tp match { case tp: ScProjectionType => tp.actualSubst   case _ => ScSubstitutor.empty }

    def rec(tp: ScType, seen: Set[ScTypeAlias]): Either[ScType, PsiNamedElement] = tp match {
      case tp: NonValueType      => rec(tp.inferValueType, seen)
      case tp: DesignatorOwner   => elem1(tp) match {
        case ta: ScTypeAliasDefinition if !seen(ta) => ta.aliasedType.map(subst(tp)).fold(_ => Left(tp), rec(_, seen + ta))
        case tp                                     => Right(tp)
      }
      case tp: ParameterizedType => tp match {
        case AliasType(ta: ScTypeAliasDefinition, _, Right(ub)) if !seen(ta) => rec(ub, seen + ta)
        case _                                                               => rec(tp.designator, seen)
      }
      case tp: StdType           => tp.syntheticClass.toRight(tp)
      case tp: ScExistentialType => rec(tp.quantified, seen)
      case tp: TypeParameterType => Right(tp.psiTypeParameter)
      case tp: ScCompoundType    => Left(tp)
      case _                     => Left(tp)
    }

    rec(tp, Set.empty).fold(Some(_), Some(_))
  }
}
