package org.jetbrains.plugins.scala
package lang
package psi
package types

import decompiler.DecompilerUtil
import nonvalue.NonValueType
import com.intellij.psi._
import result.TypingContext
import com.intellij.openapi.project.Project
import api.toplevel.typedef.ScObject
import api.statements._
import api.toplevel.ScTypedDefinition


trait ScType {
  final def equiv(t: ScType): Boolean = Equivalence.equiv(this, t)

  /**
   * Checks, whether the following assignment is correct:
   * val x: t = (y: this)
   */
  final def conforms(t: ScType): Boolean = Conformance.conforms(t, this)

  final def weakConforms(t: ScType): Boolean = Conformance.conforms(t, this, true)

  final def presentableText = ScType.presentableText(this)

  final def canonicalText = ScType.canonicalText(this)

  override def toString = presentableText

  def isValue: Boolean

  final def isStable: Boolean = ScType.isStable(this)

  def inferValueType: ValueType

  /**
   * This method is important for parameters expected type.
   * There shouldn't be any abstract type in this expected type.
   */
  def removeAbstracts = this

  @deprecated("use ScSubstitutor.subst")
  def updateThisType(place: PsiElement): ScType = this

  @deprecated("use ScSubstitutor.subst")
  def updateThisType(tp: ScType): ScType = this

  def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (false, uSubst)
  }
}

object ScType extends ScTypePresentation with ScTypePsiTypeBridge {

  def extractClass(t: ScType, project: Option[Project] = None): Option[PsiClass] = extractClassType(t, project).map(_._1)

  def extractClassType(t: ScType, project: Option[Project] = None): Option[Pair[PsiClass, ScSubstitutor]] = t match {
    case n: NonValueType => extractClassType(n.inferValueType)
    case ScDesignatorType(clazz: PsiClass) => Some(clazz, ScSubstitutor.empty)
    case ScDesignatorType(ta: ScTypeAliasDefinition) =>
      extractClassType(ta.aliasedType(TypingContext.empty).getOrElse(return None))
    case proj@ScProjectionType(p, elem, subst) => proj.actualElement match {
      case c: PsiClass => Some((c, proj.actualSubst))
      case t: ScTypeAliasDefinition =>
        extractClassType(proj.actualSubst.subst(t.aliasedType(TypingContext.empty).getOrElse(return None)))
      case _ => None
    }
    case p@ScParameterizedType(t1, _) => {
      extractClassType(t1) match {
        case Some((c, s)) => Some((c, s.followed(p.substitutor)))
        case None => None
      }
    }
    case tuple@ScTupleType(comp) => {
      tuple.resolveTupleTrait match {
        case Some(clazz) => extractClassType(clazz)
        case _ => None
      }
    }
    case fun: ScFunctionType => {
      fun.resolveFunctionTrait match {
        case Some(tp) => extractClassType(tp)
        case _ => None
      }
    }
    case std@StdType(_, _) => Some((std.asClass(project.getOrElse(DecompilerUtil.obtainProject)).getOrElse(return None), ScSubstitutor.empty))
    case _ => None
  }

  def extractDesignated(t: ScType): Option[Pair[PsiNamedElement, ScSubstitutor]] = t match {
    case ScDesignatorType(e) => Some(e, ScSubstitutor.empty)
    case proj@ScProjectionType(p, e, s) => Some((proj.actualElement, proj.actualSubst))
    case p@ScParameterizedType(t1, _) => {
      extractClassType(t1) match {
        case Some((e, s)) => Some((e, s.followed(p.substitutor)))
        case None => None
      }
    }
    case _ => None
  }

  def isSingletonType(tp: ScType): Boolean = tp match {
    case _: ScThisType => true
    case ScDesignatorType(v) =>
      v match {
        case t: ScTypedDefinition => t.isStable
        case _ => false
      }
    case ScProjectionType(_, elem, _) =>
      elem match {
        case t: ScTypedDefinition => t.isStable
        case _ => false
      }
    case _ => false
  }

  // TODO: Review this against SLS 3.2.1
  def isStable(t: ScType): Boolean = t match {
    case ScThisType(_) => true
    case ScProjectionType(projected, element: ScObject, _) => isStable(projected)
    case ScProjectionType(projected, element: ScTypedDefinition, _) => isStable(projected) && element.isStable
    case ScDesignatorType(o: ScObject) => true
    case ScDesignatorType(r: ScTypedDefinition) if r.isStable => true
    case _ => false
  }
}

