package org.jetbrains.plugins.scala.lang.psi.types.api
package designator

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, RecursionManager, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumSingletonCase, ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ConstraintSystem, ConstraintsResult, ScCompoundType, ScExistentialArgument, ScExistentialType, ScLiteralType, ScType, ScalaTypeVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult, ScalaResolveState}
import org.jetbrains.plugins.scala.util.HashBuilder._
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

/**
 * This type means type projection:
 * SomeType#member
 * member can be class or type alias
 */
final class ScProjectionType private(val projected: ScType,
                                     override val element: PsiNamedElement) extends DesignatorOwner {

  override protected def calculateAliasType: Option[AliasType] = {
    actualElement match {
      case ta: ScTypeAlias if ta.typeParameters.isEmpty =>
        val subst: ScSubstitutor = actualSubst
        Some(AliasType(ta, ta.lowerBound.map(subst), ta.upperBound.map(subst)))
      case ta: ScTypeAlias => //higher kind case
        ta match {
          case ta: ScTypeAliasDefinition => //hack for simple cases, it doesn't cover more complicated examples
            ta.aliasedType match {
              case Right(tp) if tp == this => // recursive type alias
                return Some(AliasType(ta, Right(this), Right(this)))
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

  private def actualImpl(projected: ScType, updateWithProjectionSubst: Boolean): Option[(PsiNamedElement, ScSubstitutor)] = cachedWithRecursionGuard("actualImpl", element, Option.empty[(PsiNamedElement, ScSubstitutor)], BlockModificationTracker(element), (projected, updateWithProjectionSubst)) {
    val resolvePlace = {
      def fromClazz(definition: ScTypeDefinition): PsiElement =
        definition.extendsBlock.templateBody
          .flatMap(_.lastChildStub)
          .getOrElse(definition.extendsBlock)

      projected.tryExtractDesignatorSingleton.extractClass match {
        case Some(definition: ScTypeDefinition) => fromClazz(definition)
        case _ =>
          projected match {
            case ScThisType(definition: ScTypeDefinition) => fromClazz(definition)
            case _                                        => element
          }
      }
    }

    import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
    def processType(kinds: Set[ResolveTargets.Value] = ValueSet(CLASS)): Option[(PsiNamedElement, ScSubstitutor)] = {
      def elementClazz: Option[PsiClass] = element match {
        case named: ScBindingPattern => Option(named.containingClass)
        case member: ScMember        => Option(member.containingClass)
        case _                       => None
      }

      projected match {
        case ScDesignatorType(clazz: PsiClass)
          if elementClazz.exists(ScEquivalenceUtil.areClassesEquivalent(_, clazz)) =>
          return Some(element, ScSubstitutor(projected))
        case p @ ParameterizedType(ScDesignatorType(clazz: PsiClass), _)
          if elementClazz.exists(ScEquivalenceUtil.areClassesEquivalent(_, clazz)) =>
          return Some(element, ScSubstitutor(projected).followed(p.substitutor))
        case p: ScProjectionType =>
          p.actualElement match {
            case `element` if element.is[ScTypeAlias] => //rare case of recursive projection, see SCL-15345
              return Some(element, p.actualSubst)
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
        case ScCompoundType(_, _, typesMap) =>
          typesMap.get(element.name) match {
            case Some(taSig) => return Some(taSig.typeAlias, taSig.substitutor)
            case _           =>
          }
        case _ => //continue with processor :(
      }


      val processor = new ResolveProcessor(kinds, resolvePlace, element.name) {
        doNotCheckAccessibility()

        override protected def addResults(results: Iterable[ScalaResolveResult]): Boolean = {
          candidatesSet ++= results
          true
        }
      }

      processor.processType(projected, resolvePlace, ScalaResolveState.empty, updateWithProjectionSubst)

      processor.candidates match {
        case Array(candidate) => candidate.element match {
          case candidateElement: PsiNamedElement =>
            val thisSubstitutor = ScSubstitutor(projected, candidateElement.findContextOfType(classOf[PsiClass]).orNull)
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
    def isEligibleForPrefixUnification(proj: ScType): Boolean = proj.subtypeExists {
      case _: UndefinedType => true
      case _                => false
    }

    def checkDesignatorType(e: PsiNamedElement, other: ScType): ConstraintsResult = e match {
      case td: ScTypedDefinition if td.isStable =>
        val tp = actualSubst(td.`type`().getOrAny)
        tp match {
          case designatorOwner: DesignatorOwner if designatorOwner.isSingleton =>
            tp.equiv(other, constraints, falseUndef)
          case lit: ScLiteralType => lit.equiv(other, constraints, falseUndef)
          case _                  => ConstraintsResult.Left
        }
      case _ => ConstraintsResult.Left
    }

    val desRes = checkDesignatorType(actualElement, r)
    if (desRes.isRight) return desRes

    r match {
      case tpt: ScTypePolymorphicType =>
        return ScEquivalenceUtil
          .isDesignatorEqiuivalentToPolyType(this, tpt, constraints, falseUndef)
          .getOrElse(ConstraintsResult.Left)
      case _ => ()
    }

    val res = r match {
      case t: StdType =>
        element match {
          case synth: ScSyntheticClass => synth.stdType.equiv(t, constraints, falseUndef)
          case _                       => ConstraintsResult.Left
        }
      case ParameterizedType(ScProjectionType(_, _), _) =>
        r match {
          case AliasType(_: ScTypeAliasDefinition, Right(lower), _) =>
            this.equiv(lower, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
      case proj2 @ ScProjectionType(p1, _) =>
        val desRes = checkDesignatorType(proj2.actualElement, this)
        if (desRes.isRight) return desRes

        val lElement = actualElement
        val rElement = proj2.actualElement

        val sameElements = lElement == rElement || {
          lElement.name == rElement.name &&
            (isEligibleForPrefixUnification(projected) || isEligibleForPrefixUnification(p1))
        }

        if (sameElements) projected.equiv(p1, constraints, falseUndef)
        else
          r match {
            case AliasType(_: ScTypeAliasDefinition, Right(lower), _) =>
              this.equiv(lower, constraints, falseUndef)
            case _ => ConstraintsResult.Left
          }
      case ScThisType(_) =>
        element match {
          case _: ScObject                        => ConstraintsResult.Left
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

    res match {
      case cs: ConstraintSystem   => cs
      case ConstraintsResult.Left =>
        this match {
          case AliasType(_: ScTypeAliasDefinition, Right(lower), _) =>
            lower.equiv(r, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
    }
  }

  override def isFinalType: Boolean = actualElement match {
    case cl: PsiClass if cl.isEffectivelyFinal => true
    case alias: ScTypeAliasDefinition          => alias.aliasedType.exists(_.isFinalType)
    case _                                     => false
  }

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitProjectionType(this)

  def canEqual(other: Any): Boolean = other.is[ScProjectionType]

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
      hash = projected #+ element

    hash
  }

  override def typeDepth: Int = projected.typeDepth
}

object ScProjectionType {

  private val guard = RecursionManager.RecursionGuard[ScType, Nothing]("aliasProjectionGuard")

  def simpleAliasProjection(p: ScProjectionType): ScType = {
    p.actual() match {
      case (td: ScTypeAliasDefinition, subst) if td.typeParameters.isEmpty =>
        val upper = guard.doPreventingRecursion(p) {
          td.upperBound.map(subst).toOption
        }
        upper
          .flatten
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
