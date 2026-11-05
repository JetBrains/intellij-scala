package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScCompoundType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.SubstitutorInvariants.{Mode, Rule}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.junit.Assert._

/**
 * [[SubstitutorInvariants]] in `record` mode over small fixtures: the rules that must be silent on well-formed
 * selections are, and the detector catches the one known violation (the match-clause substitutor carrying a
 * this-link, §5.2 of the design note in retronym/scala-type-system-tck#3).
 */
class SubstitutorInvariantsTest extends ScalaLightCodeInsightFixtureTestCase {

  private var savedModes: Map[Rule, Mode] = Map.empty

  override def setUp(): Unit = {
    super.setUp()
    savedModes = Rule.all.map(r => r -> SubstitutorInvariants.mode(r)).toMap
    SubstitutorInvariants.setAllModes(Mode.Record)
    SubstitutorInvariants.reset()
  }

  override def tearDown(): Unit = {
    savedModes.foreach { case (r, m) => SubstitutorInvariants.setMode(r, m) }
    super.tearDown()
  }

  private def typeEverything(text: String): ScalaFile = {
    configureFromFileText(ScalaFileType.INSTANCE, text)
    val file = getFile.asInstanceOf[ScalaFile]
    file.depthFirst().foreach {
      case e: ScExpression   => e.`type`()
      case te: ScTypeElement => te.`type`()
      case _                 =>
    }
    file
  }

  private def assertSilent(rules: Rule*): Unit =
    rules.foreach { rule =>
      assertEquals(s"$rule fired:\n${SubstitutorInvariants.samplesOf(rule).mkString("\n")}", 0, SubstitutorInvariants.count(rule))
    }

  def testInnerClassSelectionsAreFixedAndAnchored(): Unit = {
    typeEverything(
      """class Outer {
        |  type T
        |  class Inner { def f: Outer.this.T = ???; def g: Inner = this }
        |  val i = new Inner
        |  def viaThis: T = this.i.f
        |  def viaPath(o: Outer): o.T = o.i.f
        |  def nested: Inner = i.g.g
        |}
        |""".stripMargin)
    assertTrue("A1 checked no link", SubstitutorInvariants.fixedTargetChecked > 0)
    assertSilent(Rule.FixedTarget, Rule.NoReentry, Rule.StateSafe)
  }

  def testCakeSelectionsAreFixedAndAnchored(): Unit = {
    typeEverything(
      """trait Symbols { self: Universe =>
        |  class Symbol { def tpe: Type = ??? ; def owner: Symbol = ??? }
        |}
        |trait Types { self: Universe =>
        |  class Type { def typeSymbol: Symbol = ??? }
        |}
        |trait Definitions { self: Universe =>
        |  def AnyClass: Symbol = ???
        |  def x: Type = AnyClass.tpe
        |}
        |trait Universe extends Symbols with Types with Definitions
        |class Global extends Universe
        |object Use {
        |  val g = new Global
        |  val s: g.Symbol = g.AnyClass.tpe.typeSymbol.owner
        |  def h(u: Universe): u.Type = u.AnyClass.tpe
        |}
        |""".stripMargin)
    assertSilent(Rule.FixedTarget, Rule.NoReentry, Rule.StateSafe)
  }

  // `Scanners.scala`: `parensAnalyzer.balance` inside `UnitScanner` views an inherited member through
  // `UnitScanner.this -> UnitScanner.this.parensAnalyzer.type`, a self-rooted link: right once
  // (`once_is_scalac`), wrong twice (`selfRooted_twice_diverges`).
  private val unitScanner =
    """class UnitScanner {
      |  type T
      |  def balance: T = ???
      |  lazy val parensAnalyzer = new ParensAnalyzer
      |  def use = parensAnalyzer.balance
      |}
      |class ParensAnalyzer extends UnitScanner
      |""".stripMargin

  def testSelfRootedLinkOnceIsAllowed(): Unit = {
    typeEverything(unitScanner)
    assertTrue("A1 classified no link as self-rooted", SubstitutorInvariants.fixedTargetSelfRooted > 0)
    assertSilent(Rule.FixedTarget)
  }

  def testSelfRootedLinkTwiceIsReported(): Unit = {
    val file = typeEverything(unitScanner)
    val us   = file.depthFirst().collectFirst { case c: ScClass if c.name == "UnitScanner" => c }.get
    val pa   = file.depthFirst().collectFirst { case b: ScBindingPattern if b.name == "parensAnalyzer" => b }.get
    val target = ScProjectionType(ScThisType(us), pa)
    SubstitutorInvariants.reset()

    val once = ScSubstitutor(target, us)
    assertEquals(target, once(ScThisType(us)))
    assertSilent(Rule.FixedTarget)
    assertEquals(1, SubstitutorInvariants.fixedTargetSelfRooted)

    // The first copy is already classified: reported at `followed`.
    once.followed(ScSubstitutor(target, us))
    assertEquals(1, SubstitutorInvariants.count(Rule.FixedTarget))

    // Neither copy classified yet: reported at the first use, once.
    val twice = ScSubstitutor(target, us).followed(ScSubstitutor(target, us))
    assertEquals(1, SubstitutorInvariants.count(Rule.FixedTarget))
    twice(ScThisType(us))
    assertEquals(SubstitutorInvariants.samplesOf(Rule.FixedTarget).mkString("\n"), 2, SubstitutorInvariants.count(Rule.FixedTarget))
  }

