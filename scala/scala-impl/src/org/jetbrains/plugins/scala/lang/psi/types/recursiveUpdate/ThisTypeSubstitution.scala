package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.psi._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements._, params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._, typedef._
import org.jetbrains.plugins.scala.lang.psi.types._, api._, designator._, nonvalue._
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec

/**
 * Re-anchors `C.this` leaves onto the prefix `target`, the analogue of scalac's
 * `AsSeenFromMap.thisTypeAsSeen`. `seenFromClass` is the class the type was declared in
 * (scalac's `clazz` in `tp.asSeenFrom(pre, clazz)`); `null` selects the legacy anchorless
 * walk, which narrows by inheritance alone.
 *
 * Unlike scalac, whose walk only ever strips prefixes off `pre`, this substitution runs
 * inside the generic `recursiveUpdate` engine, is fused with other updates into one
 * chain, and its output is fed back into resolution. Two rules keep that sound and
 * terminating (they replace the former `hasRecursiveThisType` guard, which scanned the
 * whole target and still missed the cross-symbol case):
 *
 *  - No self-embedding: refuse a rewrite whose result is a path still rooted in (an inheritor of)
 *    the this-type being rewritten; see [[embedsRewrittenThis]].
 *  - Owner-chain matching: an anchored walk that never reaches the this-type's class leaves
 *    it alone, rather than falling back to the anchorless heuristic; see
 *    [[ownerChainMatches]].
 */
