package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClass
import org.jetbrains.annotations.{Nullable, TestOnly}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, Context, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicInteger, LongAdder}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Runtime checks of the structural constraints under which a chain of [[ThisTypeSubstitution]] links is one
 * `asSeenFrom`, as stated and proved in the Lean model of retronym/scala-type-system-tck#3
 * (`lean/AsSeenFrom/Chain.lean`). Each [[Rule]] names the theorem whose hypothesis it checks; a violation means
 * the chain is outside the model, not that its result is necessarily wrong.
 *
 * Link order. A substitutor applies its updates in array order: `a.followed(b)` is `b ∘ a`. Both orders of
 * use-site link and signature substitutor occur. `ScalaResolveState.substitutorWithThisType(owner(m))` PREPENDS
 * `(fromType, owner(m))`, so there the use-site link runs first, on the declared type, and `sig_C(m)`'s
 * `(C.this, owner(m))` runs after it on whatever it left alone. `TypeDefinitionMembers.processDeclarations`
 * puts `sig_C(m)` BEFORE the state's substitutor, so a this-link that arrives through resolve state
 * (`BaseProcessor` on a self type or a value projection, `ScProjectionType.actual`) runs after the signature
 * substitutor, anchored at `owner(m)` rather than at `C`: the `sig_C >> fromType` shape of the design note.
 * The C1 rules ([[Rule.AdjacentAnchors]], [[Rule.RunningAnchor]]) fire on both shapes by the hundreds of
 * thousands over the type-inference suites, so they are off by default; treat them as a census, not a gate.
 *
 * Modes. Every rule is `off` in production. In unit tests the cheap rules default to `record` (count, keep a
 * bounded set of sample messages; see [[report]]), the chain-walking ones to `off`. Override globally with
 * `-Dscala.types.substitutorInvariants=off|record|fail` or per rule with
 * `-Dscala.types.substitutorInvariants.A1=fail`; `fail` throws [[SubstitutorInvariantViolation]] at the site.
 *
 * Stacks. `-Dscala.types.substitutorInvariants.stacks=<depth>` additionally records, per rule, the unique call
 * paths into each violation as a trie of frames (innermost first, plumbing and library frames dropped), so the
 * report says how many distinct plugin lines mint or apply the offending links and which resolve routes reach
 * them. See [[StackProfile]].
 */
object SubstitutorInvariants {

  private val LOG = Logger.getInstance(getClass)

  val PropertyPrefix = "scala.types.substitutorInvariants"

  sealed trait Mode
  object Mode {
    case object Off    extends Mode
    case object Record extends Mode
    case object Fail   extends Mode

    def parse(s: String): Option[Mode] = s.trim.toLowerCase match {
      case "off"             => Some(Off)
      case "record" | "on"   => Some(Record)
      case "fail" | "assert" => Some(Fail)
      case _                 => None
    }
  }

  sealed abstract class Rule(val id: String, val index: Int, val theorem: String, val statement: String, val defaultInTests: Mode) {
    override def toString: String = s"$id ($theorem): $statement"
  }