  private implicit def projectContext: ProjectContext = getProject

  private def aliased(file: ScalaFile, name: String): ScType =
    file.depthFirst().collectFirst { case ta: ScTypeAliasDefinition if ta.name == name => ta }.get.aliasedType.toOption.get

  // `T2.this -> T0 with T3.this.type with T3.this.I5`, minted by `MixinNodes.SuperTypesData` for the members of
  // `T2` in a refinement of `T3.this` (`T3 <: T2`): a part rooted in an inheritor's this-type. scalac's walk
  // leaves `T3.this` alone; only the plugin's superclass early exit moves it, outside the Lean model of
  // retronym/scala-type-system-tck#7. Not admitted: reported.
  def testCompoundInheritorRootedLinkIsReported(): Unit = {
    val file = typeEverything(
      """object P {
        |  trait T0
        |  trait T2 { trait I5; type M3 }
        |  trait T3 extends T2 {
        |    type Q = (T0 with this.type) with this.I5 { type M3 = Any }
        |  }
        |}
        |""".stripMargin)
    implicit val context: Context = Context(file)
    assertFalse(aliased(file, "Q").conforms(Nothing))
    val samples = SubstitutorInvariants.samplesOf(Rule.FixedTarget)
    assertTrue(samples.mkString("\n"), samples.exists(_.contains("T3.this")))
  }

  // `T0.this -> K0 with T1.this.I1` (`K0 <: T1 <: T0`), the same with the inheritor's part under a class part.
  def testCompoundInheritorRootedLinkUnderClassPartIsReported(): Unit = {
    val file = typeEverything(
      """object P {
        |  trait T0 { trait I1; type M3 }
        |  trait T1 extends T0 {
        |    type Q = K0 with I1 { type M3 = Any }
        |  }
        |  trait K0 extends T1
        |}
        |""".stripMargin)
    implicit val context: Context = Context(file)
    assertFalse(Any.conforms(aliased(file, "Q")))
    val samples = SubstitutorInvariants.samplesOf(Rule.FixedTarget)
    assertTrue(samples.mkString("\n"), samples.exists(_.contains("T1.this")))
  }

  private val compoundAnchorRooted =
    """object P {
      |  trait T1 {
      |    type M3
      |    type Q1 = (T1 { type M3 = Any }) with this.M3
      |  }
      |}
      |""".stripMargin

  // `T1.this -> T1 with T1.this.M3`: a compound target with a part rooted in the anchor's own this-type, right
  // once (`partRooted_once_is_scalac`).
  def testCompoundAnchorRootedLinkOnceIsAllowed(): Unit = {
    val file = typeEverything(compoundAnchorRooted)
    implicit val context: Context = Context(file)
    assertFalse(Any.conforms(aliased(file, "Q1")))
    assertTrue(Nothing.conforms(aliased(file, "Q1")))
    assertFalse(aliased(file, "Q1").conforms(Nothing))
    assertTrue("A1 classified no link as self-rooted", SubstitutorInvariants.fixedTargetSelfRooted > 0)
    assertSilent(Rule.FixedTarget)
  }

  // The same link twice grows `T1 with (T1 with T1.this.M3)#M3` (`partRooted_twice_diverges`), the duplicate
  // that `ScProjectionType.processType` used to add for a compound prefix.
  def testCompoundAnchorRootedLinkTwiceIsReported(): Unit = {
    val file = typeEverything(compoundAnchorRooted)
    val t1   = file.depthFirst().collectFirst { case c: ScTemplateDefinition if c.name == "T1" => c }.get
    val m3   = file.depthFirst().collectFirst { case ta: ScTypeAlias if ta.name == "M3" => ta }.get
    val target = ScCompoundType(Seq(ScDesignatorType(t1), ScProjectionType(ScThisType(t1), m3)))
    SubstitutorInvariants.reset()

    val once = ScSubstitutor(target, t1)
    assertEquals(target, once(ScThisType(t1)))
    assertSilent(Rule.FixedTarget)
    assertEquals(1, SubstitutorInvariants.fixedTargetSelfRooted)

    val twice = ScSubstitutor(target, t1).followed(ScSubstitutor(target, t1))
    assertNotEquals(target, twice(ScThisType(t1)))
    assertEquals(SubstitutorInvariants.samplesOf(Rule.FixedTarget).mkString("\n"), 1, SubstitutorInvariants.count(Rule.FixedTarget))
  }

