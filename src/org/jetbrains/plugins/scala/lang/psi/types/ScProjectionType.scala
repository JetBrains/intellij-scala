package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements.ScTypeAlias
import resolve.{ResolveTargets, StdKinds}
import resolve.processor.{BaseProcessor, ResolveProcessor}
import api.toplevel.typedef.{ScTrait, ScClass, ScMember, ScTemplateDefinition}
import com.intellij.psi.{PsiClass, ResolveState, PsiElement, PsiNamedElement}

/**
 * @author ilyas
 */

/**
 * This type means type projection:
 * SomeType#member
 * member can be class or type alias
 */
case class ScProjectionType(projected: ScType, element: PsiNamedElement, subst: ScSubstitutor) extends ValueType {
  override def removeAbstracts = ScProjectionType(projected.removeAbstracts, element, subst)

  override def updateThisType(place: PsiElement): ScType = {
    ScProjectionType(projected.updateThisType(place), element, subst)
  }

  override def updateThisType(tp: ScType): ScType = {
    ScProjectionType(projected.updateThisType(tp), element, subst)
  }

  lazy val (actualElement, actualSubst) = {
    val emptySubst = new ScSubstitutor(Map.empty, Map.empty, Some(projected))
    element match {
      case a: ScTypeAlias => {
        val name = a.getName
        import ResolveTargets._
        val proc = new ResolveProcessor(ValueSet(CLASS), a, name)
        proc.processType(projected, a, ResolveState.initial, true)
        val candidates = proc.candidates
        if (candidates.length == 1 && candidates(0).element.isInstanceOf[ScTypeAlias]) {
          (candidates(0).element.asInstanceOf[ScTypeAlias], candidates(0).substitutor)
        } else {
          (element, emptySubst followed subst)
        }
      }
      case _ => (element, emptySubst followed subst)
    }
    /*val emptySubst = new ScSubstitutor(Map.empty, Map.empty, Some(projected))
    val a = element
    val name = a.getName
    import ResolveTargets._
    val proc = new ResolveProcessor(ValueSet(CLASS, OBJECT), a, name)
    proc.processType(projected, a, ResolveState.initial, true)
    val candidates = proc.candidates
    if (candidates.length == 1 && candidates(0).element.isInstanceOf[ScTypeAlias]) {
      val res = (element, candidates(0)) match {
        case (_: PsiClass, _: PsiClass) => true
        case (_: ScTypeAlias, _: ScTypeAlias) => true
        case _ => false
      }
      if (res) (candidates(0).element, emptySubst followed candidates(0).substitutor)
      else (element, emptySubst followed subst)
    } else {
      (element, emptySubst followed subst)
    }*/
  }
}

/**
 * This type means type, which depends on place, where you want to get expression type.
 * For example
 *
 * class A       {
 *   def foo: this.type = this
 * }
 *
 * class B extneds A       {
 *   val z = foo // <- type in this place is B.this.type, not A.this.type
 * }
 *
 * So when expression is typed, we should replace all such types be return value.
 */
case class ScThisType(clazz: ScTemplateDefinition) extends ValueType {
  override def updateThisType(place: PsiElement): ScType = {
    /*def workWithClazz(clazz: ScTemplateDefinition): ScType = {
      var td: ScTemplateDefinition = ScalaPsiUtil.getPlaceTd(place)
      while (td != null) {
        if (td == clazz || td.isInheritor(clazz, true)) return ScThisType(td.getType(TypingContext.empty).getOrElse(return tp))
        td = ScalaPsiUtil.getPlaceTd(td)
      }
      tp.updateThisType(place)
    }
    tp match {
      case ScParameterizedType(ScDesignatorType(clazz: ScTemplateDefinition), _) => {
        workWithClazz(clazz)
      }
      case ScDesignatorType(clazz: ScTemplateDefinition) => {
        workWithClazz(clazz)
      }
      case _ => tp.updateThisType(place)
    }*/
    this //todo:
  }

  override def updateThisType(tp: ScType): ScType = {
    ScType.extractClass(tp) match {
      case Some(cl) if cl == clazz => return tp
      case _ =>
    }
    BaseTypes.get(tp).find(tp => {
      ScType.extractClass(tp) match {
        case Some(cl) if cl == clazz => true
        case _ => false
      }
    }) match {
      case Some(_) => tp
      case _ => this
    }
  }
}

/**
 * This type means normal designator type.
 * It can be whether singleton type (v.type) or simple type (java.lang.String).
 * element can be any stable element, class, value or type alias
 */
case class ScDesignatorType(element: PsiNamedElement) extends ValueType {
  private var isStaticClass = false
  //You can use this method to check if it's Java class,
  // which is used for getting static context => no implicit conversion
  def isStatic = isStaticClass
  def this(elem: PsiNamedElement, isStaticClass: Boolean) {
    this (elem)
    this.isStaticClass = isStaticClass
  }
}

// SLS 3.2.3
//
// A type designator refers to a named value type. It can be simple or
// qualified. All such type designators are shorthands for type projections.
//
// Specifically, the unqualified type name `t` where `t` is bound in some
// class, object, or package `C` is taken as a shorthand for
// `C.this.type#t`. If `t` is not bound in a class, object, or package, then `t` is taken as a
// shorthand for `ε.type#t`
//
object ExpandDesignatorToProjection {
  def unapply(t: ScDesignatorType): Option[ScProjectionType] = t.element match {
    case m: ScMember => Some(ScProjectionType(ScDesignatorType(m.getContainingClass), m, ScSubstitutor.empty))
    case _ => None
  }
}