  object Rule {
    /** C2. Every this-link may be applied as often as the chain holds it. A link is one of two kinds:
     *
     *  - fixed: `ScSubstitutor(target, anchor)` applied to `target` is the identity, the `hp` hypothesis of
     *    `idempotent`, so the link may be re-run on its own output. Checked semantically (the link is applied to
     *    its target) rather than by the Lean's sufficient condition "no this-leaf of the target is on the anchor's
     *    owner chain", which `Outer.this.i.type` seen from `Inner` legitimately violates while still mapping to
     *    itself;
     *  - self-rooted (`SelfRooted`): the target is a path rooted in the anchor's own this-type, as in
     *    `UnitScanner.this -> UnitScanner.this.parensAnalyzer.type` (`ParensAnalyzer extends UnitScanner`). The
     *    `UnitScanner.this` inside the target is the enclosing scanner, not the receiver; one pass leaves it
     *    alone, as scalac's does, and gives scalac's result (`once_is_scalac`), but a second copy of the link
     *    later in the chain rewrites it too (`selfRooted_twice_diverges`). So a self-rooted link may occur at
     *    most once in a chain, which [[selfRootedOnce]] checks at `followed`.
     *
     *  A link of neither kind, or a self-rooted link that a chain holds twice, is a violation. Only targets with
     *  a this-leaf on the anchor's owner chain are at risk (`asf_eq_of_fixed`); others are not checked.
     *
     *  Checked lazily, the first time the walk applies the link, and reported with the line that minted it.
     *  Checked when the substitutor was built, it evaluated types out of turn and changed the result
     *  (`scala/reflect/internal/util/JavaClearable.scala` then reported `JavaClearableCollection[T]` not
     *  conforming to `JavaClearable[T]`); checked lazily, the tests, the TCK and scala/scala's sources give the
     *  same results with it on and off. The duplicate check at `followed` compares links by equality only and
     *  defers to that first use when the link has not been classified yet.
     *
     *  Fails the tests: silent over the type-system tests and scala/scala's sources, where every link checked is
     *  fixed except a handful of self-rooted ones, none duplicated, at no measurable cost. */
    case object FixedTarget extends Rule("A1", 0, "idempotent_of_fixed, once_is_scalac", "a this-link maps its own target to itself, or is self-rooted and occurs once in its chain", Mode.Fail)

    /** C3. A substitutor threaded into resolve *state*, and so applied to the types of other references
     *  (`matchClauseSubstitutor`), binds type variables only: no this-link. `stateSafe_preserves_this`. */
    case object StateSafe extends Rule("A2", 1, "stateSafe_preserves_this", "a substitutor threaded into resolve state has no this-links", Mode.Fail)

    /** C1, cheap form. At a `followed` junction, when the last this-link of the left operand targets `Q.this`,
     *  the first this-link of the right operand is anchored at `Q`: the running prefix is `Q.this`, so `Q` is the
     *  class whose view the type is now in. Exact only for the `sig_C >> fromType` shape; see the class doc. */
    case object AdjacentAnchors extends Rule("A3", 2, "chain_is_intended", "a this-link following one that targets Q.this is anchored at Q", Mode.Off)

    /** C1, exact form (`wellAnchored`). Fold the this-links of the combined chain: `run := t₁`, then for each later
     *  `(t, a)` require `a == cls(run.widen)` and set `run := (t, a)(run)`. One base-type walk per link. */
    case object RunningAnchor extends Rule("A4", 3, "chain_is_intended", "each this-link is anchored at the class of the running composed prefix", Mode.Off)

    /** Precondition `inView` of `compose`. A link left `D.this` alone, `D` is on its anchor's owner chain, and
     *  scalac's `thisTypeAsSeen` (the prefix of `pre baseType clazz`, climbing `clazz`'s owner chain until it is
     *  `D`) rewrites it to something else: the prefix is wrong or an earlier link was mis-anchored. A leftover
     *  that scalac also leaves, or rewrites to `D.this` itself, is not counted.
     *
     *  Off by default: like A1 it evaluates base types while a substitution is running, which can perturb
     *  typing; compare a census run's results with a run without it. */
    case object NoLeftover extends Rule("A5", 4, "compose (inView)", "a this-type on the anchor's owner chain is rewritten as scalac does", Mode.Off)

    /** `IntelliJ.agrees` applies to anchored walks only. Holds by construction: `ScSubstitutor(target, null)` is
     *  empty, since a member with no class owner has no owner chain for `asSeenFrom` to climb. Kept so that the
     *  rule ids of the tally stay stable. */
    case object Anchored extends Rule("A6", 5, "IntelliJ.agrees", "a this-link has an anchor", Mode.Record)