  // `T0.this -> T0 with a15.I6 with v14.I6` maps its target to `T0 with k0.I6`, the same compound with its
  // singleton paths canonicalized (`a15: v14.type`, `v14: k0.type`): fixed up to equivalence.
  def testCompoundTargetRespelledIsFixed(): Unit = {
    val file = typeEverything(
      """object P {
        |  trait T0 { type M3 }
        |  trait T2 { trait I6 }
        |  trait K0 extends T2
        |  val k0: K0 = ???
        |  val v14: k0.type = ???
        |  val a15: v14.type = ???
        |  type Q = (T0 with a15.I6 { type M3 = Any }) with v14.I6
        |}
        |""".stripMargin)
    implicit val context: Context = Context(file)
    assertFalse(Any.conforms(aliased(file, "Q")))
    assertSilent(Rule.FixedTarget)
  }

  // `sameUpToAliases` is `Eqv` of the Lean model: singleton aliases and compound parts as a set, not conformance.
  def testSameUpToAliasesIsNotConformance(): Unit = {
    val file = typeEverything(
      """object P {
        |  trait T0
        |  trait T2 { trait I6 }
        |  trait K0 extends T2
        |  trait A extends B
        |  trait B
        |  val k0: K0 = ???
        |  val v14: k0.type = ???
        |  val a15: v14.type = ???
        |  type R1 = T0 with a15.I6 with v14.I6
        |  type R2 = k0.I6 with T0
        |  type R3 = A with B
        |  type R4 = A
        |}
        |""".stripMargin)
    implicit val context: Context = Context(file)
    val Seq(r1, r2, r3, r4) = Seq("R1", "R2", "R3", "R4").map(aliased(file, _))
    assertTrue(ThisTypeSubstitution.sameUpToAliases(r1, r2))
    assertTrue(ThisTypeSubstitution.sameUpToAliases(r2, r1))
    assertTrue(r3.conforms(r4) && r4.conforms(r3))
    assertFalse(ThisTypeSubstitution.sameUpToAliases(r3, r4))
  }

  // `I2.this -> T2.this.v12.type` with `v12: K0#I5`: the value's `T2` is another instance than `T2.this`, so
  // the link rewrites the `T2.this` of its own target (scalac too, capturing the unstable `K0`
  // existentially). Outer-rooted, right once.
  def testOuterRootedLinkOnceIsAllowed(): Unit = {
    val file = typeEverything(
      """object P {
        |  trait T1 { trait I1 }
        |  trait T2 extends T1 {
        |    trait I2 { val v13: T2 = ??? }
        |    val v12: K0#I5 = ???
        |    type Q = this.v12.v13.I1
        |  }
        |  trait T3 extends T2 { trait I5 extends I2 }
        |  trait K0 extends T3
        |}
        |""".stripMargin)
    implicit val context: Context = Context(file)
    assertTrue(aliased(file, "Q").conforms(Any))
    assertTrue("A1 classified no link as self-rooted", SubstitutorInvariants.fixedTargetSelfRooted > 0)
    assertSilent(Rule.FixedTarget)
  }

  // The leak of §5.2: the extractor's this-links must not reach the substitutor that the resolver threads to
  // every reference in the case body; `doForMatchClause` keeps the bindings alone.
  def testMatchClauseSubstitutorHasNoThisLink(): Unit = {
    typeEverything(
      """class C {
        |  object E { def unapply(x: Any): Option[Int] = Some(1) }
        |  def m: Int = 0
        |  def use(a: Any): Int = a match {
        |    case E(n) => n + m
        |    case _    => 0
        |  }
        |}
        |""".stripMargin)
    assertSilent(Rule.StateSafe)
  }

  def testStateSafeDetectsThisLinkDirectly(): Unit = {
    val file = typeEverything("class C { def m: Int = 0 }")
    val c    = file.depthFirst().collectFirst { case td: ScTemplateDefinition => td }.get
    SubstitutorInvariants.reset()

    SubstitutorInvariants.stateSafe(ScSubstitutor.empty, "test")
    assertSilent(Rule.StateSafe)

    SubstitutorInvariants.stateSafe(ScSubstitutor(ScThisType(c), c), "test")
    assertEquals(1, SubstitutorInvariants.count(Rule.StateSafe))
  }

  def testFailModeThrows(): Unit = {
    val file = typeEverything("class C { def m: Int = 0 }")
    val c    = file.depthFirst().collectFirst { case td: ScTemplateDefinition => td }.get
    SubstitutorInvariants.setMode(Rule.StateSafe, Mode.Fail)
    try {
      SubstitutorInvariants.stateSafe(ScSubstitutor(ScThisType(c), c), "test")
      fail("expected SubstitutorInvariantViolation")
    } catch {
      case e: SubstitutorInvariants.SubstitutorInvariantViolation => assertTrue(e.getMessage, e.getMessage.startsWith("A2 violated"))
    }
  }
}