private case class ThisTypeSubstitution(target: ScType, @Nullable seenFromClass: PsiClass) extends LeafSubstitution {

  override def toString: String = seenFromClass match {
    case null => s"`this` -> $target"
    case _    => s"`this` -> $target asSeenFrom $seenFromClass"
  }

  override protected val subst: PartialFunction[LeafType, ScType] = {
    case th: ScThisType =>
      TypeRecursionGuard.nestedSubstitution(th, s"$th with $this") {
        val res = doUpdateThisTypeFromClass(th, target, seenFromClass)
        if ((res ne th) && embedsRewrittenThis(res, th)) th
        else res
      }
  }

  @tailrec
  private def spineRootThis(tp: ScType): Option[ScThisType] = tp match {
    case th: ScThisType                                 => Some(th)
    case ScProjectionType(pre, _)                       => spineRootThis(pre)
    case ParameterizedType(ScProjectionType(pre, _), _) => spineRootThis(pre)
    case _                                              => None
  }

  /**
   * No self-embedding: a rewrite of `th` to a path whose root is still `th`'s class (or an
   * inheritor of it) hasn't eliminated `th`, it has embedded it. Fed back through
   * resolution, such a result is re-substituted and grows without bound. scalac's walk
   * can't produce this shape, since it only strips prefixes off `pre`.
   *
   * A bare this-type result (`Types.this -> Global.this`) is always progress: it is the
   * ordinary cake re-anchor onto an inheritor, and has no structure to recirculate.
   * SCL-7043's `Enumeration.this -> CE.this.enum.type` is admitted too: `CE` aggregates an
   * `Enumeration` rather than inheriting one.
   */
  private def embedsRewrittenThis(res: ScType, th: ScThisType): Boolean = res match {
    case _: ScThisType => false
    case _             => spineRootThis(res).exists(rootTh => isSameOrInheritor(rootTh.element, th))
  }

  /** Narrows `thisTp` against `target`, climbing `target`'s prefix while it doesn't. */
  @tailrec
  private def doUpdateThisType(thisTp: ScThisType, target: ScType): ScType =
    if (isMoreNarrow(target, thisTp, Set.empty)) target
    else {
      containingClassType(target) match {
        case Some(targetContext) => doUpdateThisType(thisTp, targetContext)
        case _                   => thisTp
      }
    }

  /** The anchored walk: scalac's `thisTypeAsSeen`, climbing `clazz`'s owner chain in step with `target`. */
  @tailrec
  private def doUpdateThisTypeFromClass(thisTp: ScThisType, target: ScType, @Nullable clazz: PsiClass): ScType =
    if (clazz == null) doUpdateThisType(thisTp, target)
    // scalac's `toPrefix` returns `pre` as soon as the this-type's class is a subclass of
    // the cursor and `pre` widens to that class, before consulting `pre baseType clazz`.
    // Without this, a this-type declared in a proper superclass of the cursor is walked
    // past and lost: `SymbolTable.this.Type`, the inferred result type of a member of
    // `Definitions` (`SymbolTable <: Definitions`), seen from `g: Global`, must become
    // `g.Type`. The subclass test keeps the cross-symbol case out (there, `Infer` is not a
    // subclass of the cursor `Typer`), so owner-chain matching below still applies to it.
    else if (isInheritorDeep(thisTp.element, clazz) && isMoreNarrow(target, thisTp, Set.empty))
      doUpdateThisType(thisTp, target)
    else if (clazz == thisTp.element || clazz.containingClass == null) {
      if (ownerChainMatches(clazz, target, thisTp)) doUpdateThisType(thisTp, target)
      else thisTp
    }
    else {
      // The merged base type (scalac's `pre baseType clazz`), so that several contributions
      // of the same class resolve to one deterministic prefix.
      BaseTypes.baseType(target, clazz).flatMap(containingClassType) match {
        case Some(targetContext) =>
          doUpdateThisTypeFromClass(thisTp, targetContext, clazz.containingClass)
        case _ =>
          // `clazz` isn't a base class of `target`, e.g. it is an inner class reached through
          // a path (`global.AstTransformer`), and an inherited member mentions the enclosing
          // universe's this-type (`Trees.this`). scalac keeps walking until `pre` is exhausted
          // instead of giving up, so narrow against `target` directly (SCL-21947, the
          // `OuterPathTransformer` shape), subject to owner-chain matching.
          if (ownerChainMatches(clazz, target, thisTp)) doUpdateThisType(thisTp, target)
          else thisTp
      }
    }

  private def containingClassType(tp: ScType): Option[ScType] = tp match {
    case ScThisType(template) =>
      template.containingClass match {
        case td: ScTemplateDefinition => Some(ScThisType(td))
        case _                        => None
      }
    case ScProjectionType(newType, _)                       => Some(newType)
    case ParameterizedType(ScProjectionType(newType, _), _) => Some(newType)
    case _                                                  => None
  }

  private def isSameOrInheritor(clazz: PsiClass, thisTp: ScThisType): Boolean =
    clazz == thisTp.element || isInheritorDeep(clazz, thisTp.element)

  /**
   * Owner-chain matching: scalac's `matchesPrefixAndClass` rewrites a this-type only when
   * the owner-chain cursor reaches its class. An anchored walk whose cursor never does is
   * scalac's unmatched case: the this-type is left for another, correctly anchored hop.
   * Falling back to the anchorless inheritance heuristic instead is unsound: firing
   * `[target = typer.this.type, seenFromClass = Typer]` on `Infer.this` climbs
   * `Typer -> Typers`, never `Infer`, yet narrowed `Infer.this` to
   * `Global.this.analyzer.type` because `Analyzer` inherits `Infer`. That embeds a fresh
   * `Global.this` root, which the rest of the chain re-anchors, growing the type on every
   * re-derivation (the cross-symbol pump in the scala/scala compiler cake).
   *
   * Two admissions:
   *  - the cursor's containing chain reaches the this-type's class. Same-or-inheritor
   *    rather than scalac's `==`, since IntelliJ spells cake self-types through the
   *    declaring trait; `areClassesEquivalent` since an object member's anchor is a
   *    different PSI handle than the `ScObject` (SCL-6549);
   *  - `target` denotes exactly the this-type's own class, i.e. it re-spells the same
   *    instance as a path (`implicitInstance.this` against `SCL6549.implicitInstance.type`).
   *    `target` is widened first, as scalac does with `pre.widen`, since a val path
   *    (`quotes.reflect.type`) only reveals its class once widened. A strict inheritor
   *    (`Global.this.analyzer.type` for `Infer.this`) is the case above and stays blocked.
   */
  private def ownerChainMatches(clazz: PsiClass, target: ScType, thisTp: ScThisType): Boolean =
    ownerChainReaches(clazz, thisTp) || targetDenotesLeafClass(target, thisTp)

  @tailrec
  private def ownerChainReaches(clazz: PsiClass, thisTp: ScThisType): Boolean =
    if (clazz == null) false
    else if (isSameOrInheritor(clazz, thisTp) || ScEquivalenceUtil.areClassesEquivalent(clazz, thisTp.element)) true
    else if (selfTypeReaches(clazz, thisTp)) true
    else ownerChainReaches(clazz.containingClass, thisTp)

  // A cake trait sees a sibling's members through its self type (`trait Definitions
  // { self: SymbolTable => }` uses `Symbol` from `Symbols`). scalac spells such a type
  // `Definitions.this.Symbol`, so its owner-chain walk matches at `Definitions`; IntelliJ
  // spells it after the declaring trait, `Symbols.this.Symbol`. Treat a cursor whose self
  // type reaches the this-type's class as reaching it.
  private def selfTypeReaches(clazz: PsiClass, thisTp: ScThisType): Boolean = clazz match {
    case td: ScTemplateDefinition =>
      td.selfType.flatMap(_.extractClass).exists(sc => sc != clazz && isSameOrInheritor(sc, thisTp))
    case _ => false
  }

  private def targetDenotesLeafClass(target: ScType, thisTp: ScThisType)(implicit context: Context): Boolean = {
    def denotesLeaf(tp: ScType): Boolean = extractAll(tp) match {
      case Some(cls: PsiClass) => cls == thisTp.element || ScEquivalenceUtil.areClassesEquivalent(cls, thisTp.element)
      case _                   => false
    }
    denotesLeaf(target) || {
      val widened = target.widen
      (widened ne target) && denotesLeaf(widened)
    }
  }

  private def hasSameOrInheritor(compound: ScCompoundType, thisTp: ScThisType)(implicit context: Context): Boolean = {
    compound.components
      .exists {
        extractAll(_)
          .exists {
            case tp: ScCompoundType => hasSameOrInheritor(tp, thisTp)
            case tp: ScTypeParam =>
              (for {
                upper <- tp.upperBound.toOption
                cls   <- upper.extractClass
              } yield isSameOrInheritor(cls, thisTp)).getOrElse(false)
            // An abstract type member (`type Setting <: SettingValue`) reaches its base
            // classes only through its upper bound, as for type parameters above
            // (SCL-21947, `MutableSettings`: `BooleanSetting <: Setting { type T = ... }`).
            case ta: ScTypeAlias => isMoreNarrow(ta.upperBound.getOrAny, thisTp, Set.empty)
            case cls: PsiClass => isSameOrInheritor(cls, thisTp)
            case _             => false
          }
      }
  }

  @tailrec
  private def isMoreNarrow(target: ScType, thisTp: ScThisType, visited: Set[PsiElement])(implicit context: Context): Boolean = {
    extractAll(target) match {
      case Some(pat: ScBindingPattern) =>
        if (visited.contains(pat)) false
        else isMoreNarrow(pat.`type`().getOrAny, thisTp, visited + pat)
      case Some(param: ScParameter)    => isMoreNarrow(param.`type`().getOrAny, thisTp, visited)
      case Some(typeParam: PsiTypeParameter) =>
        if (visited.contains(typeParam)) false
        else target match {
          case t: TypeParameterType => isMoreNarrow(t.upperType, thisTp, visited + typeParam)
          case p: ParameterizedType =>
            p.designator match {
              case tpt: TypeParameterType =>
                val upperType = ParameterizedType(tpt.upperType, p.typeArguments)
                isMoreNarrow(upperType, thisTp, visited + typeParam)
              case _                      =>
                isMoreNarrow(p.substitutor(TypeParameterType(typeParam)), thisTp, visited + typeParam)
            }
          case _ => isSameOrInheritor(typeParam, thisTp)
        }
      case Some(t: ScTypeDefinition) =>
        if (visited.contains(t)) false
        else if (isSameOrInheritor(t, thisTp)) true
        else
          t.selfType match {
            case Some(selfTp) => isMoreNarrow(selfTp, thisTp, visited + t)
            case _            => false
          }
      case Some(td: ScTypeAliasDeclaration) => isMoreNarrow(td.upperBound.getOrAny, thisTp, visited)
      case Some(cl: PsiClass)               => isSameOrInheritor(cl, thisTp)
      case Some(named: ScTypedDefinition)   =>
        named.`type`().getOrAny match {
          // A stable path whose declared type IS `thisTp` itself (`val global: Global.this.type`)
          // cannot narrow `thisTp`: collapsing `Global.this := <path>` where `<path>: Global.this.type`
          // is circular and, when this path is reached as a projection prefix
          // (`pre.genBCode.global`), folds `Global.this` onto the whole projection — a self-referential
          // type the resolver's recursion guard then prunes, dropping all members. Refuse the collapse
          // so `doUpdateThisType` keeps walking the prefix chain to a concrete outer prefix
          // (`pre.global`). (nsc `Global { val genBCode: SubComponent { val global: Global.this.type } }` shape)
          case ScThisType(c) if c == thisTp.element => false
          case nt                                   => isMoreNarrow(nt, thisTp, visited)
        }
      case Some(compound: ScCompoundType)   => hasSameOrInheritor(compound, thisTp)
      case _                                => false
    }
  }

  private def extractAll(tp: ScType)(implicit context: Context) = {
    // Like tp.extractDesignated(expandAliases = true)
    // But also return non-designator returns
    // Such as ScCompoundType, after dealiasing and dereferencing.

    def elem1(tp: DesignatorOwner) = tp match { case tp: ScProjectionType => tp.actualElement case _ => tp.element }
    def subst(tp: DesignatorOwner) = tp match { case tp: ScProjectionType => tp.actualSubst   case _ => ScSubstitutor.empty }

    def rec(tp: ScType, seen: Set[ScTypeAlias]): Either[ScType, PsiNamedElement] = tp match {
      case tp: NonValueType      => rec(tp.inferValueType, seen)
      case tp: DesignatorOwner   => elem1(tp) match {
        case ta: ScTypeAliasDefinition if !ta.isEffectivelyOpaque && !seen(ta) => ta.aliasedType.map(subst(tp)).fold(_ => Left(tp), rec(_, seen + ta))
        case tp                                     => Right(tp)
      }
      case tp: ParameterizedType => tp match {
        case AliasType(ta: ScTypeAliasDefinition, _, Right(ub), effectivelyOpaque) if !effectivelyOpaque && !seen(ta) => rec(ub, seen + ta)
        case _                                                               => rec(tp.designator, seen)
      }
      case tp: StdType           => tp.syntheticClass.toRight(tp)
      case tp: ScExistentialType => rec(tp.quantified, seen)
      case tp: TypeParameterType => Right(tp.psiTypeParameter)
      case tp: ScCompoundType    => Left(tp)
      case _                     => Left(tp)
    }

    rec(tp, Set.empty).fold(Some(_), Some(_))
  }
}

private object ThisTypeSubstitution {

  /**
   * Collapse a non-canonical spelling of a singleton path (`global.analyzer.global` for
   * `global`) where it is minted: when a substitutor is built, and when a substitution
   * rebuilds a projection over a rewritten prefix. Otherwise resolution feeds fresh
   * spellings back in as substitution targets and the paths compound
   * (`analyzer.global.analyzer.global...`) until the no-self-embedding rule cuts them off.
   */
  def canonicalizeTarget(tp: ScType): ScType =
    TypeRecursionGuard.nestedSubstitution(tp, s"canonicalizing $tp")(ScProjectionType.collapseSingletonPath(tp))
}
