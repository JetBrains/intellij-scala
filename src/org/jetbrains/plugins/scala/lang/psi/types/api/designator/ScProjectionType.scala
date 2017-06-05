package org.jetbrains.plugins.scala.lang.psi.types.api.designator

import java.util.Objects

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScClassParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.mutable.ArrayBuffer

/**
 * @author ilyas
 */

/**
 * This type means type projection:
 * SomeType#member
 * member can be class or type alias
 */
class ScProjectionType private(val projected: ScType,
                               val element: PsiNamedElement,
                               val superReference: Boolean /* todo: find a way to remove it*/) extends DesignatorOwner {

  override protected def isAliasTypeInner: Option[AliasType] = {
    actualElement match {
      case ta: ScTypeAlias if ta.typeParameters.isEmpty =>
        val subst: ScSubstitutor = actualSubst
        Some(AliasType(ta, ta.lowerBound.map(subst.subst), ta.upperBound.map(subst.subst)))
      case ta: ScTypeAlias => //higher kind case
        ta match {
          case ta: ScTypeAliasDefinition => //hack for simple cases, it doesn't cover more complicated examples
            ta.aliasedType match {
              case Success(tp, _) =>
                actualSubst.subst(tp) match {
                  case ParameterizedType(des, typeArgs) =>
                    val taArgs = ta.typeParameters
                    if (taArgs.length == typeArgs.length && taArgs.zip(typeArgs).forall {
                      case (tParam: ScTypeParam, TypeParameterType(_, _, _, param)) if tParam == param => true
                      case _ => false
                    }) return Some(AliasType(ta, Success(des, Some(element)), Success(des, Some(element))))
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        val args: ArrayBuffer[ScExistentialArgument] = new ArrayBuffer[ScExistentialArgument]()
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(_.nameAndId),
          ta.typeParameters.map(tp => {
            val name = tp.name + "$$"
            val ex = ScExistentialArgument(name, Nil, Nothing, Any)
            args += ex
            ex
          }))
        val s = actualSubst.followed(genericSubst)
        Some(AliasType(ta, ta.lowerBound.map(scType => ScExistentialType(s.subst(scType), args.toList)),
          ta.upperBound.map(scType => ScExistentialType(s.subst(scType), args.toList))))
      case _ => None
    }
  }

  override def isStable: Boolean = (projected match {
    case designatorOwner: DesignatorOwner => designatorOwner.isStable
    case _ => false
  }) && super.isStable

  override private[types] def designatorSingletonType: Option[ScType] = super.designatorSingletonType.map(actualSubst.subst)

  override def removeAbstracts = ScProjectionType(projected.removeAbstracts, element, superReference)

  override def updateSubtypes(update: (ScType) => (Boolean, ScType), visited: Set[ScType]): ScType = {
    ScProjectionType(projected.recursiveUpdate(update, visited), element, superReference)
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                                    variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        ScProjectionType(projected.recursiveVarianceUpdateModifiable(newData, update, 0), element, superReference)
    }
  }

  @CachedWithRecursionGuard(element, None, ModCount.getBlockModificationCount)
  private def actualImpl(projected: ScType, superReference: Boolean): Option[(PsiNamedElement, ScSubstitutor)] = {
    val resolvePlace = {
      def fromClazz(definition: ScTypeDefinition): PsiElement =
        definition.extendsBlock.templateBody
          .flatMap(_.lastChildStub)
          .getOrElse(definition.extendsBlock)

      projected.extractClass match {
        case Some(definition: ScTypeDefinition) => fromClazz(definition)
        case _ => projected match {
          case ScThisType(definition: ScTypeDefinition) => fromClazz(definition)
          case _ => element
        }
      }
    }

    import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
    def processType(kinds: collection.Set[ResolveTargets.Value] = ValueSet(CLASS),
                    default: Boolean = !superReference): Option[(PsiNamedElement, ScSubstitutor)] = {
      def elementClazz: Option[PsiClass] = element match {
        case named: ScBindingPattern => Option(named.containingClass)
        case member: ScMember => Option(member.containingClass)
        case _ => None
      }
      projected match {
        case ScDesignatorType(clazz: PsiClass)
          if elementClazz.exists(ScEquivalenceUtil.areClassesEquivalent(_, clazz)) =>
          return Some(element, ScSubstitutor(projected))
        case p@ParameterizedType(ScDesignatorType(clazz: PsiClass), args)
          if elementClazz.exists(ScEquivalenceUtil.areClassesEquivalent(_, clazz)) =>
          return Some(element, ScSubstitutor(projected).followed(p.substitutor))
        case p: ScProjectionType =>
          p.actualElement match {
            case clazz: PsiClass
              if elementClazz.exists(ScEquivalenceUtil.areClassesEquivalent(_, clazz)) =>
              return Some(element, ScSubstitutor(projected).followed(p.actualSubst))
            case _ => //continue with processor :(
          }
        case ScThisType(clazz)
          if elementClazz.exists(ScEquivalenceUtil.areClassesEquivalent(_, clazz)) =>
          //for this type we shouldn't put this substitutor because of possible recursions
          //and we don't need that, because all types are already calculated with proper this type
          return Some(element, ScSubstitutor.empty)
        case _ => //continue with processor :(
      }

      val processor = new ResolveProcessor(kinds, resolvePlace, element.name) {
        override protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
          candidatesSet ++= results
          true
        }
      }
      processor.processType(projected, resolvePlace, ResolveState.initial)

      processor.candidates match {
        case Array(candidate) => candidate.element match {
          case candidateElement: PsiNamedElement =>
            val thisSubstitutor = ScSubstitutor(projected)
            val defaultSubstitutor =
              projected match {
                case _: ScThisType => candidate.substitutor
                case _ => thisSubstitutor.followed(candidate.substitutor)
              }
            if (default) {
              Some(candidateElement, defaultSubstitutor)
            } else {
              Some(element,
                ScalaPsiUtil.superTypeMembersAndSubstitutors(candidateElement)
                  .find(_.info == element)
                  .map(node => defaultSubstitutor.followed(node.substitutor))
                  .getOrElse(defaultSubstitutor))
            }
          case _ => None
        }
        case _ => None
      }
    }

    element match {
      case _: ScTypeAlias => processType()
      case d: ScTypedDefinition if d.isStable =>
        // TODO: superMemberSubstitutor? However I don't know working example for this case
        processType(ValueSet(VAL, OBJECT), default = true)
      case _: ScTypeDefinition => processType()
      case _: PsiClass => processType()
      case _ => None
    }
  }

  private def actual: (PsiNamedElement, ScSubstitutor) = {
    actualImpl(projected, superReference).getOrElse(element, ScSubstitutor.empty)
  }

  def actualElement: PsiNamedElement = actual._1
  def actualSubst: ScSubstitutor = actual._2

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    def isSingletonOk(typed: ScTypedDefinition): Boolean = {
      typed.nameContext match {
        case _: ScValue => true
        case p: ScClassParameter if !p.isVar => true
        case _ => false
      }
    }

    actualElement match {
      case a: ScTypedDefinition if isSingletonOk(a) =>
        val subst = actualSubst
        val tp = subst.subst(a.getType(TypingContext.empty).getOrAny)
        tp match {
          case designatorOwner: DesignatorOwner if designatorOwner.isSingleton =>
            val resInner = tp.equiv(r, uSubst, falseUndef)
            if (resInner._1) return resInner
          case _ =>
        }
      case _ =>
    }
    isAliasType match {
      case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
        return (lower match {
          case Success(tp, _) => tp
          case _ => return (false, uSubst)
        }).equiv(r, uSubst, falseUndef)
      case _ =>
    }
    r match {
      case t: StdType =>
        element match {
          case synth: ScSyntheticClass => synth.stdType.equiv(t, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case ParameterizedType(ScProjectionType(_, _, _), _) =>
        r.isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
            this.equiv(lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case proj2@ScProjectionType(p1, _, _) =>
        proj2.actualElement match {
          case a: ScTypedDefinition if isSingletonOk(a) =>
            val subst = actualSubst
            val tp = subst.subst(a.getType(TypingContext.empty).getOrAny)
            tp match {
              case designatorOwner: DesignatorOwner if designatorOwner.isSingleton =>
                val resInner = tp.equiv(this, uSubst, falseUndef)
                if (resInner._1) return resInner
              case _ =>
            }
          case _ =>
        }
        r.isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
            this.equiv(lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }, uSubst, falseUndef)
          case _ =>
        }
        if (actualElement != proj2.actualElement) {
          actualElement match {
            case _: ScObject =>
            case t: ScTypedDefinition if t.isStable =>
              val s: ScSubstitutor = ScSubstitutor(projected) followed actualSubst
              t.getType(TypingContext.empty) match {
                case Success(tp: DesignatorOwner, _) if tp.isSingleton =>
                  return s.subst(tp).equiv(r, uSubst, falseUndef)
                case _ =>
              }
            case _ =>
          }
          proj2.actualElement match {
            case _: ScObject =>
            case t: ScTypedDefinition =>
              val s: ScSubstitutor =
                ScSubstitutor(p1) followed proj2.actualSubst
              t.getType(TypingContext.empty) match {
                case Success(tp: DesignatorOwner, _) if tp.isSingleton =>
                  return s.subst(tp).equiv(this, uSubst, falseUndef)
                case _ =>
              }
            case _ =>
          }
          return (false, uSubst)
        }
        projected.equiv(p1, uSubst, falseUndef)
      case ScThisType(_) =>
        element match {
          case _: ScObject => (false, uSubst)
          case t: ScTypedDefinition if t.isStable =>
            t.getType(TypingContext.empty) match {
              case Success(singleton: DesignatorOwner, _) if singleton.isSingleton =>
                val newSubst = actualSubst.followed(ScSubstitutor(projected))
                r.equiv(newSubst.subst(singleton), uSubst, falseUndef)
              case _ => (false, uSubst)
            }
          case _ => (false, uSubst)
        }
      case _ => (false, uSubst)
    }
  }

  override def isFinalType: Boolean = actualElement match {
    case cl: PsiClass if cl.isEffectivelyFinal => true
    case alias: ScTypeAliasDefinition => alias.aliasedType.exists(_.isFinalType)
    case _ => false
  }

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitProjectionType(this)

  def canEqual(other: Any): Boolean = other.isInstanceOf[ScProjectionType]

  override def equals(other: Any): Boolean = other match {
    case that: ScProjectionType =>
      (that canEqual this) &&
        projected == that.projected &&
        element == that.element &&
        superReference == that.superReference
    case _ => false
  }

  private var hash: Int = -1

  //noinspection HashCodeUsesVar
  override def hashCode: Int = {
    if (hash == -1)
      hash = Objects.hash(projected, element, scala.Boolean.box(superReference))

    hash
  }

  override def typeDepth: Int = projected.typeDepth
}

object ScProjectionType {

  private val guard = RecursionManager.RecursionGuard[ScType, Option[ScType]]("aliasProjectionGuard")

  def simpleAliasProjection(p: ScProjectionType): ScType = {
    if (guard.currentStackContains(p)) return p

    p.actual match {
      case (td: ScTypeAliasDefinition, subst) if td.typeParameters.isEmpty =>
        val upper = guard.doPreventingRecursion(p, td.upperBound.map(subst.subst).toOption)
        upper
          .filter(_.typeDepth < p.typeDepth)
          .getOrElse(p)
      case _ => p
    }
  }

  def apply(projected: ScType, element: PsiNamedElement,
            superReference: Boolean /* todo: find a way to remove it*/): ScType = {

    val simple = new ScProjectionType(projected, element, superReference)
    simple.actualElement match {
      case td: ScTypeAliasDefinition if td.typeParameters.isEmpty =>
        val manager = ScalaPsiManager.instance(element.getProject)
        manager.simpleAliasProjectionCached(simple).getOrElse(simple)
      case _ => simple
    }
  }

  def unapply(proj: ScProjectionType): Option[(ScType, PsiNamedElement, Boolean)] = {
    Some(proj.projected, proj.element, proj.superReference)
  }
}
