package org.jetbrains.plugins.scala.lang.psi.types.api
package designator

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, RecursionManager, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ConstraintSystem, ConstraintsResult, Context, ScCompoundType, ScLiteralType, ScType, ScTypeExt, ScalaTypeVisitor}
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

  override protected def calculateAliasType(implicit context: Context): Option[AliasType] = calculateAliasTypeAux(actualElement, actualSubst)

  override def isStable: Boolean = (projected match {
    case designatorOwner: DesignatorOwner => designatorOwner.isStable
    case _ => false
  }) && super.isStable

  override private[types] def designatorSingletonType: Option[ScType] = {
    val normal = super.designatorSingletonType.map(actualSubst)
    // scalac's `pre.memberType(sym)`: the singleton's *underlying* follows the
    // RESOLVED member, not the static `element`. When `element` is an abstract
    // declaration (e.g. `IGen#global: SymbolTable`) — or `None` for an object prefix —
    // the prefix may carry a more-specific override (an anonymous-class refinement or
    // `override object … { val global: X.this.type }`) whose type is a singleton.
    // Use that override's type, asSeenFrom this prefix: `ScSubstitutor(projected)`
    // rewrites the override's own and *enclosing* `this`-types onto the prefix chain
    // (so `Global.this` becomes e.g. `NscGen.this.global`), matching scalac's asSeenFrom.
    if (normal.exists(ScProjectionType.isSingletonLike)) normal
    else ScProjectionType.overrideSingletonOf(this).filter(ScProjectionType.isSingletonLike).orElse(normal)
  }

  private def actualImpl(projected: ScType, updateWithProjectionSubst: Boolean)(implicit context: Context): Option[(PsiNamedElement, ScSubstitutor)] = cachedWithRecursionGuard("actualImpl", element, Option.empty[(PsiNamedElement, ScSubstitutor)], BlockModificationTracker(element), (projected, updateWithProjectionSubst)) {
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
          return Some(element, ScSubstitutor(projected, clazz))
        case p @ ParameterizedType(ScDesignatorType(clazz: PsiClass), _)
          if elementClazz.exists(ScEquivalenceUtil.areClassesEquivalent(_, clazz)) =>
          return Some(element, ScSubstitutor(projected, clazz).followed(p.substitutor))
        case p: ScProjectionType =>
          p.actualElement match {
            case `element` if element.is[ScTypeAlias] => //rare case of recursive projection, see SCL-15345
              return Some(element, p.actualSubst)
            case clazz: PsiClass
              if elementClazz.exists(ScEquivalenceUtil.areClassesEquivalent(_, clazz)) =>
              return Some(element, ScSubstitutor(projected, clazz).followed(p.actualSubst))
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
            val thisSubstitutor = ScSubstitutor(projected, element.findContextOfType(classOf[PsiClass]).orNull)
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
          case null => None
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

  private def actual(updateWithProjectionSubst: Boolean = true)(implicit context: Context): (PsiNamedElement, ScSubstitutor) =
    actualImpl(projected, updateWithProjectionSubst).getOrElse(element, ScSubstitutor.empty)

  def actualElement: PsiNamedElement = actual()._1
  def actualSubst: ScSubstitutor = actual()._2

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean)(implicit context: Context): ConstraintsResult = {
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

    // Override-aware singleton collapse (scalac's `pre.memberType`). `checkDesignatorType`
    // above uses the prefix designator's statically-declared type, which is not a
    // singleton when that designator points at an ABSTRACT member (e.g.
    // `SymbolLoaders#symbolTable: SymbolTable`) whose singleton-ness comes only from an
    // override further down the prefix's class (`symbolTable: global.type`). Consult the
    // override-aware `designatorSingletonType` to recover the underlying singleton and
    // compare. Used only as a fallback, so it never turns a passing comparison into a
    // failure (SCL-21947, the BrowsingLoaders.enterIfNew override-matching case).
    def checkOverrideSingleton(proj: ScProjectionType, other: ScType): ConstraintsResult =
      proj.designatorSingletonType match {
        case Some(tp) if ScProjectionType.isSingletonLike(tp) => tp.equiv(other, constraints, falseUndef)
        case _                                                => ConstraintsResult.Left
      }

    val desRes = checkDesignatorType(actualElement, r)
    if (desRes.isRight) return desRes

    val ovrRes = checkOverrideSingleton(this, r)
    if (ovrRes.isRight) return ovrRes

    r match {
      case tpt: ScTypePolymorphicType =>
        return ScEquivalenceUtil
          .isTypeConstructorEquivalentToPolyType(this, tpt, constraints, falseUndef)
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
          case AliasType(_: ScTypeAliasDefinition, Right(lower), _, effectivelyOpaque) if !effectivelyOpaque =>
            this.equiv(lower, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
      case proj2 @ ScProjectionType(p1, _) =>
        val desRes = checkDesignatorType(proj2.actualElement, this)
        if (desRes.isRight) return desRes

        val ovrRes = checkOverrideSingleton(proj2, this)
        if (ovrRes.isRight) return ovrRes

        val lElement = actualElement
        val rElement = proj2.actualElement

        val sameElements = ScEquivalenceUtil.smartEquivalence(lElement, rElement) || {
          lElement.name == rElement.name &&
            (isEligibleForPrefixUnification(projected) || isEligibleForPrefixUnification(p1))
        } || {
          // A class realizing an abstract type member (e.g. `class Symbol` overriding
          // `type Symbol >: Null`) — different PSI elements with the same name in the
          // same linearization. Treat as equivalent when one is an abstract type alias
          // and the other is a class, mirroring scalac's memberType. (SCL-21947)
          lElement.name == rElement.name && (
            (lElement.is[ScTypeAliasDeclaration] && rElement.is[PsiClass]) ||
            (lElement.is[PsiClass] && rElement.is[ScTypeAliasDeclaration])
          )
        }

        if (sameElements) projected.equiv(p1, constraints, falseUndef)
        else
          r match {
            case AliasType(_: ScTypeAliasDefinition, Right(lower), _, effectivelyOpaque) if !effectivelyOpaque =>
              this.equiv(lower, constraints, falseUndef)
            case _ => ConstraintsResult.Left
          }
      case ScThisType(_) =>
        element match {
          case _: ScObject                        => ConstraintsResult.Left
          case t: ScTypedDefinition if t.isStable =>
            t.`type`() match {
              case Right(singleton: DesignatorOwner) if singleton.isSingleton =>
                val newSubst = actualSubst.followed(ScSubstitutor(projected, ScSubstitutor.declarationAnchor(t)))
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
          case AliasType(_: ScTypeAliasDefinition, Right(lower), _, effectivelyOpaque) if !effectivelyOpaque =>
            lower.equiv(r, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
    }
  }

  override def isFinalType(implicit context: Context): Boolean = actualElement match {
    case cl: PsiClass if cl.isEffectivelyFinal => true
    case alias: ScTypeAliasDefinition if !alias.isEffectivelyOpaque => alias.aliasedType.exists(_.isFinalType)
    case _                                     => false
  }

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitProjectionType(this)

  def canEqual(other: Any): Boolean = other.is[ScProjectionType]

  override def equals(other: Any): Boolean = other match {
    case that: ScProjectionType =>
      (that `canEqual` this) &&
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
  private val singletonGuard = RecursionManager.RecursionGuard[ScType, Nothing]("overrideSingletonGuard")

  private[designator] def isSingletonLike(t: ScType): Boolean = t match {
    case _: ScThisType      => true
    case d: DesignatorOwner => d.isSingleton
    case _                  => false
  }

  /** Override-aware underlying of a stable val/object-path projection (scalac's
   *  `pre.memberType(sym)`): resolve the most-specific member of `proj.element.name`
   *  on the prefix's class, take its type, and asSeenFrom the prefix. */
  private[designator] def overrideSingletonOf(proj: ScProjectionType): Option[ScType] =
    proj.element match {
      case named: ScTypedDefinition =>
        singletonGuard.doPreventingRecursion(proj) {
          implicit val ctx: Context = Context(proj.element)
          proj.projected.extractClass.flatMap { cls =>
            TypeDefinitionMembers.getSignatures(cls).forName(named.name).iterator
              .map(_.namedElement)
              .collect { case td: ScTypedDefinition if td.isStable => td }
              .flatMap(e => e.`type`().toOption.iterator.map(ScSubstitutor(proj.projected, ScSubstitutor.declarationAnchor(e)).apply))
              .collectFirst { case t if isSingletonLike(t) => t }
          }
        }.flatten
      case _ => None
    }

  /**
   * Collapse a stable val-path projection to the singleton it is known to be, to a
   * fixpoint: `global.analyzer.global`, where `analyzer`'s type refines
   * `val global: Global.this.type`, normalizes to `global`. scalac follows a path's
   * singleton type when comparing prefixes of path-dependent types; without this, a cake
   * member reached through such an alias yields an unreduced prefix and a spurious
   * mismatch (SCL-21947, `Infer.inferTypedPattern`). `fuel` bounds the walk.
   */
  @annotation.tailrec
  private[types] def collapseSingletonPath(tp: ScType, fuel: Int = 8): ScType = tp match {
    case proj: ScProjectionType if fuel > 0 =>
      val stable = proj.element match {
        case d: ScTypedDefinition => d.isStable
        case _                    => false
      }
      if (!stable) tp
      else projectionSingleton(proj) match {
        case Some(singleton) if singleton ne proj => collapseSingletonPath(singleton, fuel - 1)
        case _                                    => tp
      }
    case _ => tp
  }

  /**
   * The singleton type of the stable path `proj`: its own (override-aware)
   * `designatorSingletonType`, or else, when the member resolved to an abstract
   * declaration (`Analyzer#global: Global`), the singleton its prefix's refinement
   * declares (`new { val global: Global.this.type } with Analyzer`).
   */
  private def projectionSingleton(proj: ScProjectionType): Option[ScType] =
    proj.designatorSingletonType.filter(isSingletonLike).orElse {
      proj.projected match {
        case pp: ScProjectionType =>
          pp.designatorSingletonType match {
            case Some(ct: ScCompoundType) =>
              ct.signatureMap.iterator.collectFirst {
                case (sig, tpe) if sig.name == proj.element.name && isSingletonLike(proj.actualSubst(tpe)) => proj.actualSubst(tpe)
              }
            case _ => None
          }
        case _ => None
      }
    }

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

  def unapply(proj: ScProjectionType): Some[(ScType, PsiNamedElement)] = {
    Some(proj.projected, proj.element)
  }

  object withActual {
    private val extractor = new withActual(true)

    def unapply(proj: ScProjectionType): Some[(PsiNamedElement, ScSubstitutor)] = extractor.unapply(proj)
  }

  class withActual(updateWithProjectionSubst: Boolean) {
    def unapply(proj: ScProjectionType)(implicit context: Context): Some[(PsiNamedElement, ScSubstitutor)] =
      Some(proj.actual(updateWithProjectionSubst))
  }
}