    /** I4. Under C1–C3 `chain_is_single` says no output needs re-rewriting. A census of the results that the
     *  former no-self-embedding brake refused (rooted in an inheritor of the rewritten this-type's class): with
     *  every link anchored they are legitimate single-pass rewrites, so the brake is gone, and a count here is
     *  information, not a violation. */
    case object NoReentry extends Rule("I4", 6, "chain_is_single", "a rewrite's result is not rooted in the rewritten class", Mode.Off)

    val all: Seq[Rule] = Seq(FixedTarget, StateSafe, AdjacentAnchors, RunningAnchor, NoLeftover, Anchored, NoReentry)
  }

  final class SubstitutorInvariantViolation(message: String) extends AssertionError(message)

  // ---- modes -------------------------------------------------------------------------------------------------

  private val modes: Array[Mode] = new Array[Mode](Rule.all.size)

  def mode(rule: Rule): Mode = {
    val m = modes(rule.index)
    if (m ne null) m else resolveMode(rule)
  }

  @inline def enabled(rule: Rule): Boolean = mode(rule) ne Mode.Off

  private def resolveMode(rule: Rule): Mode = {
    val fromProps =
      sys.props.get(s"$PropertyPrefix.${rule.id}").orElse(sys.props.get(PropertyPrefix)).flatMap(Mode.parse)
    val app = ApplicationManager.getApplication
    if (app == null) fromProps.getOrElse(Mode.Off) // don't cache before the application exists
    else {
      val m = fromProps.getOrElse(if (app.isUnitTestMode) rule.defaultInTests else Mode.Off)
      modes(rule.index) = m
      if (m ne Mode.Off) reportOnExit
      m
    }
  }

  @TestOnly
  def setMode(rule: Rule, mode: Mode): Unit = modes(rule.index) = mode

  @TestOnly
  def setAllModes(mode: Mode): Unit = Rule.all.foreach(setMode(_, mode))

  // ---- recording ---------------------------------------------------------------------------------------------

  private val MaxSamples = 32

  private val counts: Array[AtomicInteger] = Array.fill(Rule.all.size)(new AtomicInteger)
  private val samples: Array[mutable.LinkedHashSet[String]] = Array.fill(Rule.all.size)(mutable.LinkedHashSet.empty[String])

  def count(rule: Rule): Int = counts(rule.index).get

  private val fixedTargetChecks = new AtomicInteger
  private val fixedLinks        = new AtomicInteger
  private val selfRootedLinks   = new AtomicInteger
  private val selfRootedSamples = mutable.LinkedHashSet.empty[String]

  /** How many links A1 has checked, so that a silent A1 is known to have run. */
  def fixedTargetChecked: Int = fixedTargetChecks.get

  /** Of those, how many were fixed and how many self-rooted. */
  def fixedTargetFixed: Int = fixedLinks.get
  def fixedTargetSelfRooted: Int = selfRootedLinks.get

  def samplesOf(rule: Rule): Seq[String] = samples(rule.index).synchronized(samples(rule.index).toSeq)

  def reset(): Unit = {
    counts.foreach(_.set(0))
    fixedTargetChecks.set(0)
    fixedLinks.set(0)
    selfRootedLinks.set(0)
    selfRootedSamples.synchronized(selfRootedSamples.clear())
    samples.foreach(s => s.synchronized(s.clear()))
    StackProfile.reset()
  }

  /** Counts and sample messages per rule, for a harness to print after highlighting a corpus. */
  def report: String = {
    val sb = new StringBuilder("substitutor invariants:\n")
    for (rule <- Rule.all) {
      sb.append(f"  ${rule.id}%-3s ${mode(rule)}%-7s ${count(rule)}%8d  ${rule.statement}\n")
      if (rule == Rule.FixedTarget && enabled(rule)) {
        sb.append(s"        ($fixedTargetChecked links checked: $fixedTargetFixed fixed, $fixedTargetSelfRooted self-rooted)\n")
        selfRootedSamples.synchronized(selfRootedSamples.toSeq).foreach(l => sb.append("        self-rooted: ").append(l).append('\n'))
      }
      samplesOf(rule).foreach(s => sb.append("        ").append(s).append('\n'))
      if (StackProfile.depth > 0) StackProfile.render(rule, sb)
    }
    sb.result()
  }

