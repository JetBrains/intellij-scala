package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.SubstitutorInvariants.{Mode, Rule}
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

  // The leak of §5.2: `PatternTypeInference` puts `ThisTypeSubstitution(C.this, owner(unapply))` into the
  // substitutor that the resolver threads to every reference in the case body. The detector must see it; once
  // the match-clause substitutor is type-parameter-only this assertion flips to `assertSilent(Rule.StateSafe)`.
  def testMatchClauseSubstitutorCarriesThisLink(): Unit = {
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
    assertTrue(SubstitutorInvariants.report, SubstitutorInvariants.count(Rule.StateSafe) > 0)
    assertTrue(SubstitutorInvariants.samplesOf(Rule.StateSafe).exists(_.contains("matchClauseSubstitutor")))
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
