package org.jetbrains.plugins.scala.lang.typeSystemTck

import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScCompoundType, ScType, ScTypeExt}
import org.junit.Assert

import scala.collection.mutable.ArrayBuffer

/**
 * Runs the scala-type-system-tck corpus against the IntelliJ PSI type system,
 * the way [[org.jetbrains.plugins.scala.lang.typeSystemTck.TckCorpus]] describes,
 * and compares conformance and base type sequences against the scalac-generated
 * goldens. The reference oracle lives in ~/code/scala-type-system-tck.
 *
 * Iterate with (in the sbt shell, after `packageArtifact` once):
 *   testOnly org.jetbrains.plugins.scala.lang.typeSystemTck.TypeSystemTckTest
 *
 * Mode: STRICT by default — every dimension (conformance, baseTypeSeq, baseClasses,
 * termType, baseType) fails the test on divergence from scalac, EXCEPT the small set
 * of known representation seams registered in [[Deferred]]. The deferred set is a
 * two-way pin: a NEW diff outside it fails (regression), and a deferred case that
 * stops diverging ALSO fails (progression) — forcing it to be un-deferred so we never
 * silently lose ground. Pass `-Dscala.tck.lenient=true` to downgrade the soft
 * dimensions to report-only while iterating locally.
 *
 * Iterate: `testOnly org.jetbrains.plugins.scala.lang.typeSystemTck.TypeSystemTckTest`
 */
class TypeSystemTckTest extends ScalaLightCodeInsightFixtureTestCase {

  // Local-iteration escape hatch: report diffs without failing on the soft dimensions
  // (baseTypeSeq / baseClasses / termType). Conformance and baseType stay hard always.
  private val lenient: Boolean = java.lang.Boolean.getBoolean("scala.tck.lenient")

  /**
   * Known, deferred scalac-vs-PSI divergences, keyed `"<entryId>/<queryName>"`.
   * These are representation/convention seams in `baseTypeSeq` / `baseClasses`, NOT
   * conformance bugs (every `<:<` row passes; `baseType`, the merge primitive, also
   * passes). They are pinned so the suite is otherwise strict. If you make one of
   * these pass, the test will fail with a "progression" message — delete it from here.
   *
   * Groups (see PR #5):
   *  A. Empty `baseTypeSeq` for non-class type forms (existential / singleton /
   *     literal / primitive): the PSI probe doesn't widen to the underlying before
   *     reading supers. Conformance over these forms is correct.
   *  B. Same-symbol merge stored as an INTERSECTION in scalac's seq (`Box[Dog] with
   *     Box[Animal]`) but as the reduced/merged form in PSI; plus `Dog with Cat` vs
   *     `Cat with Dog` arg-order convention. `baseType` (the glb) matches.
   *  C. Refinement dropped from a base in the seq (`B{type T = Repro}` -> `B`).
   *  D. Self-type contribution missing from `baseClasses` linearization order.
   */
  private object Deferred {
    val baseTypeSeq: Set[String] = Set(
      "05-refinement-through-projection/Combined", // C: refinement base dropped
      "07-same-symbol-merge/BoxMerge",             // B: intersection-vs-merge
      "07-same-symbol-merge/SinkMerge",            // B
      "10-existentials/BoxWild",                   // A: existential, empty actual
      "10-existentials/BoxAnimalWild",             // A
      "12-singleton-literal-path/DogSingleton",    // A: singleton, empty actual
      "12-singleton-literal-path/Lit",             // A: literal, empty actual
      "15-top-bottom-valueclass/Int",              // A: primitive, empty actual
      "18-multipath-base-type/LR",                 // B: arg-order / merge
      "18-multipath-base-type/LRS",                // B
    )
    val baseClasses: Set[String] = Set(
      "04-self-type-path-dependent/AnimalBoxThis", // D: self-type base order
    )
  }