  /**
   * Unique call paths into each violation, as a trie keyed by stack frame, innermost first. Frames of this
   * package (the substitutor engine and these checks) and of `scala.`/`java.` are dropped, so a first-level node
   * is the plugin line that built or applied the link and its subtree is the set of routes that reach it.
   * Recorded with [[StackWalker]] to a fixed depth, no `Throwable`; nodes are lock-free.
   */
  object StackProfile {
    val depth: Int = Integer.getInteger(s"$PropertyPrefix.stacks", 0).intValue

    /** How many first-level nodes (distinct minting/applying sites) to print per rule, and children per node. */
    private val MaxChildren = 12

    final case class Frame(className: String, method: String, line: Int) {
      def render: String = {
        val cls = className.stripPrefix("org.jetbrains.plugins.scala.")
        s"$cls.$method:$line"
      }
    }

    final class Node {
      val count    = new LongAdder
      val children = new ConcurrentHashMap[Frame, Node]
    }

    private val roots: Array[Node] = Array.fill(Rule.all.size)(new Node)

    private val walker = StackWalker.getInstance()

    /** The first plugin frame outside the engine: the line that minted a link. One frame, no `Throwable`. */
    def mintSite(): String =
      walker.walk(_.filter(f => !isNoise(f.getClassName)).findFirst()).map(f => s"${f.getClassName}.${f.getMethodName}:${f.getLineNumber}").orElse("?")

    private val OwnPackage = "org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate."

    private val Extensions = "org.jetbrains.plugins.scala.extensions."

    // Our own engine, the standard libraries, and the plugin's collection helpers (`smartMapWithIndex` etc.)
    // are plumbing between the site and the violation.
    private def isNoise(className: String): Boolean =
      className.startsWith(OwnPackage) || className.startsWith(Extensions) ||
        className.startsWith("scala.") || className.startsWith("java.")

    private[SubstitutorInvariants] def record(rule: Rule): Unit = {
      val frames: java.util.List[Frame] = walker.walk { stream =>
        stream
          .filter(f => !isNoise(f.getClassName))
          .limit(depth.toLong)
          .map[Frame](f => Frame(f.getClassName, f.getMethodName, f.getLineNumber))
          .collect(java.util.stream.Collectors.toList[Frame])
      }
      var node = roots(rule.index)
      node.count.increment()
      frames.forEach { frame =>
        node = node.children.computeIfAbsent(frame, _ => new Node)
        node.count.increment()
      }
    }

    def root(rule: Rule): Node = roots(rule.index)

    /** First-level nodes: the distinct plugin lines a rule's violations were reached through. */
    def sites(rule: Rule): Map[Frame, Long] =
      roots(rule.index).children.asScala.map { case (f, n) => f -> n.count.sum }.toMap

    /** Distinct full paths (leaves of the trie). */
    def paths(rule: Rule): Int = {
      def leaves(n: Node): Int = if (n.children.isEmpty) 1 else n.children.values.asScala.map(leaves).sum
      val r = roots(rule.index)
      if (r.children.isEmpty) 0 else leaves(r)
    }

    private[SubstitutorInvariants] def reset(): Unit = roots.indices.foreach(i => roots(i) = new Node)

    private[SubstitutorInvariants] def render(rule: Rule, sb: StringBuilder): Unit = {
      val r = roots(rule.index)
      if (r.count.sum > 0) {
        sb.append(s"        stacks: ${r.children.size} site(s), ${paths(rule)} distinct path(s), depth $depth\n")
        def go(n: Node, indent: Int): Unit = {
          val kids = n.children.asScala.toSeq.sortBy { case (_, c) => -c.count.sum }
          kids.take(MaxChildren).foreach { case (frame, child) =>
            sb.append("        ").append("  " * indent).append(f"${child.count.sum}%8d  ").append(frame.render).append('\n')
            go(child, indent + 1)
          }
          if (kids.size > MaxChildren)
            sb.append("        ").append("  " * indent).append(s"      …  ${kids.size - MaxChildren} more\n")
        }
        go(r, 0)
      }
    }
  }

