package org.jetbrains.plugins.scala
package lang
package psi
package types

import impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil
import api.toplevel.typedef._
import api.statements.{ScTypeAliasDefinition, ScTypeAlias}
import result.{TypingContext, Success}
import api.toplevel.ScTypedDefinition
import resolve.processor.ResolveProcessor
import resolve.ResolveTargets
import com.intellij.psi.{PsiClass, ResolveState, PsiNamedElement}

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

  override def recursiveUpdate(update: ScType => (Boolean, ScType)): ScType = {
    update(this) match {
      case (true, res) => res
      case _ =>
        ScProjectionType(projected.recursiveUpdate(update), element, subst)
    }
  }

  override def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int): ScType = {
    update(this, variance) match {
      case (true, res) => res
      case _ =>
        ScProjectionType(projected.recursiveVarianceUpdate(update, 0), element, subst)
    }
  }

  private def actual: (PsiNamedElement, ScSubstitutor) = {
    var res = actualInnerTuple
    if (res != null) return res
    res = actualInner
    actualInnerTuple = res
    res
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
        if (candidates.length == 1 && candidates(0).element.isInstanceOf[PsiNamedElement]) {
          (candidates(0).element.asInstanceOf[PsiNamedElement], emptySubst followed candidates(0).substitutor)
        } else {
          (element, emptySubst followed subst)
        }
      }
      case d: ScTypedDefinition if d.isStable => {
        val name = d.getName
        import ResolveTargets._
        val proc = new ResolveProcessor(ValueSet(VAL, OBJECT), d, name)
        proc.processType(projected, d, ResolveState.initial, true)
        val candidates = proc.candidates
        if (candidates.length == 1 && candidates(0).element.isInstanceOf[PsiNamedElement]) {
          (candidates(0).element.asInstanceOf[PsiNamedElement], emptySubst followed candidates(0).substitutor)
        } else {
          (element, emptySubst followed subst)
        }
      }
      case d: ScTypeDefinition => {
        val name = d.getName
        import ResolveTargets._
        val proc = new ResolveProcessor(ValueSet(CLASS), d, name) //ScObject in ScTypedDefinition case.
        proc.processType(projected, d, ResolveState.initial, true)
        val candidates = proc.candidates
        if (candidates.length == 1 && candidates(0).element.isInstanceOf[PsiNamedElement]) {
          (candidates(0).element.asInstanceOf[PsiNamedElement], emptySubst followed candidates(0).substitutor)
        } else {
          (element, emptySubst followed subst)
        }
      }
      case _ => (element, emptySubst followed subst)
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor,
                          falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    r match {
      case _ if actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        val a = actualElement.asInstanceOf[ScTypeAliasDefinition]
        val subst = actualSubst
        Equivalence.equivInner(subst.subst(a.aliasedType match {
          case Success(tp, _) => tp
          case _ => return (false, uSubst)
        }), r, uSubst, falseUndef)
      case t: StdType =>
        element match {
          case synth: ScSyntheticClass => Equivalence.equivInner(synth.t, t, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case proj2@ScProjectionType(p1, element1, subst1) => {
        if (actualElement != proj2.actualElement) {
          actualElement match {
            case o: ScObject =>
            case t: ScTypedDefinition if t.isStable =>
              val s: ScSubstitutor = new ScSubstitutor(Map.empty, Map.empty, Some(projected)) followed actualSubst
              t.getType(TypingContext.empty) match {
                case Success(tp, _) if ScType.isSingletonType(tp) =>
                  return Equivalence.equivInner(s.subst(tp), r, uSubst, falseUndef)
                case _ =>
              }
            case _ =>
          }
          proj2.actualElement match {
            case o: ScObject =>
            case t: ScTypedDefinition =>
              val s: ScSubstitutor =
                new ScSubstitutor(Map.empty, Map.empty, Some(p1)) followed proj2.actualSubst
              t.getType(TypingContext.empty) match {
                case Success(tp, _) if ScType.isSingletonType(tp) =>
                  return Equivalence.equivInner(s.subst(tp), this, uSubst, falseUndef)
                case _ =>
              }
            case _ =>
          }
          return (false, uSubst)
        }
        Equivalence.equivInner(projected, p1, uSubst, falseUndef)
      }
      case ScThisType(clazz) =>
        element match {
          case o: ScObject => (false, uSubst)
          case t: ScTypedDefinition if t.isStable =>
            t.getType(TypingContext.empty) match {
              case Success(singl, _) if ScType.isSingletonType(singl) =>
                val newSubst = subst.followed(new ScSubstitutor(Map.empty, Map.empty, Some(projected)))
                Equivalence.equivInner(r, newSubst.subst(singl), uSubst, falseUndef)
              case _ => (false, uSubst)
            }
          case _ => (false, uSubst)
        }
      case _ => (false, uSubst)
    }
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitProjectionType(this)
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

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor,
                          falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (this, r) match {
      case (ScThisType(clazz1), ScThisType(clazz2)) =>
        (ScEquivalenceUtil.areClassesEquivalent(clazz1, clazz2), uSubst)
      case (ScThisType(obj1: ScObject), ScDesignatorType(obj2: ScObject)) =>
        (ScEquivalenceUtil.areClassesEquivalent(obj1, obj2), uSubst)
      case (_, ScDesignatorType(obj: ScObject)) =>
        (false, uSubst)
      case (_, ScDesignatorType(typed: ScTypedDefinition)) if typed.isStable =>
        typed.getType(TypingContext.empty) match {
          case Success(tp, _) if ScType.isSingletonType(tp) =>
            Equivalence.equivInner(this, tp, uSubst, falseUndef)
          case _ =>
            (false, uSubst)
        }
      case (_, ScProjectionType(_, o: ScObject, _)) => (false, uSubst)
      case (_, p@ScProjectionType(tp, elem: ScTypedDefinition, subst)) if elem.isStable =>
        elem.getType(TypingContext.empty) match {
          case Success(singl, _) if ScType.isSingletonType(singl) =>
            val newSubst = subst.followed(new ScSubstitutor(Map.empty, Map.empty, Some(tp)))
            Equivalence.equivInner(this, newSubst.subst(singl), uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case _ => (false, uSubst)
    }
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitThisType(this)
  }
}

/**
 * This type means normal designator type.
 * It can be whether singleton type (v.type) or simple type (java.lang.String).
 * element can be any stable element, class, value or type alias
 */
case class ScDesignatorType(element: PsiNamedElement) extends ValueType {
  override def getValType: Option[StdType] = {
    element match {
      case clazz: PsiClass =>
        ScType.baseTypesQualMap.get(clazz.getQualifiedName)
      case _ => None
    }
  }

  private var isStaticClass = false
  //You can use this method to check if it's Java class,
  // which is used for getting static context => no implicit conversion
  def isStatic = isStaticClass
  def this(elem: PsiNamedElement, isStaticClass: Boolean) {
    this (elem)
    this.isStaticClass = isStaticClass
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor,
                          falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (this, r) match {
      case (ScDesignatorType(a: ScTypeAliasDefinition), _) =>
        Equivalence.equivInner(a.aliasedType match {
          case Success(tp, _) => tp
          case _ => return (false, uSubst)
        }, r, uSubst, falseUndef)
      case (_, ScDesignatorType(element1)) =>
        if (ScEquivalenceUtil.smartEquivalence(element, element1)) return (true, uSubst)
        if (ScType.isSingletonType(this) && ScType.isSingletonType(r)) {
          element match {
            case o: ScObject =>
            case bind: ScTypedDefinition if bind.isStable =>
              bind.getType(TypingContext.empty) match {
                case Success(tp, _) if ScType.isSingletonType(tp) =>
                  return Equivalence.equivInner(tp, r, uSubst, falseUndef)
                case _ =>
              }
            case _ =>
          }
          element1 match {
            case o: ScObject =>
            case bind: ScTypedDefinition if bind.isStable =>
              bind.getType(TypingContext.empty) match {
                case Success(tp, _) if ScType.isSingletonType(tp) =>
                  return Equivalence.equivInner(tp, this, uSubst, falseUndef)
                case _ =>
              }
          }
        }
        (false, uSubst)
      case _ => (false, uSubst)
    }
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitDesignatorType(this)
  }
}