  def testCorpus(): Unit = {
    val entries = TckCorpus.load()
    Assert.assertTrue("no corpus entries found", entries.nonEmpty)

    val report = new ArrayBuffer[String]()
    val conformanceFailures = new ArrayBuffer[String]()
    val btsFailures = new ArrayBuffer[String]()
    val bcFailures = new ArrayBuffer[String]()
    val ttFailures = new ArrayBuffer[String]()
    val bfFailures = new ArrayBuffer[String]()
    // `<entryId>/<queryName>` keys of the cases that diverged this run, for the
    // strict + progression check against [[Deferred]].
    val btsDiffKeys = scala.collection.mutable.Set.empty[String]
    val bcDiffKeys = scala.collection.mutable.Set.empty[String]
    val ttDiffKeys = scala.collection.mutable.Set.empty[String]

    entries.foreach { entry =>
      report += s"\n## ${entry.id} — ${entry.description}"
      val (resolved, terms) = resolveTypes(entry)

      // --- conformance (hard) ---
      entry.conformance.foreach { q =>
        val a = resolved(q.lhs)
        val b = resolved(q.rhs)
        val holds = a.conforms(b)
        val ok = holds == q.expect
        report += f"  ${if (ok) "ok  " else "FAIL"} ${q.lhs} <:< ${q.rhs} = $holds (expected ${q.expect})"
        if (!ok)
          conformanceFailures += s"[${entry.id}] ${q.lhs} <:< ${q.rhs}: expected ${q.expect}, PSI says $holds"
      }

      // --- base type sequence (set comparison vs golden) ---
      // scalac's baseTypeSeq is reflexive (bt0 = T) and ends with scala.Any;
      // IntelliJ's `BaseTypes.get` yields only proper, class-extractable base
      // types. Compare like for like: the golden's *proper supers* = the golden
      // minus its head (the type itself) and minus scala.Any.
      entry.baseTypeSeqQueries.foreach { name =>
        val tp = resolved(name)
        val actualKeys = BaseTypes.get(tp).map(render).map(normalizeKey).toSet
        val goldenOrdered = entry.goldenBaseTypeSeq.getOrElse(name, Seq.empty)
        val properSupers = goldenOrdered.drop(1).filterNot(_ == "scala.Any").map(normalizeKey).toSet

        val missing = properSupers -- actualKeys // scalac has it, PSI doesn't
        val extra = actualKeys -- properSupers    // PSI has it, scalac doesn't
        if (missing.isEmpty && extra.isEmpty) {
          report += s"  ok   baseTypeSeq($name) {${actualKeys.size} proper supers}"
        } else {
          report += s"  DIFF baseTypeSeq($name):"
          report += s"         golden supers: ${properSupers.toList.sorted.mkString(", ")}"
          report += s"         actual:        ${actualKeys.toList.sorted.mkString(", ")}"
          if (missing.nonEmpty) report += s"         missing: ${missing.toList.sorted.mkString(", ")}"
          if (extra.nonEmpty) report += s"         extra:   ${extra.toList.sorted.mkString(", ")}"
          btsFailures += s"[${entry.id}] baseTypeSeq($name): missing=$missing extra=$extra"
          btsDiffKeys += s"${entry.id}/$name"
        }
      }

      // --- baseClasses (linearization) — ORDER matters (SPEC §2) ---
      // Compare IntelliJ's MixinNodes.linearization against scalac's baseClasses
      // as ordered lists, dropping conventions that differ: scalac's `<refinement>`
      // head for compound types and the trailing `scala.Any`.
      entry.baseTypeSeqQueries.foreach { name =>
        val tp = resolved(name)
        val actual = linearizationNames(tp)
        val golden = entry.goldenBaseClasses.getOrElse(name, Seq.empty)
          .map(bcName).filterNot(n => n == "scala.Any" || n == "<refinement>").toList
        if (golden.nonEmpty) {
          if (actual == golden) {
            report += s"  ok   baseClasses($name) [${actual.size}]"
          } else {
            report += s"  DIFF baseClasses($name) (order-sensitive):"
            report += s"         golden: ${golden.mkString(", ")}"
            report += s"         actual: ${actual.mkString(", ")}"
            bcFailures += s"[${entry.id}] baseClasses($name): golden=$golden actual=$actual"
            bcDiffKeys += s"${entry.id}/$name"
          }
        }
      }

      // --- term types (member resolution / asSeenFrom / ThisTypeSubstitution) ---
      // The inferred type of `val __t_<name> = <expr>` vs scalac's. Path prefixes
      // matter here (Impl.this.Tree vs Api#Tree), so `#` is NOT collapsed.
      entry.termTypeQueries.foreach { d =>
        val golden = entry.goldenTermTypes.getOrElse(d.name, "")
        if (golden.nonEmpty) {
          val actual = terms.get(d.name).map(render).getOrElse("<unresolved>")
          if (normalizeKey(actual) == normalizeKey(golden)) {
            report += s"  ok   termType(${d.name}) = $actual"
          } else {
            report += s"  DIFF termType(${d.name}):"
            report += s"         golden: $golden"
            report += s"         actual: $actual"
            ttFailures += s"[${entry.id}] termType(${d.name}): golden=$golden actual=$actual"
            ttDiffKeys += s"${entry.id}/${d.name}"
          }
        }
      }

      // --- baseType(prefix, clazz) — the merge primitive (glb), directly ---
      entry.baseTypeQueries.foreach { q =>
        val golden = entry.goldenBaseTypes.getOrElse(q.name, "")
        if (golden.nonEmpty) {
          val pre = resolved(q.prefix)
          val actual = resolved(q.clazz).extractClass
            .flatMap(c => BaseTypes.baseType(pre, c)).map(render).getOrElse("<none>")
          if (withKey(actual) == withKey(golden)) {
            report += s"  ok   baseType(${q.name}) = $actual"
          } else {
            report += s"  DIFF baseType(${q.name}):"
            report += s"         golden: $golden"
            report += s"         actual: $actual"
            bfFailures += s"[${entry.id}] baseType(${q.name}): golden=$golden actual=$actual"
          }
        }
      }
    }

    println(report.mkString("\n"))
    println(s"\n=== TCK: ${conformanceFailures.size} conformance failure(s), " +
      s"${btsFailures.size} baseTypeSeq diff(s), ${bcFailures.size} baseClasses diff(s), " +
      s"${ttFailures.size} termType diff(s), ${bfFailures.size} baseType diff(s) ===")

    // --- conformance & baseType: HARD, no deferrals (the merge primitive and the
    //     human ground truth must never diverge) ---
    if (conformanceFailures.nonEmpty)
      Assert.fail("Conformance divergences from scalac:\n" + conformanceFailures.mkString("\n"))
    if (bfFailures.nonEmpty)
      Assert.fail("baseType (merge) divergences from scalac:\n" + bfFailures.mkString("\n"))

    // --- baseTypeSeq / baseClasses / termType: STRICT by default, two-way against
    //     the deferred registry. Lenient mode reports only. ---
    val problems = new ArrayBuffer[String]()

    def strictDimension(dim: String, actualDiffs: Set[String],
                        deferred: Set[String], details: Iterable[String]): Unit = {
      val regressions = (actualDiffs -- deferred).toList.sorted   // new, un-pinned diffs
      val progressions = (deferred -- actualDiffs).toList.sorted  // pinned but now passing
      if (regressions.nonEmpty)
        problems += s"$dim: NEW divergence(s) from scalac (regression) — fix, or add to Deferred:\n" +
          regressions.map("  " + _).mkString("\n") +
          "\n  details:\n" + details.map("    " + _).mkString("\n")
      if (progressions.nonEmpty)
        problems += s"$dim: deferred case(s) no longer diverge (progression!) — remove from Deferred:\n" +
          progressions.map("  " + _).mkString("\n")
    }

    if (lenient) {
      if (btsFailures.nonEmpty) println(s"[lenient] baseTypeSeq diffs:\n${btsFailures.mkString("\n")}")
      if (bcFailures.nonEmpty) println(s"[lenient] baseClasses diffs:\n${bcFailures.mkString("\n")}")
      if (ttFailures.nonEmpty) println(s"[lenient] termType diffs:\n${ttFailures.mkString("\n")}")
    } else {
      strictDimension("baseTypeSeq", btsDiffKeys.toSet, Deferred.baseTypeSeq, btsFailures)
      strictDimension("baseClasses", bcDiffKeys.toSet, Deferred.baseClasses, bcFailures)
      strictDimension("termType", ttDiffKeys.toSet, Set.empty, ttFailures)
      if (problems.nonEmpty)
        Assert.fail(problems.mkString("\n\n"))
    }
  }