  /** `-Dscala.types.substitutorInvariants.reportOnExit=true`: print [[report]] when the JVM exits, to collect the
   *  worklist from a whole test run. */
  private lazy val reportOnExit: Unit =
    if (java.lang.Boolean.getBoolean(s"$PropertyPrefix.reportOnExit"))
      Runtime.getRuntime.addShutdownHook(new Thread(() => System.out.println(report)))

  def violated(rule: Rule, detail: => String): Unit = mode(rule) match {
    case Mode.Off => ()
    case m =>
      reportOnExit
      counts(rule.index).incrementAndGet()
      if (StackProfile.depth > 0) StackProfile.record(rule)
      val message = s"${rule.id} violated (${rule.statement}): $detail"
      val set = samples(rule.index)
      set.synchronized(if (set.size < MaxSamples) set += message)
      m match {
        case Mode.Fail => throw new SubstitutorInvariantViolation(message)
        case _         => LOG.debug(message)
      }
  }

  /** Checks that run a substitution themselves (A1, A4) mint further substitutors; don't check those. */
  private val checking: ThreadLocal[java.lang.Boolean] = ThreadLocal.withInitial(() => java.lang.Boolean.FALSE)

  private def withoutNestedChecks[T](body: => T): T = {
    checking.set(java.lang.Boolean.TRUE)
    try body
    finally checking.set(java.lang.Boolean.FALSE)
  }

  // ---- helpers -----------------------------------------------------------------------------------------------

  private def sameClass(a: PsiClass, b: PsiClass): Boolean =
    a == b || ScEquivalenceUtil.areClassesEquivalent(a, b)

  /** `cls` is `anchor` or one of its enclosing classes: the Lean's `c = d` at some step of the walk. */
  def ownerChainContains(@Nullable anchor: PsiClass, cls: PsiClass): Boolean = {
    var c = anchor
    while (c != null) {
      if (sameClass(c, cls)) return true
      c = c.containingClass
    }
    false
  }

  private def thisLeaves(tp: ScType): Seq[ScThisType] = {
    val buf = mutable.ArrayBuffer.empty[ScThisType]
    tp.visitRecursively {
      case th: ScThisType => buf += th
      case _              =>
    }
    buf.toSeq
  }

  private def viewClass(prefix: ScType, anchor: PsiClass): Option[PsiClass] =
    prefix.widen.extractClass(using Context(anchor))

  private[recursiveUpdate] def thisLinks(subst: ScSubstitutor): Seq[ThisTypeSubstitution] =
    subst.substitutions.iterator.collect { case t: ThisTypeSubstitution => t }.toSeq

  // ---- the checks, called from the sites they guard -----------------------------------------------------------

  // A1's classification of a link, made at its first use.
  private[recursiveUpdate] final val A1Unchecked  = 0
  private[recursiveUpdate] final val A1Fixed      = 1
  private[recursiveUpdate] final val A1SelfRooted = 2
  private[recursiveUpdate] final val A1Neither    = 3

  /** Two equal links in one chain, found before either was classified: reported once, by whichever is classified
   *  first, if it turns out self-rooted. */
  private[recursiveUpdate] final class A1Duplicate(val mintSites: String) extends java.util.concurrent.atomic.AtomicBoolean

