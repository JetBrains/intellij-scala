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
 * Scaffold status:
 *  - Conformance is a HARD assertion against the corpus's human ground truth.
 *  - baseTypeSeq is compared as a SET of rendered types (IntelliJ's
 *    `BaseTypes.get` is unordered — `res.values.toList` off a HashMap — so order
 *    cannot yet be checked; that is the residual SCL issue). Membership diffs are
 *    reported, and fail the test only when `-Dscala.tck.strictBts=true`.
 */
class TypeSystemTckTest extends ScalaLightCodeInsightFixtureTestCase {

  private val strictBts: Boolean = java.lang.Boolean.getBoolean("scala.tck.strictBts")
  private val strictBc: Boolean = java.lang.Boolean.getBoolean("scala.tck.strictBc")
  private val strictTt: Boolean = java.lang.Boolean.getBoolean("scala.tck.strictTt")
  private val strictBf: Boolean = java.lang.Boolean.getBoolean("scala.tck.strictBf")

  def testCorpus(): Unit = {
    val entries = TckCorpus.load()
    Assert.assertTrue("no corpus entries found", entries.nonEmpty)

    val report = new ArrayBuffer[String]()
    val conformanceFailures = new ArrayBuffer[String]()
    val btsFailures = new ArrayBuffer[String]()
    val bcFailures = new ArrayBuffer[String]()
    val ttFailures = new ArrayBuffer[String]()
    val bfFailures = new ArrayBuffer[String]()

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

    if (conformanceFailures.nonEmpty)
      Assert.fail("Conformance divergences from scalac:\n" + conformanceFailures.mkString("\n"))
    if (strictBts && btsFailures.nonEmpty)
      Assert.fail("baseTypeSeq divergences from scalac:\n" + btsFailures.mkString("\n"))
    if (strictBc && bcFailures.nonEmpty)
      Assert.fail("baseClasses (linearization) divergences from scalac:\n" + bcFailures.mkString("\n"))
    if (strictTt && ttFailures.nonEmpty)
      Assert.fail("termType divergences from scalac:\n" + ttFailures.mkString("\n"))
    // baseType is the direct merge primitive — make it a HARD assertion.
    if (bfFailures.nonEmpty)
      Assert.fail("baseType (merge) divergences from scalac:\n" + bfFailures.mkString("\n"))
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
