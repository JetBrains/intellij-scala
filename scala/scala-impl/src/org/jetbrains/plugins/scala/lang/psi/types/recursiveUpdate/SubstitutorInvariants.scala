package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClass
import org.jetbrains.annotations.{Nullable, TestOnly}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicInteger, LongAdder}
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
    /** C2. `ScSubstitutor(target, anchor)` applied to `target` is the identity. This is the `hp` hypothesis of
     *  `idempotent`, so the link may be re-run on its own output and duplicates dropped. Checked semantically
     *  (the link is applied to its target) rather than by the Lean's sufficient condition "no this-leaf of the
     *  target is on the anchor's owner chain", which `Outer.this.i.type` seen from `Inner` legitimately violates
     *  while still mapping to itself. Only targets that meet the syntactic precondition are checked.
     *
     *  Off by default: applying the link while the substitutor is being built evaluates types out of turn, and
     *  that is observable. With A1 on, `scala/reflect/internal/util/JavaClearable.scala` reports
     *  `JavaClearableCollection[T]` not conforming to `JavaClearable[T]`; with it off, it doesn't. Enable it
     *  explicitly (`.A1=record`) and treat a result that differs from a run without it as an artifact. */
    case object FixedTarget extends Rule("A1", 0, "idempotent_of_fixed", "a this-link maps its own target to itself", Mode.Off)

    /** C3. A substitutor threaded into resolve *state*, and so applied to the types of other references
     *  (`matchClauseSubstitutor`), binds type variables only: no this-link. `stateSafe_preserves_this`. */
    case object StateSafe extends Rule("A2", 1, "stateSafe_preserves_this", "a substitutor threaded into resolve state has no this-links", Mode.Record)

    /** C1, cheap form. At a `followed` junction, when the last this-link of the left operand targets `Q.this`,
     *  the first this-link of the right operand is anchored at `Q`: the running prefix is `Q.this`, so `Q` is the
     *  class whose view the type is now in. Exact only for the `sig_C >> fromType` shape; see the class doc. */
    case object AdjacentAnchors extends Rule("A3", 2, "chain_is_intended", "a this-link following one that targets Q.this is anchored at Q", Mode.Off)

    /** C1, exact form (`wellAnchored`). Fold the this-links of the combined chain: `run := t₁`, then for each later
     *  `(t, a)` require `a == cls(run.widen)` and set `run := (t, a)(run)`. One base-type walk per link. */
    case object RunningAnchor extends Rule("A4", 3, "chain_is_intended", "each this-link is anchored at the class of the running composed prefix", Mode.Off)

    /** Precondition `inView` of `compose`. A link that leaves `D.this` alone although `D` is on its anchor's
     *  owner chain was handed a type that is not in the view its prefix resolves (scalac: `clazz == D` but no
     *  `pre baseType D`): the prefix is wrong or an earlier link was mis-anchored. */
    case object NoLeftover extends Rule("A5", 4, "compose (inView)", "a this-type on the anchor's owner chain is rewritten, not left over", Mode.Record)

    /** `IntelliJ.agrees` applies to anchored walks only. An anchorless link (`seenFromClass == null`) narrows by
     *  inheritance alone, which scalac never does; refinement and synthetic members still take this route. */
    case object Anchored extends Rule("A6", 5, "IntelliJ.agrees", "a this-link has an anchor", Mode.Record)

    /** I4. Under C1–C3 `chain_is_single` says no output needs re-rewriting, so the no-self-embedding brake
     *  ([[ThisTypeSubstitution]]) never fires. Each firing marks a chain outside the model. */
    case object NoReentry extends Rule("I4", 6, "chain_is_single", "the no-self-embedding brake does not fire", Mode.Record)

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

  def samplesOf(rule: Rule): Seq[String] = samples(rule.index).synchronized(samples(rule.index).toSeq)

  def reset(): Unit = {
    counts.foreach(_.set(0))
    samples.foreach(s => s.synchronized(s.clear()))
    StackProfile.reset()
  }

  /** Counts and sample messages per rule, for a harness to print after highlighting a corpus. */
  def report: String = {
    val sb = new StringBuilder("substitutor invariants:\n")
    for (rule <- Rule.all) {
      sb.append(f"  ${rule.id}%-3s ${mode(rule)}%-7s ${count(rule)}%8d  ${rule.statement}\n")
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

  /** A1, at `ScSubstitutor(target, anchor)`. */
  private[recursiveUpdate] def fixedTarget(link: ThisTypeSubstitution, subst: ScSubstitutor): Unit =
    if (enabled(Rule.FixedTarget) && !checking.get) {
      val leaves = thisLeaves(link.target)
      // Only a this-leaf of the target that the walk can reach is at risk (`asf_eq_of_fixed` otherwise).
      // Anchorless links are A6's business and have no `Context` to check under.
      val atRisk =
        leaves.nonEmpty && link.seenFromClass != null && leaves.exists(th => ownerChainContains(link.seenFromClass, th.element))
      if (atRisk) withoutNestedChecks {
        val res = subst(link.target)
        // `==` first (cheap, exact); `equiv` tolerates a re-spelling of the same path (`Obj.v` as a
        // designator or as a projection).
        if (res != link.target && !res.equiv(link.target)(using Context(link.seenFromClass)))
          violated(Rule.FixedTarget, s"[$link] maps its own target to $res")
      }
    }

  /** A6, at `ScSubstitutor(target)` / `ScSubstitutor(target, null)`. */
  private[recursiveUpdate] def anchored(target: ScType): Unit =
    if (enabled(Rule.Anchored)) violated(Rule.Anchored, s"anchorless this-link to $target")

  /** A2, where a substitutor is put into resolve state for other references. */
  def stateSafe(subst: ScSubstitutor, where: String): Unit =
    if (enabled(Rule.StateSafe)) {
      val links = thisLinks(subst)
      if (links.nonEmpty)
        violated(Rule.StateSafe, s"$where carries this-link(s) ${links.mkString(", ")} in $subst")
    }

  /** A5, in `ThisTypeSubstitution` when `th` is left alone. */
  private[recursiveUpdate] def noLeftover(link: ThisTypeSubstitution, th: ScThisType): Unit =
    if (enabled(Rule.NoLeftover) && link.seenFromClass != null && ownerChainContains(link.seenFromClass, th.element))
      violated(Rule.NoLeftover, s"[$link] left $th alone although ${th.element.name} is on the anchor's owner chain")

  /** I4, in `ThisTypeSubstitution` when the no-self-embedding brake refuses a result. */
  private[recursiveUpdate] def noReentry(link: ThisTypeSubstitution, th: ScThisType, refused: ScType): Unit =
    if (enabled(Rule.NoReentry))
      violated(Rule.NoReentry, s"[$link] would embed $th in $refused")

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
