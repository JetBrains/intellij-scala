package org.jetbrains.plugins.scala.lang.psi.types.api
package designator

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, RecursionManager, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
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

  // scalac's `pre.memberType(sym)`: the singleton's underlying follows the member resolved
  // on the prefix (`actual`), not the static `element`, which may be an abstract declaration
  // (`IGen#global: SymbolTable`) overridden by a singleton-typed member
  // (`val global: X.this.type`). `actual` runs a resolver over the prefix, which is costly
  // and re-entrant (it expands the prefix's exports, SCL-22266), so consult it only when
  // the static `element` is a stable member that an override could refine; otherwise
  // the member type is the declared one.
  override private[types] def designatorSingletonType: Option[ScType] = element match {
    case _: ScObject                                                                  => None
    case stable: ScTypedDefinition if stable.isStable && !ScProjectionType.overridable(stable) =>
      compoundMemberType(stable).orElse(super.designatorSingletonType.map(actualSubst))
    case stable: ScTypedDefinition if stable.isStable =>
      actualElement match {
        case parameter: ScParameter if parameter.isStable         => parameter.insideParamType.toOption.map(actualSubst)
        case definition: ScTypedDefinition if definition.isStable =>
          compoundMemberType(definition).orElse(definition.`type`().toOption.map(actualSubst))
        case _                                                    => None
      }
    case _ => None
  }

  /**
   * The type of `member` when it is a member of the compound type `projected` is typed with, as
   * scalac's `memberType` reads a compound's decls: `reifier.global` for
   * `lazy val reifier: Reifier { val global: Utils.this.global.type }`, or `bTypes.coreBTypes.bTypes`
   * for `val coreBTypes = new CoreBTypesFromSymbols[G] { val bTypes: BTypesFromSymbols.this.type = ... }`.
   * The compound holds the member's type as seen from where `projected` was selected
   * (`NodePrinters.this.global.type` inside `trait NodePrinters { self: Utils => }`). The declaration
   * belongs to a refinement or an anonymous class, which has no owner chain through `projected`, so
   * re-substituting its this-types from `projected` would rewrite `Utils.this` onto `reifier` and make
   * `reifier.global` its own singleton.
   */
  private def compoundMemberType(member: ScTypedDefinition): Option[ScType] =
    projected.widen match {
      case ScCompoundType(_, signatures, _) => signatures.collectFirst { case (sig, tpe) if sig.namedElement == member => tpe }
      case _                                => None
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
            // Seen from `projected` at the owner of the member found there (scalac's `sym.owner` for the `sym`
            // that `pre.memberType` picks), not of the static `element`: an abstract `type T` of `api.Internals`
            // realized by a `class T` of `internal.Trees` has `Trees.this` in its base types.
            // A member of a refinement (`Symbol { type NameType = Symbol.this.NameType }`) has no owner chain
            // through `projected`: its this-types belong to the refinement's lexical context, which the
            // compound's own substitutor (in `candidate.substitutor`) already puts into the right view, as
            // scalac reads a compound's decls. Re-substituted from `projected`, the anchorless walk rewrote
            // `Symbol.this` onto `projected` itself (`clone.NameType` aliasing `clone.NameType`).
            val thisSubstitutor =
              if (ScProjectionType.isRefinementMember(candidateElement)) ScSubstitutor.empty
              else ScSubstitutor(projected, ScSubstitutor.declarationAnchor(candidateElement))
            // A member found in a compound already has its signature seen from the compound
            // (`MixinNodes.SuperTypesData(cp, ...)` links `cp` at each base class), the same link as
            // `thisSubstitutor`. Applied twice, a compound self-rooted one (`T1.this -> T1 with T1.this.M3`)
            // rewrites the `T1.this` that its first copy brought in and grows the type to
            // `T1 with (T1 with T1.this.M3)#M3` (`partRooted_twice_diverges`, retronym/scala-type-system-tck#7).
            val defaultSubstitutor =
              projected match {
                case _: ScThisType | _: ScCompoundType => candidate.substitutor
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

    // A stable value typed as a singleton, or as an alias to one, is that singleton.
    def checkDesignatorType(e: PsiNamedElement, subst: ScSubstitutor, other: ScType): ConstraintsResult = e match {
      case td: ScTypedDefinition if td.isStable =>
        val declared = subst(td.`type`().getOrAny)
        val tp = ScProjectionType.singletonThroughAliases(declared, td).getOrElse(declared)
        tp match {
          case designatorOwner: DesignatorOwner if designatorOwner.isSingleton =>
            tp.equiv(other, constraints, falseUndef)
          case lit: ScLiteralType => lit.equiv(other, constraints, falseUndef)
          case _                  => ConstraintsResult.Left
        }
      case _ => ConstraintsResult.Left
    }

    val desRes = checkDesignatorType(actualElement, actualSubst, r)
    if (desRes.isRight) return desRes

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
        val desRes = checkDesignatorType(proj2.actualElement, proj2.actualSubst, this)
        if (desRes.isRight) return desRes

        val lElement = actualElement
        val rElement = proj2.actualElement

        val sameElements = ScEquivalenceUtil.smartEquivalence(lElement, rElement) || {
          lElement.name == rElement.name &&
            (isEligibleForPrefixUnification(projected) || isEligibleForPrefixUnification(p1))
        }

        if (sameElements) ScProjectionType.collapseSingletonPath(projected).equiv(ScProjectionType.collapseSingletonPath(p1), constraints, falseUndef)
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


  /** Whether a subclass could override `d`: a non-final member of a class or trait (not a
   *  method or extension parameter, not a local). */
  private def overridable(d: ScTypedDefinition): Boolean = d.nameContext match {
    case m: ScMember => m.containingClass != null && !m.isEffectivelyFinal
    case _           => false
  }

  private[designator] def isSingletonLike(t: ScType): Boolean = t match {
    case _: ScThisType      => true
    case d: DesignatorOwner => d.isSingleton
    case _                  => false
  }

  /**
   * Collapse a stable val path to the singleton it is known to be, to a fixpoint, prefixes first:
   * `global.analyzer.global`, where `analyzer`'s type refines `val global: Global.this.type`, normalizes to
   * `global`, and `universe.analyzer` for a local or early-defined `val universe: self.global.type` to
   * `self.global.analyzer`. scalac follows a path's singleton type when comparing prefixes of path-dependent
   * types; without this, a cake member reached through such an alias yields an unreduced prefix and a spurious
   * mismatch (SCL-21947, `Infer.inferTypedPattern`; scala/scala `Macros.macroContext`). `fuel` bounds the walk.
   *
   * `throughAliases` also follows a val typed by an alias to a singleton (`val symbolTable: SymbolTable` for
   * `type SymbolTable = outer.symbolTable.type`). That is right for comparing paths, but not for respelling
   * one: scalac keeps `TastyUniverse.symbolTable.Symbol` as written, so canonicalization passes `false`.
   */
  private[types] def collapseSingletonPath(tp: ScType, fuel: Int = 8, throughAliases: Boolean = true): ScType = {
    def singletonOf(t: ScType, place: PsiElement): Option[ScType] =
      if (throughAliases) singletonThroughAliases(t, place) else Some(t).filter(isSingletonLike)

    tp match {
      case proj: ScProjectionType if fuel > 0 =>
        val prefix = collapseSingletonPath(proj.projected, fuel - 1, throughAliases)
        val withPrefix: ScType = if (prefix eq proj.projected) proj else ScProjectionType(prefix, proj.element)
        withPrefix match {
          case p: ScProjectionType if isStableValue(p.element) =>
            p.designatorSingletonType.flatMap(singletonOf(_, p.element)) match {
              case Some(singleton) if singleton ne p => collapseSingletonPath(singleton, fuel - 1, throughAliases)
              case _                                 => p
            }
          case other => other
        }
      // A stable value without a prefix (a local or early-defined val, a parameter) typed as a singleton.
      case des: ScDesignatorType if fuel > 0 && isStableValue(des.element) =>
        des.designatorSingletonType.flatMap(singletonOf(_, des.element)) match {
          case Some(singleton) if singleton ne des => collapseSingletonPath(singleton, fuel - 1, throughAliases)
          case _                                   => tp
        }
      case _ => tp
    }
  }

  /** Whether [[collapseSingletonPath]] may change `tp`: some element of its prefix spine is a stable value. */
  @scala.annotation.tailrec
  private[types] def mayCollapse(tp: ScType): Boolean = tp match {
    case proj: ScProjectionType => isStableValue(proj.element) || mayCollapse(proj.projected)
    case des: ScDesignatorType  => isStableValue(des.element)
    case _                      => false
  }

  private def isStableValue(element: PsiNamedElement): Boolean = element match {
    case _: ScObject          => false
    case d: ScTypedDefinition => d.isStable
    case _                    => false
  }

  /**
   * `t` if it is a singleton, or the singleton that the alias `t` stands for: a val typed by an alias to a
   * singleton is that singleton, as for scalac, which dealiases a singleton's underlying type. scala/scala's
   * `ClassfileParser.TastyUniverse` has `type SymbolTable = ClassfileParser.this.symbolTable.type` and
   * `val symbolTable: SymbolTable`, so `TastyUniverse.symbolTable.Symbol` is `symbolTable.Symbol`.
   */
  private[designator] def singletonThroughAliases(t: ScType, place: PsiElement, fuel: Int = 4): Option[ScType] =
    if (isSingletonLike(t)) Some(t)
    else if (fuel <= 0) None
    else
      t.aliasType(using Context(place))
        .filter(alias => alias.ta.is[ScTypeAliasDefinition] && !alias.effectivelyOpaque)
        .flatMap(_.upper.toOption)
        .flatMap(singletonThroughAliases(_, place, fuel - 1))

  /** Whether `member` is declared in a refinement, which gives it no class to anchor this-types at. */
  private[designator] def isRefinementMember(member: PsiNamedElement): Boolean =
    ScSubstitutor.declarationAnchor(member) == null &&
      com.intellij.psi.util.PsiTreeUtil.getContextOfType(member, classOf[org.jetbrains.plugins.scala.lang.psi.api.base.types.ScRefinement]) != null

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
