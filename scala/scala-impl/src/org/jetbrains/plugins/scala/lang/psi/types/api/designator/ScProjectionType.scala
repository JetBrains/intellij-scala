package org.jetbrains.plugins.scala.lang.psi.types.api.designator

import java.util.Objects

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.RecursionManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScTypeParam, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, _}
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

/**
 * @author ilyas
 */

/**
 * This type means type projection:
 * SomeType#member
 * member can be class or type alias
 */
final class ScProjectionType private(val projected: ScType,
                                     val element: PsiNamedElement) extends DesignatorOwner {

  override protected def isAliasTypeInner: Option[AliasType] = {
    actualElement match {
      case ta: ScTypeAlias if ta.typeParameters.isEmpty =>
        val subst: ScSubstitutor = actualSubst
        Some(AliasType(ta, ta.lowerBound.map(subst), ta.upperBound.map(subst)))
      case ta: ScTypeAlias => //higher kind case
        ta match {
          case ta: ScTypeAliasDefinition => //hack for simple cases, it doesn't cover more complicated examples
            ta.aliasedType match {
              case Right(tp) =>
                actualSubst(tp) match {
                  case target @ ParameterizedType(des, typeArgs) =>
                    val tParams = ta.typeParameters
                    val sameParams = tParams.length == typeArgs.length && tParams.zip(typeArgs).forall {
                      case (tParam: ScTypeParam, TypeParameterType.ofPsi(param)) if tParam.typeParamId == param.typeParamId => true
                      case _                                                                                                => false
                    }

                    if (sameParams) return Some(AliasType(ta, Right(des), Right(des)))
                    else {
                      val typeConsuctor = ScTypePolymorphicType(target, tParams.map(TypeParameter.apply))
                      return Option(AliasType(ta, Right(typeConsuctor), Right(typeConsuctor)))
                    }
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        val existentialArgs = ta.typeParameters
          .map(tp => ScExistentialArgument(tp.name + "$$", Nil, Nothing, Any))
          .toList

        val genericSubst = ScSubstitutor.bind(ta.typeParameters, existentialArgs)

        val s = actualSubst.followed(genericSubst)
        Some(AliasType(ta,
          ta.lowerBound.map(scType => ScExistentialType(s(scType))),
          ta.upperBound.map(scType => ScExistentialType(s(scType)))))
      case _ => None
    }
  }

  override def isStable: Boolean = (projected match {
    case designatorOwner: DesignatorOwner => designatorOwner.isStable
    case _ => false
  }) && super.isStable

  override private[types] def designatorSingletonType: Option[ScType] = super.designatorSingletonType.map(actualSubst)

  @CachedWithRecursionGuard(element, None, ModCount.getBlockModificationCount)
  private def actualImpl(projected: ScType, updateWithProjectionSubst: Boolean): Option[(PsiNamedElement, ScSubstitutor)] = {
    val resolvePlace = {
      def fromClazz(definition: ScTypeDefinition): PsiElement =
        definition.extendsBlock.templateBody
          .flatMap(_.lastChildStub)
          .getOrElse(definition.extendsBlock)

      projected.tryExtractDesignatorSingleton.extractClass match {
        case Some(definition: ScTypeDefinition) => fromClazz(definition)
        case _ => projected match {
          case ScThisType(definition: ScTypeDefinition) => fromClazz(definition)
          case _ => element
        }
      }
    }

    import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
    def processType(kinds: collection.Set[ResolveTargets.Value] = ValueSet(CLASS)): Option[(PsiNamedElement, ScSubstitutor)] = {
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
      processor.processType(projected, resolvePlace, ResolveState.initial, updateWithProjectionSubst)

      processor.candidates match {
        case Array(candidate) => candidate.element match {
          case candidateElement: PsiNamedElement =>
            val thisSubstitutor = ScSubstitutor(projected)
            val defaultSubstitutor =
              projected match {
                case _: ScThisType => candidate.substitutor
                case _ => thisSubstitutor.followed(candidate.substitutor)
              }
            val needSuperSubstitutor = element match {
              case _: PsiClass => element != candidateElement
              case _ => false
            }
            if (needSuperSubstitutor) {
              Some(element,
                ScalaPsiUtil.superTypeSignatures(candidateElement)
                  .find(_.namedElement == element)
                  .map(typeSig => typeSig.substitutor.followed(defaultSubstitutor))
                  .getOrElse(defaultSubstitutor))

            } else {
              Some(candidateElement, defaultSubstitutor)
            }
          case _ => None
        }
        case _ => None
      }
    }

    element match {
      case d: ScTypedDefinition if d.isStable => //val's, objects, parameters
        processType(ValueSet(VAL, OBJECT))
      case _: ScTypeAlias | _: PsiClass =>
        processType(ValueSet(CLASS))
      case _ => None
    }
  }

  private def actual(updateWithProjectionSubst: Boolean = true): (PsiNamedElement, ScSubstitutor) =
    actualImpl(projected, updateWithProjectionSubst).getOrElse(element, ScSubstitutor.empty)

  def actualElement: PsiNamedElement = actual()._1
  def actualSubst: ScSubstitutor = actual()._2

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
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
        val tp = subst(a.`type`().getOrAny)
        tp match {
          case designatorOwner: DesignatorOwner if designatorOwner.isSingleton =>
            val resInner = tp.equiv(r, constraints, falseUndef)
            if (resInner.isRight) return resInner
          case lit: ScLiteralType =>
            val resInner = tp.equiv(r, constraints, falseUndef)
            if (resInner.isRight) return resInner
          case _ =>
        }
      case _ =>
    }
    isAliasType match {
      case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
        return (lower match {
          case Right(tp) => tp
          case _ => return ConstraintsResult.Left
        }).equiv(r, constraints, falseUndef)
      case _ =>
    }
    r match {
      case t: StdType =>
        element match {
          case synth: ScSyntheticClass => synth.stdType.equiv(t, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
      case ParameterizedType(ScProjectionType(_, _), _) =>
        r.isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
            this.equiv(lower match {
              case Right(tp) => tp
              case _ => return ConstraintsResult.Left
            }, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
      case proj2@ScProjectionType(p1, _) =>
        proj2.actualElement match {
          case a: ScTypedDefinition if isSingletonOk(a) =>
            val subst = actualSubst
            val tp = subst(a.`type`().getOrAny)
            tp match {
              case designatorOwner: DesignatorOwner if designatorOwner.isSingleton =>
                val resInner = tp.equiv(this, constraints, falseUndef)
                if (resInner.isRight) return resInner
              case _ =>
            }
          case _ =>
        }
        r.isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) =>
            this.equiv(lower match {
              case Right(tp) => tp
              case _ => return ConstraintsResult.Left
            }, constraints, falseUndef)
          case _ =>
        }
        if (actualElement != proj2.actualElement) {
          actualElement match {
            case _: ScObject =>
            case t: ScTypedDefinition if t.isStable =>
              val s: ScSubstitutor = ScSubstitutor(projected) followed actualSubst
              t.`type`() match {
                case Right(tp: DesignatorOwner) if tp.isSingleton =>
                  return s(tp).equiv(r, constraints, falseUndef)
                case _ =>
              }
            case _ =>
          }
          proj2.actualElement match {
            case _: ScObject =>
            case t: ScTypedDefinition =>
              val s: ScSubstitutor =
                ScSubstitutor(p1) followed proj2.actualSubst
              t.`type`() match {
                case Right(tp: DesignatorOwner) if tp.isSingleton =>
                  return s(tp).equiv(this, constraints, falseUndef)
                case _ =>
              }
            case _ =>
          }
          return ConstraintsResult.Left
        }
        projected.equiv(p1, constraints, falseUndef)
      case ScThisType(_) =>
        element match {
          case _: ScObject => ConstraintsResult.Left
          case t: ScTypedDefinition if t.isStable =>
            t.`type`() match {
              case Right(singleton: DesignatorOwner) if singleton.isSingleton =>
                val newSubst = actualSubst.followed(ScSubstitutor(projected))
                r.equiv(newSubst(singleton), constraints, falseUndef)
              case _ => ConstraintsResult.Left
            }
          case _ => ConstraintsResult.Left
        }
      case _ => ConstraintsResult.Left
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
        element == that.element
    case _ => false
  }

  private var hash: Int = -1

  //noinspection HashCodeUsesVar
  override def hashCode: Int = {
    if (hash == -1)
      hash = Objects.hash(projected, element)

    hash
  }

  override def typeDepth: Int = projected.typeDepth
}

object ScProjectionType {

  private val guard = RecursionManager.RecursionGuard[ScType, Option[ScType]]("aliasProjectionGuard")

  def simpleAliasProjection(p: ScProjectionType): ScType = {
    if (guard.checkReentrancy(p)) return p

    p.actual() match {
      case (td: ScTypeAliasDefinition, subst) if td.typeParameters.isEmpty =>
        val upper = guard.doPreventingRecursion(p, td.upperBound.map(subst).toOption)
        upper
          .filter(_.typeDepth < p.typeDepth)
          .getOrElse(p)
      case _ => p
    }
  }

  def apply(projected: ScType, element: PsiNamedElement): ScType = {

    val simple = new ScProjectionType(projected, element)
    simple.actualElement match {
      case td: ScTypeAliasDefinition if td.typeParameters.isEmpty =>
        val manager = ScalaPsiManager.instance(element.getProject)
        manager.simpleAliasProjectionCached(simple).nullSafe.getOrElse(simple)
      case _ => simple
    }
  }

  def unapply(proj: ScProjectionType): Option[(ScType, PsiNamedElement)] = {
    Some(proj.projected, proj.element)
  }

  object withActual {
    private[this] val extractor = new withActual(true)

    def unapply(proj: ScProjectionType): Option[(PsiNamedElement, ScSubstitutor)] = extractor.unapply(proj)
  }

  class withActual(updateWithProjectionSubst: Boolean) {
    def unapply(proj: ScProjectionType): Option[(PsiNamedElement, ScSubstitutor)] =
      Option(proj.actual(updateWithProjectionSubst))
  }
}
