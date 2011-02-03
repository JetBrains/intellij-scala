package org.jetbrains.plugins.scala
package lang
package psi
package types

import resolve.ResolveTargets
import resolve.processor.ResolveProcessor
import com.intellij.psi.{ResolveState, PsiElement, PsiNamedElement}
import impl.toplevel.synthetic.ScSyntheticClass
import result.Success
import api.statements.{ScTypeAliasDefinition, ScTypeAlias}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import api.toplevel.typedef._

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

  private def actual: (PsiNamedElement, ScSubstitutor) = {
    var res = actualInnerTuple
    if (res != null) return res
    res = actualInner
    actualInnerTuple = res
    return res
  }

  def actualElement: PsiNamedElement = actual._1
  def actualSubst: ScSubstitutor = actual._2

  @volatile
  private var actualInnerTuple: (PsiNamedElement, ScSubstitutor) = null

  private def actualInner: (PsiNamedElement, ScSubstitutor) = {
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
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    r match {
      case t: StdType => {
        element match {
          case synth: ScSyntheticClass => Equivalence.equivInner(synth.t, t, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      }
      case _ if actualElement.isInstanceOf[ScTypeAliasDefinition] => {
        val a = actualElement.asInstanceOf[ScTypeAliasDefinition]
        val subst = actualSubst
        Equivalence.equivInner(subst.subst(a.aliasedType match {
          case Success(tp, _) => tp
          case _ => return (false, uSubst)
        }), r, uSubst, falseUndef)
      }
      case proj2@ScProjectionType(p1, element1, subst1) => {
        if (actualElement != proj2.actualElement) return (false, uSubst)
        Equivalence.equivInner(projected, p1, uSubst, falseUndef)
      }
      case _ => (false, uSubst)
    }
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
    this
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

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (this, r) match {
      case (ScThisType(clazz1), ScThisType(clazz2)) =>
        return (ScEquivalenceUtil.areClassesEquivalent(clazz1, clazz2), uSubst)
      case (ScThisType(obj1: ScObject), ScDesignatorType(obj2: ScObject)) =>
        return (ScEquivalenceUtil.areClassesEquivalent(obj1, obj2), uSubst)
      case _ => (false, uSubst)
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

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (this, r) match {
      case (ScDesignatorType(a: ScTypeAliasDefinition), _) =>
        Equivalence.equivInner(a.aliasedType match {
          case Success(tp, _) => tp
          case _ => return (false, uSubst)
        }, r, uSubst, falseUndef)
      case (ScDesignatorType(element), ScDesignatorType(element1)) =>
        (ScEquivalenceUtil.smartEquivalence(element, element1), uSubst)
      case _ => (false, uSubst)
    }
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