  /** IntelliJ's linearization (`MixinNodes.linearization`) as ordered class names. */
  private def linearizationNames(tp: ScType): List[String] = {
    def className(t: ScType): Option[String] =
      t.extractClass.flatMap(c => Option(c.getQualifiedName)).map(bcName)
    // Widen singleton/literal types to their underlying class first (scalac's
    // baseClasses does this; MixinNodes.linearization expects a class/compound).
    val lin: Seq[ScType] = tp.widen.widenIfLiteral match {
      case ct: ScCompoundType => MixinNodes.linearization(ct, addTp = true)
      case w => w.extractClass match {
        case Some(c) => MixinNodes.linearization(c)
        case None    => Seq.empty
      }
    }
    lin.flatMap(className).filterNot(_ == "scala.Any").toList
  }

  /** Class-name key for baseClasses comparison: normalize, then unify the nested
   *  separator (scalac renders `Outer#Inner`, IntelliJ `Outer.Inner`). */
  private def bcName(s: String): String = normalize(s).replace('#', '.')

  /** Compile the wrapped corpus source; read each `__q_` alias's type and each
   *  `__t_` term probe's inferred type. */
  private def resolveTypes(entry: TckCorpus.Entry): (Map[String, ScType], Map[String, ScType]) = {
    configureFromFileText(ScalaFileType.INSTANCE, TckCorpus.wrap(entry))
    val file = getFile.asInstanceOf[ScalaFile]

    val types = file.depthFirst().collect {
      case ta: ScTypeAliasDefinition if ta.name.startsWith(TckCorpus.QueryPrefix) =>
        ta.name.stripPrefix(TckCorpus.QueryPrefix) -> aliasType(entry.id, ta)
    }.toMap

    val terms = file.depthFirst().collect {
      case b: ScBindingPattern if b.name.startsWith(TckCorpus.TermPrefix) =>
        b.name.stripPrefix(TckCorpus.TermPrefix) -> b.`type`().toOption
    }.collect { case (n, Some(t)) => n -> t }.toMap

    entry.types.foreach { d =>
      Assert.assertTrue(s"[${entry.id}] unresolved query type '${d.name}'", types.contains(d.name))
    }
    (types, terms)
  }

