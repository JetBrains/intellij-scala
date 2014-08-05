package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi._
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions.{toObjectExt, toPsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTemplateBodyImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.immutable.HashSet
import scala.collection.mutable.ArrayBuffer

/**
 * @author ilyas
 */

object ScProjectionType {
  def apply(projected: ScType, element: PsiNamedElement,
            superReference: Boolean /* todo: find a way to remove it*/): ScType = {
    val res = new ScProjectionType(projected, element, superReference)
    projected match {
      case c: ScCompoundType =>
        res.isAliasType match {
          case Some(AliasType(td: ScTypeAliasDefinition, _, upper)) if td.typeParameters.isEmpty => upper.getOrElse(res)
          case _ => res
        }
      case _ => res
    }
  }

  def unapply(proj: ScProjectionType): Option[(ScType, PsiNamedElement, Boolean)] = {
    Some(proj.projected, proj.element, proj.superReference)
  }
}

/**
 * This type means type projection:
 * SomeType#member
 * member can be class or type alias
 */
class ScProjectionType private (val projected: ScType, val element: PsiNamedElement,
                            val superReference: Boolean /* todo: find a way to remove it*/) extends ValueType {
  def copy(superReference: Boolean): ScProjectionType = new ScProjectionType(projected, element, superReference)

  override protected def isAliasTypeInner: Option[AliasType] = {
    if (actualElement.isInstanceOf[ScTypeAlias]) {
      actualElement match {
        case ta: ScTypeAlias if ta.typeParameters.length == 0 =>
          val subst: ScSubstitutor = actualSubst
          Some(AliasType(ta, ta.lowerBound.map(subst.subst), ta.upperBound.map(subst.subst)))
        case ta: ScTypeAlias => //higher kind case
          val args: ArrayBuffer[ScExistentialArgument] = new ArrayBuffer[ScExistentialArgument]()
          val genericSubst = ScalaPsiUtil.
            typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp))),
            ta.typeParameters.map(tp => {
              val name = tp.name + "$$"
              args += new ScExistentialArgument(name, Nil, types.Nothing, types.Any)
              ScTypeVariable(name)
            }))
          val subst: ScSubstitutor = actualSubst
          val s = subst.followed(genericSubst)
          Some(AliasType(ta, ta.lowerBound.map(scType => ScExistentialType(s.subst(scType), args.toList)),
            ta.upperBound.map(scType => ScExistentialType(s.subst(scType), args.toList))))
        case _ => None
      }
    } else None
  }

  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1) {
      hash = projected.hashCode() + element.hashCode() * 31 + (if (superReference) 239 else 0)
    }
    hash
  }

  override def removeAbstracts = ScProjectionType(projected.removeAbstracts, element, superReference)

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    update(this) match {
      case (true, res) => res
      case _ =>
        ScProjectionType(projected.recursiveUpdate(update, visited + this), element, superReference)
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                                    variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        ScProjectionType(projected.recursiveVarianceUpdateModifiable(newData, update, 0), element, superReference)
    }
  }

  private def actual: (PsiNamedElement, ScSubstitutor) = {
    def actualInner(element: PsiNamedElement, projected: ScType): Option[(PsiNamedElement, ScSubstitutor)] = {
      val emptySubst = new ScSubstitutor(Map.empty, Map.empty, Some(projected))
      val resolvePlace = {
        def fromClazz(clazz: ScTypeDefinition): PsiElement = {
          clazz.extendsBlock.templateBody.flatMap(_.asInstanceOf[ScTemplateBodyImpl].getLastChildStub.toOption).
            getOrElse(clazz.extendsBlock)
        }
        ScType.extractClass(projected, Some(element.getProject)) match {
          case Some(clazz: ScTypeDefinition) => fromClazz(clazz)
          case _ => projected match {
            case ScThisType(clazz: ScTypeDefinition) => fromClazz(clazz)
            case _ => element
          }
        }
      }

      def resolveProcessor(kinds: Set[ResolveTargets.Value], name: String): ResolveProcessor = {
        new ResolveProcessor(kinds, resolvePlace, name) {
          override protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
            candidatesSet ++= results
            true
          }
        }
      }

      element match {
        case a: ScTypeAlias =>
          val name = a.name
          import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
          val proc = resolveProcessor(ValueSet(CLASS), name)
          proc.processType(projected, resolvePlace, ResolveState.initial)
          val candidates = proc.candidates
          if (candidates.length == 1 && candidates(0).element.isInstanceOf[PsiNamedElement]) {
            Some(candidates(0).element, emptySubst followed candidates(0).substitutor)
          } else None
        case d: ScTypedDefinition if d.isStable =>
          val name = d.name
          import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

          val proc = resolveProcessor(ValueSet(VAL, OBJECT), name)
          proc.processType(projected, resolvePlace, ResolveState.initial)
          val candidates = proc.candidates
          if (candidates.length == 1 && candidates(0).element.isInstanceOf[PsiNamedElement]) {
            Some(candidates(0).element, emptySubst followed candidates(0).substitutor)
          } else None
        case d: ScTypeDefinition =>
          val name = d.name
          import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
          val proc = resolveProcessor(ValueSet(CLASS), name) //ScObject in ScTypedDefinition case.
          proc.processType(projected, resolvePlace, ResolveState.initial)
          val candidates = proc.candidates
          if (candidates.length == 1 && candidates(0).element.isInstanceOf[PsiNamedElement]) {
            Some(candidates(0).element, emptySubst followed candidates(0).substitutor)
          } else None
        case d: PsiClass =>
          val name = d.getName
          import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
          val proc = resolveProcessor(ValueSet(CLASS), name) //ScObject in ScTypedDefinition case.
          proc.processType(projected, resolvePlace, ResolveState.initial)
          val candidates = proc.candidates
          if (candidates.length == 1 && candidates(0).element.isInstanceOf[PsiNamedElement]) {
            Some(candidates(0).element, emptySubst followed candidates(0).substitutor)
          } else None
        case _ => None
      }
    }

    val (actualElement, actualSubst) =
      CachesUtil.getMappedWithRecursionPreventingWithRollback[PsiNamedElement, ScType, Option[(PsiNamedElement, ScSubstitutor)]](
        element, projected, CachesUtil.PROJECTION_TYPE_ACTUAL_INNER, actualInner, None,
        PsiModificationTracker.MODIFICATION_COUNT).getOrElse(
          (element, ScSubstitutor.empty)
        )

    if (superReference) (element, actualSubst)
    else (actualElement, actualSubst)
  }

  def actualElement: PsiNamedElement = actual._1
  def actualSubst: ScSubstitutor = actual._2

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor,
                          falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    def isSingletonOk(typed: ScTypedDefinition): Boolean = {
      typed.nameContext match {
        case v: ScValue => true
        case p: ScClassParameter if !p.isVar => true
        case _ => false
      }
    }

    actualElement match {
      case a: ScTypedDefinition if isSingletonOk(a) =>
        val subst = actualSubst
        val tp = subst.subst(a.getType(TypingContext.empty).getOrAny)
        if (ScType.isSingletonType(tp)) {
          val resInner = Equivalence.equivInner(tp, r, uSubst, falseUndef)
          if (resInner._1) return resInner
        }
      case _ =>
    }
    isAliasType match {
      case Some(AliasType(ta: ScTypeAliasDefinition, lower, _)) =>
        return Equivalence.equivInner(lower match {
          case Success(tp, _) => tp
          case _ => return (false, uSubst)
        }, r, uSubst, falseUndef)
      case _ =>
    }
    r match {
      case t: StdType =>
        element match {
          case synth: ScSyntheticClass => Equivalence.equivInner(synth.t, t, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case param@ScParameterizedType(proj2@ScProjectionType(p1, element1, _), typeArgs) =>
        r.isAliasType match {
          case Some(AliasType(ta: ScTypeAliasDefinition, lower, _)) =>
            Equivalence.equivInner(this, lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case proj2@ScProjectionType(p1, element1, _) =>
        proj2.actualElement match {
          case a: ScTypedDefinition if isSingletonOk(a) =>
            val subst = actualSubst
            val tp = subst.subst(a.getType(TypingContext.empty).getOrAny)
            if (ScType.isSingletonType(tp)) {
              val resInner = Equivalence.equivInner(tp, this, uSubst, falseUndef)
              if (resInner._1) return resInner
            }
          case _ =>
        }
        r.isAliasType match {
          case Some(AliasType(ta: ScTypeAliasDefinition, lower, _)) =>
            Equivalence.equivInner(this, lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }, uSubst, falseUndef)
          case _ =>
        }
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
      case ScThisType(clazz) =>
        element match {
          case o: ScObject => (false, uSubst)
          case t: ScTypedDefinition if t.isStable =>
            t.getType(TypingContext.empty) match {
              case Success(singl, _) if ScType.isSingletonType(singl) =>
                val newSubst = actualSubst.followed(new ScSubstitutor(Map.empty, Map.empty, Some(projected)))
                Equivalence.equivInner(r, newSubst.subst(singl), uSubst, falseUndef)
              case _ => (false, uSubst)
            }
          case _ => (false, uSubst)
        }
      case _ => (false, uSubst)
    }
  }

  override def isFinalType = actualElement match {
    case cl: PsiClass if cl.isEffectivelyFinal => true
    case _ => false
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitProjectionType(this)
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[ScProjectionType]

  override def equals(other: Any): Boolean = other match {
    case that: ScProjectionType =>
      (that canEqual this) &&
        projected == that.projected &&
        element == that.element &&
        superReference == that.superReference
    case _ => false
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
  clazz.getClass //throw NPE if clazz is null...

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
      case (_, p@ScProjectionType(tp, elem: ScTypedDefinition, _)) if elem.isStable =>
        elem.getType(TypingContext.empty) match {
          case Success(singl, _) if ScType.isSingletonType(singl) =>
            val newSubst = p.actualSubst.followed(new ScSubstitutor(Map.empty, Map.empty, Some(tp)))
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
  override protected def isAliasTypeInner: Option[AliasType] = {
    element match {
      case ta: ScTypeAlias if ta.typeParameters.length == 0 =>
        Some(AliasType(ta, ta.lowerBound, ta.upperBound))
      case ta: ScTypeAlias => //higher kind case
        val args: ArrayBuffer[ScExistentialArgument] = new ArrayBuffer[ScExistentialArgument]()
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp))),
          ta.typeParameters.map(tp => {
            val name = tp.name + "$$"
            args += new ScExistentialArgument(name, Nil, types.Nothing, types.Any)
            ScTypeVariable(name)
          }))
        Some(AliasType(ta, ta.lowerBound.map(scType => ScExistentialType(genericSubst.subst(scType), args.toList)),
          ta.upperBound.map(scType => ScExistentialType(genericSubst.subst(scType), args.toList))))
      case _ => None
    }
  }

  override def getValType: Option[StdType] = {
    element match {
      case o: ScObject => None
      case clazz: PsiClass =>
        ScType.baseTypesQualMap.get(clazz.qualifiedName)
      case _ => None
    }
  }

  private var isStaticClass = false
  /**
   * You can use this method to check if it's Java class,
   * which is used for getting static context => no implicit conversion
   */
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

  override def isFinalType = element match {
    case cl: PsiClass if cl.isEffectivelyFinal => true
    case _ => false
  }
}