  /**
   * A1, at `ScSubstitutor(target, anchor)`. Only syntactic work happens here: a link whose target has a
   * this-leaf on the anchor's owner chain (the only ones at risk, `asf_eq_of_fixed` otherwise) is marked with
   * the frame that minted it. The check itself is deferred to [[fixedTargetOnFirstUse]]: applying the link
   * while the substitutor is being built evaluated types out of turn, which changed the result
   * (`JavaClearable.scala`).
   */
  private[recursiveUpdate] def fixedTarget(link: ThisTypeSubstitution): Unit =
    if (enabled(Rule.FixedTarget) && !checking.get) {
      val leaves = thisLeaves(link.target)
      if (leaves.exists(th => ownerChainContains(link.seenFromClass, th.element)))
        link.a1MintSite = StackProfile.mintSite()
    }

  /** A1, the first time the walk applies `link`: classify it as fixed or self-rooted, else report it. A
   *  self-rooted link already seen twice in one chain is reported now. */
  private[recursiveUpdate] def fixedTargetOnFirstUse(link: ThisTypeSubstitution): Unit =
    if (!checking.get) {
      fixedTargetChecks.incrementAndGet()
      withoutNestedChecks {
        val res = ScSubstitutor(link)(link.target)
        // `==` first (cheap, exact); `equiv` tolerates a re-spelling of the same path (`Obj.v` as a
        // designator or as a projection).
        val kind =
          if (res == link.target || res.equiv(link.target)(using Context(link.seenFromClass))) A1Fixed
          else if (ThisTypeSubstitution.isSelfRooted(link, res)) A1SelfRooted
          else A1Neither
        link.a1Kind = kind
        kind match {
          case A1Fixed =>
            fixedLinks.incrementAndGet()
          case A1SelfRooted =>
            selfRootedLinks.incrementAndGet()
            selfRootedSamples.synchronized(if (selfRootedSamples.size < MaxSamples) selfRootedSamples += s"[$link] (minted at ${link.a1MintSite})")
            val dup = link.a1Duplicate
            if (dup != null && dup.compareAndSet(false, true)) duplicated(link, dup.mintSites)
          case _ =>
            violated(Rule.FixedTarget, s"[$link] maps its own target to $res, and its target is not rooted in ${link.seenFromClass.name}.this (minted at ${link.a1MintSite})")
        }
      }
    }

  private def duplicated(link: ThisTypeSubstitution, mintSites: String): Unit =
    violated(Rule.FixedTarget, s"self-rooted [$link] occurs twice in one chain (minted at $mintSites)")

  /**
   * A1 at `first.followed(second)`: a self-rooted link may occur at most once in a chain. Each operand was checked
   * when it was built, so only a link of `second` equal to one of `first` is new. Only links A1 marked at risk
   * are compared, so this is a scan of a few array slots unless both operands hold one. Classification is not
   * forced here: if neither copy has been used yet, both are tagged and the first use reports.
   */
  private[recursiveUpdate] def selfRootedOnce(first: ScSubstitutor, second: ScSubstitutor): Unit =
    if (enabled(Rule.FixedTarget) && !checking.get) {
      val subs1 = first.substitutions
      val subs2 = second.substitutions
      var j = 0
      while (j < subs2.length) {
        subs2(j) match {
          case b: ThisTypeSubstitution if b.a1MintSite != null && b.a1Kind != A1Fixed =>
            var i = 0
            while (i < subs1.length) {
              subs1(i) match {
                case a: ThisTypeSubstitution if a.a1MintSite != null && a == b =>
                  val kind = if (a.a1Kind != A1Unchecked) a.a1Kind else b.a1Kind
                  val sites = s"${a.a1MintSite} and ${b.a1MintSite}"
                  kind match {
                    case A1SelfRooted => duplicated(b, sites)
                    case A1Unchecked =>
                      val dup = new A1Duplicate(sites)
                      a.a1Duplicate = dup
                      b.a1Duplicate = dup
                    case _ => // fixed: idempotent; neither: reported at its first use
                  }
                case _ =>
              }
              i += 1
            }
          case _ =>
        }
        j += 1
      }
    }