  private def aliasType(id: String, ta: ScTypeAliasDefinition): ScType =
    ta.aliasedType match {
      case Right(t)     => t
      case Failure(msg) => throw new AssertionError(s"[$id] could not type '${ta.name}': $msg")
    }

  // --- canonical rendering (SPEC §4), normalized to the TCK form ---

  private def render(tp: ScType): String =
    try normalize(tp.canonicalText)
    catch { case _: Throwable => tp.toString }

  private def normalize(s: String): String =
    s.replace("_root_.", "")
      .replace(s"${TckCorpus.WrapperPkg}.${TckCorpus.WrapperObj}.", "")
      .replace("scala.AnyRef", "java.lang.Object")
      // scalac renders `scala.AnyVal`; IntelliJ's canonicalText renders the short
      // `AnyVal` — canonicalize both to the short form.
      .replace("scala.AnyVal", "AnyVal")

  /** Whitespace-insensitive key so formatting differences don't mask membership. */
  private def normalizeKey(s: String): String = normalize(s).replaceAll("\\s+", "")

  /** Order-insensitive atom key for baseType comparison: drops bracket/`with`
   *  structure and sorts the identifier atoms, so commutative-intersection order
   *  (`Box[Cat with Dog]` vs `Box[Dog with Cat]`) doesn't mask a missing merge
   *  component (which still changes the atom set). */
  private def withKey(s: String): String =
    normalize(s).replaceAll("[\\[\\],{}]", " ").split("(?i)\\bwith\\b|\\s+").filter(_.nonEmpty).sorted.mkString(" ")
}
