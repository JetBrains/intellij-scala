package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
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
 * (scalac's `clazz` in `tp.asSeenFrom(pre, clazz)`).
 *
 * Unlike scalac, whose walk only ever strips prefixes off `pre`, this substitution runs
 * inside the generic `recursiveUpdate` engine, is fused with other updates into one
 * chain, and its output is fed back into resolution. Owner-chain matching keeps that
 * sound and terminating: a walk that never reaches the this-type's class leaves it
 * alone, rather than narrowing by inheritance alone; see [[ownerChainMatches]]. With every
 * link anchored, as scalac's are, no brake on the result is needed: the former
 * `hasRecursiveThisType` guard, and the no-self-embedding rule that replaced it, refused
 * legitimate single-pass rewrites (a member of `TokenData` seen from
 * `ScannerData.this.next.type`, where `ScannerData <: TokenData`) and are gone.
 * [[TypeRecursionGuard]] turns any growth that remains into a test failure.
 */
private case class ThisTypeSubstitution(target: ScType, seenFromClass: PsiClass) extends LeafSubstitution {

  override def toString: String = s"`this` -> $target asSeenFrom $seenFromClass"

  /** A1's bookkeeping, not part of the case class's equality. `a1MintSite`: the frame that minted the link, if
   *  its target has a this-leaf on the anchor's owner chain (the only links at risk). `a1Kind`: what the check at
   *  the link's first use found. `a1Duplicate`: set when a chain holds this link twice before it was classified.
   *  See [[SubstitutorInvariants.fixedTargetOnFirstUse]]. */
  @volatile private[recursiveUpdate] var a1MintSite: String = null
  @volatile private[recursiveUpdate] var a1Kind: Int = SubstitutorInvariants.A1Unchecked
  @volatile private[recursiveUpdate] var a1Duplicate: SubstitutorInvariants.A1Duplicate = null

  override protected val subst: PartialFunction[LeafType, ScType] = {
    case th: ScThisType =>
      TypeRecursionGuard.nestedSubstitution(th, s"$th with $this") {
        if (a1MintSite != null && a1Kind == SubstitutorInvariants.A1Unchecked) SubstitutorInvariants.fixedTargetOnFirstUse(this)
        val res = doUpdateThisTypeFromClass(th, target, seenFromClass)
        if (SubstitutorInvariants.enabled(SubstitutorInvariants.Rule.NoReentry) && (res ne th) && ThisTypeSubstitution.embedsRewrittenThis(res, th))
          SubstitutorInvariants.noReentry(this, th, res)
        res
      }
  }

  /** Narrows `thisTp` against `target`, climbing `target`'s prefix while it doesn't. */
  @tailrec
  private def doUpdateThisType(thisTp: ScThisType, target: ScType): ScType =
    if (isMoreNarrow(target, thisTp, Set.empty)) target
    else {
      containingClassType(target) match {
        case Some(targetContext) => doUpdateThisType(thisTp, targetContext)
        case _                   => leftAlone(thisTp)
      }
    }

  /** Every path on which the walk leaves `thisTp` unrewritten, for [[SubstitutorInvariants.noLeftover]] (A5). */
  private def leftAlone(thisTp: ScThisType): ScThisType = {
    SubstitutorInvariants.noLeftover(this, thisTp)
    thisTp
  }

  /** The anchored walk: scalac's `thisTypeAsSeen`, climbing `clazz`'s owner chain in step with `target`. */
  @tailrec
  private def doUpdateThisTypeFromClass(thisTp: ScThisType, target: ScType, clazz: PsiClass): ScType =
    // scalac's `toPrefix` returns `pre` as soon as the this-type's class is a subclass of
    // the cursor and `pre` widens to that class, before consulting `pre baseType clazz`.
    // Without this, a this-type declared in a proper superclass of the cursor is walked
    // past and lost: `SymbolTable.this.Type`, the inferred result type of a member of
    // `Definitions` (`SymbolTable <: Definitions`), seen from `g: Global`, must become
    // `g.Type`. The subclass test keeps the cross-symbol case out (there, `Infer` is not a
    // subclass of the cursor `Typer`), so owner-chain matching below still applies to it.
    if (isInheritorDeep(thisTp.element, clazz) && isMoreNarrow(target, thisTp, Set.empty))
      doUpdateThisType(thisTp, target)
    else if (clazz == thisTp.element || clazz.containingClass == null) {
      if (ownerChainMatches(clazz, target, thisTp)) doUpdateThisType(thisTp, target)
      else leftAlone(thisTp)
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
          else leftAlone(thisTp)
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
    else ownerChainReaches(clazz.containingClass, thisTp)

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

  @tailrec
  private def spineRootThis(tp: ScType): Option[ScThisType] = tp match {
    case th: ScThisType                                 => Some(th)
    case ScProjectionType(pre, _)                       => spineRootThis(pre)
    case ParameterizedType(ScProjectionType(pre, _), _) => spineRootThis(pre)
    case _                                              => None
  }

  private def isSameOrInheritor(clazz: PsiClass, cls: PsiClass): Boolean =
    clazz == cls || ScEquivalenceUtil.areClassesEquivalent(clazz, cls) || isInheritorDeep(clazz, cls)

  /**
   * A result that is a path still rooted in (an inheritor of) the rewritten this-type's
   * class. I4's census of what the former no-self-embedding brake refused; a bare
   * this-type result (`Types.this -> Global.this`) never counts.
   */
  def embedsRewrittenThis(res: ScType, th: ScThisType): Boolean = res match {
    case _: ScThisType => false
    case _             => spineRootThis(res).exists(rootTh => isSameOrInheritor(rootTh.element, th.element))
  }

  /** The this-types at the roots of the parts of `tp`, where parts are the operands of compounds and the
   *  prefixes of projections (`T1.this` in `T1 with T1.this.M3` and in `(T1 with T1.this.M3)#M3`). The Lean's
   *  `PartRootedAt`; `partRootedAt_iff` makes it "the class is among the this-types of `tp`" there. */
  private def partRoots(tp: ScType): Seq[ScThisType] = tp match {
    case th: ScThisType                                 => Seq(th)
    case ScProjectionType(pre, _)                       => partRoots(pre)
    case ParameterizedType(ScProjectionType(pre, _), _) => partRoots(pre)
    case c: ScCompoundType                              => c.components.flatMap(partRoots)
    case _                                              => Seq.empty
  }

  private def sameClass(a: PsiClass, b: PsiClass): Boolean = a == b || ScEquivalenceUtil.areClassesEquivalent(a, b)

  /**
   * A link that doesn't fix its target, of a shape whose single application is scalac's result and whose
   * second is not, so it may occur at most once in a chain. The Lean model of retronym/scala-type-system-tck#7
   * (`Relaxations.lean`) covers three shapes:
   *
   *  - self-rooted (`SelfRooted`): the target is a path rooted in the this-type of the anchor itself, and
   *    `res`, the link applied to its own target, grafts the target onto that root again
   *    (`UnitScanner.this.parensAnalyzer.type` to `UnitScanner.this.parensAnalyzer.parensAnalyzer.type`).
   *    `once_is_scalac`, `selfRooted_twice_diverges`. As for compounds below, a path rooted in an
   *    inheritor's this-type is not admitted: the model doesn't cover it;
   *  - compound self-rooted (`PartSelfRooted`): a compound target with a part rooted in the anchor's own
   *    this-type, the view `MixinNodes.SuperTypesData` mints for the members of a compound
   *    (`T1.this -> T1 with T1.this.M3`). Applied to its target the link grafts the compound into that part
   *    (`T1 with (T1 with T1.this.M3)#M3`), so it always moves its target; one pass never looks inside what it
   *    put in place. `partRooted_once_is_scalac`, `partRooted_twice_diverges`. A part rooted in an
   *    *inheritor*'s this-type (`T2.this -> T0 with T3.this.type with T3.this.I5`, `T3 <: T2`) is not
   *    admitted: scalac's walk leaves `T3.this` alone, and only the plugin's superclass early exit in
   *    `doUpdateThisTypeFromClass`, which the model leaves out, moves it;
   *  - outer-rooted (`OuterRooted`): the target is a path rooted in `D.this` for a class `D` strictly
   *    enclosing the anchor, through a value whose `D`-instance is another one (`I2.this -> T2.this.v12.type`
   *    with `v12: K0#I5`, `I5 <: I2`, inner classes of `T2`), and `res` is the target with only its root
   *    replaced (`asf_rootedAt`; that the root moved is `rewrites`, by `graft_inj`). The walk takes `T2.this` to the prefix of
   *    `v12.type`'s base type `K0#I2`. `outerRooted_once_is_scalac` holds against the model's scalac, which
   *    substitutes that prefix directly, as the plugin does; real scalac captures an unstable prefix like `K0`
   *    existentially, a separate difference that the TCK's group G covers.
   */
  def isSelfRooted(link: ThisTypeSubstitution, res: ScType): Boolean = link.target match {
    case _: ScThisType => false
    case c: ScCompoundType =>
      partRoots(c).exists(root => sameClass(root.element, link.seenFromClass))
    case target =>
      val anchor = link.seenFromClass
      spineRootThis(target).exists { root =>
        if (sameClass(root.element, anchor))
          // `SelfRooted`: `res` is the target grafted onto itself, so still rooted in the anchor
          spineRootThis(res).exists(r => sameClass(r.element, anchor))
        else
          // `OuterRooted`: `res` is the target with only its root replaced (`asf_rootedAt`)
          anchor.containingClass != null &&
            SubstitutorInvariants.ownerChainContains(anchor.containingClass, root.element) &&
            selections(res).endsWith(selections(target))
      }
  }

  /** The members selected along a path, outermost first: `List(v12)` for `T2.this.v12.type`. */
  private def selections(tp: ScType): List[PsiNamedElement] = {
    @tailrec
    def go(t: ScType, acc: List[PsiNamedElement]): List[PsiNamedElement] = t match {
      case p @ ScProjectionType(pre, _)                       => go(pre, p.element :: acc)
      case ParameterizedType(p @ ScProjectionType(pre, _), _) => go(pre, p.element :: acc)
      case _                                                  => acc
    }
    go(tp, Nil)
  }

  /**
   * `res` is `target` up to the equivalence of the Lean's `Eqv` (`Relaxations.lean`, retronym/scala-type-system-tck#7):
   * paths followed to the end of their singleton aliases (`val v14: k0.type`, so `v14.I6` is `k0.I6`), and a
   * compound's parts taken as a set (commutative, associative, idempotent). A link that gives its target back
   * up to that equivalence is idempotent up to it (`idempotent_eqv`) and right however often it occurs
   * (`once_is_scalac_eqv`). Nothing weaker is admitted: conformance both ways equates types `Eqv` doesn't
   * (`A with B` and `A` for `A <: B`), and is a type-system call that an invariant check must not make.
   *
   * Paths are canonicalized by [[canonicalizeTarget]], the normalization substitution already applies to the
   * targets it mints. `ScCompoundType.apply` flattens nested compounds, so parts are compared as sets one level
   * deep. Refinements, which the model doesn't have, are compared as `equiv` compares them.
   */
  def sameUpToAliases(res: ScType, target: ScType)(implicit context: Context): Boolean = {
    def same(a: ScType, b: ScType): Boolean = (a, b) match {
      case (a: ScCompoundType, b: ScCompoundType) =>
        def covers(xs: Seq[ScType], ys: Seq[ScType]) = xs.forall(x => ys.exists(same(x, _)))
        def refinement(c: ScCompoundType) =
          ScCompoundType(Seq.empty, forceRefinement = true, c.signatureMap, c.typesMap)(c.projectContext)
        covers(a.components, b.components) && covers(b.components, a.components) &&
          (a.signatureMap.isEmpty && a.typesMap.isEmpty && b.signatureMap.isEmpty && b.typesMap.isEmpty ||
            refinement(a).equiv(refinement(b)))
      case _ =>
        val (ca, cb) = (canonicalizeTarget(a), canonicalizeTarget(b))
        ca == cb || ca.equiv(cb)
    }
    same(res, target)
  }

  /**
   * Collapse a non-canonical spelling of a singleton path (`global.analyzer.global` for
   * `global`) where it is minted: when a substitutor is built, and when a substitution
   * rebuilds a projection over a rewritten prefix. Otherwise resolution feeds fresh
   * spellings back in as substitution targets and the paths compound
   * (`analyzer.global.analyzer.global...`) until the no-self-embedding rule cuts them off.
   */
  def canonicalizeTarget(tp: ScType): ScType =
    if (!ScProjectionType.mayCollapse(tp)) tp
    else if (isPlainPath(tp)) canonicalizeCached(tp)
    else canonicalize(tp)

  /**
   * Cached because canonicalizing a path re-canonicalizes its prefix through the uncached
   * `designatorSingletonType`, and a nested canonicalization starts with fresh fuel: without the
   * cache the cost grows exponentially in path depth. Context-free: `collapseSingletonPath`
   * without `throughAliases` doesn't consult opaque aliases. Only plain paths are cached, so no key
   * holds an inference variable.
   */
  private val canonicalizeCached: ScType => ScType =
    cached("canonicalizeTarget", ModTracker.anyScalaPsiChange, (tp: ScType) => canonicalize(tp))

  private def canonicalize(tp: ScType): ScType =
    TypeRecursionGuard.nestedSubstitution(tp, s"canonicalizing $tp")(ScProjectionType.collapseSingletonPath(tp, throughAliases = false))

  /** A path rooted in a this-type or a designator: `C.this.a.b`, `o.a.b`, `a.b`. */
  @tailrec
  private def isPlainPath(tp: ScType): Boolean = tp match {
    case proj: ScProjectionType              => isPlainPath(proj.projected)
    case _: ScThisType | _: ScDesignatorType => true
    case _                                   => false
  }
}