    /** A2, where a substitutor is put into resolve state for other references. */
  def stateSafe(subst: ScSubstitutor, where: String): Unit =
    if (enabled(Rule.StateSafe)) {
      val links = thisLinks(subst)
      if (links.nonEmpty)
        violated(Rule.StateSafe, s"$where carries this-link(s) ${links.mkString(", ")} in $subst")
    }

  /** A5, in `ThisTypeSubstitution` when `th` is left alone. */
  private[recursiveUpdate] def noLeftover(link: ThisTypeSubstitution, th: ScThisType): Unit =
    if (enabled(Rule.NoLeftover) && !checking.get && ownerChainContains(link.seenFromClass, th.element)) withoutNestedChecks {
      scalacThisTypeAsSeen(link.target, link.seenFromClass, th) match {
        case Some(expected) if expected != th && !expected.equiv(th)(using Context(link.seenFromClass)) =>
          violated(Rule.NoLeftover, s"[$link] left $th alone; scalac's walk gives $expected")
        case _ =>
      }
    }

  /** scalac's `AsSeenFromMap.thisTypeAsSeen` for `th`, `None` where it leaves `th` alone. */
  private def scalacThisTypeAsSeen(pre0: ScType, clazz0: PsiClass, th: ScThisType): Option[ScType] = {
    implicit val context: Context = Context(clazz0)

    def prefixOf(tp: ScType): Option[ScType] = tp match {
      case ScProjectionType(pre, _)                       => Some(pre)
      case ParameterizedType(ScProjectionType(pre, _), _) => Some(pre)
      case _                                              => None
    }

    @tailrec
    def loop(pre: ScType, clazz: PsiClass): Option[ScType] =
      if (clazz == null) None
      else if (sameClass(clazz, th.element) &&
        pre.widen.extractClass.exists(c => sameClass(c, clazz) || ScalaPsiUtil.isInheritorDeep(c, clazz))) Some(pre)
      else BaseTypes.baseType(pre, clazz).flatMap(prefixOf) match {
        case Some(prefix) => loop(prefix, clazz.containingClass)
        case None         => None
      }

    loop(pre0, clazz0)
  }

  /** I4, in `ThisTypeSubstitution`, for a result rooted in the rewritten this-type's class. */
  private[recursiveUpdate] def noReentry(link: ThisTypeSubstitution, th: ScThisType, refused: ScType): Unit =
    if (enabled(Rule.NoReentry))
      violated(Rule.NoReentry, s"[$link] embeds $th in $refused")

  /** A3 and A4, at `followed`. A3 looks at the junction only (each operand's own junctions were checked when it
   *  was built); A4 folds the whole combined chain. */
  private[recursiveUpdate] def wellAnchored(first: ScSubstitutor, second: ScSubstitutor, combined: ScSubstitutor): Unit = {
    val a3 = enabled(Rule.AdjacentAnchors)
    val a4 = enabled(Rule.RunningAnchor)
    if ((a3 || a4) && !checking.get) {
      if (a3) {
        for {
          t1 <- thisLinks(first).lastOption
          t2 <- thisLinks(second).headOption
          if t2.seenFromClass != null
        } t1.target match {
          case ScThisType(q) if !sameClass(t2.seenFromClass, q) =>
            violated(Rule.AdjacentAnchors, s"[$t2] follows [$t1] but is not anchored at ${q.name} in $combined")
          case _ =>
        }
      }
      if (a4) withoutNestedChecks {
        val links = thisLinks(combined)
        if (links.size > 1) {
          var run = links.head.target
          for (link <- links.tail if link.seenFromClass != null) {
            viewClass(run, link.seenFromClass) match {
              case Some(cls) if !sameClass(cls, link.seenFromClass) =>
                violated(Rule.RunningAnchor, s"[$link] is anchored at ${link.seenFromClass.name} but the running prefix $run is a ${cls.name} in $combined")
              case _ =>
            }
            run = ScSubstitutor(link)(run)
          }
        }
      }
    }
  }
}
