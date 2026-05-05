package org.jetbrains.plugins.scala.testingSupport.test.specs2

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.testingSupport.specs2.WithSpecs2_4
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.junit.Assert.{assertEquals, assertTrue}

import scala.jdk.CollectionConverters._

/**
 * Unit tests for [[Specs2BazelTestFilter]] — the Bazel `--test_filter`
 * builder ported from Google's deleted IJwB Scala plugin.
 *
 * Each test configures a single specs2 source file, finds the `ScInfixExpr`
 * for a particular test or scope, and asserts the regex string the Bazel
 * gutter logic would emit when the user clicks that location.
 */
class Specs2BazelTestFilterTest extends ScalaFixtureTestCase with WithSpecs2_4 {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13

  private val sourceText: String =
    """import org.specs2.mutable.Specification
      |
      |class ExampleSpec extends Specification {
      |  "a top-level test" in { ok }
      |
      |  "scope A" should {
      |    "scoped test 1" in { ok }
      |    "scoped test 2" >> { ok }
      |  }
      |
      |  "scope B" can {
      |    "scoped test 3" in { ok }
      |  }
      |}
      |""".stripMargin

  private def configure(): ScClass = {
    myFixture.configureByText("ExampleSpec.scala", sourceText)
    PsiTreeUtil.findChildOfType(myFixture.getFile, classOf[ScClass])
  }

  private def findInfix(predicate: ScInfixExpr => Boolean): ScInfixExpr = {
    val all = PsiTreeUtil.findChildrenOfType(myFixture.getFile, classOf[ScInfixExpr]).asScala
    all.find(predicate).getOrElse(throw new AssertionError("no matching ScInfixExpr in fixture"))
  }

  // ----- test ref clicks (in / >>) ------------------------------------

  def testTopLevelTest_inOperator(): Unit = {
    val clazz = configure()
    val infix = findInfix(e => TestNodeProvider.isSpecs2TestExpr(e) && e.getText.startsWith("\"a top-level"))
    val filter = Specs2BazelTestFilter.getTestFilter(clazz, infix)
    assertEquals(Some("ExampleSpec#\\Qa top-level test\\E$"), filter)
  }

  def testScopedTest_inOperator(): Unit = {
    val clazz = configure()
    val infix = findInfix(e => TestNodeProvider.isSpecs2TestExpr(e) && e.getText.startsWith("\"scoped test 1"))
    val filter = Specs2BazelTestFilter.getTestFilter(clazz, infix)
    assertEquals(Some("ExampleSpec#\\Qscope A should::scoped test 1\\E$"), filter)
  }

  def testScopedTest_chevronOperator(): Unit = {
    val clazz = configure()
    val infix = findInfix(e => TestNodeProvider.isSpecs2TestExpr(e) && e.getText.startsWith("\"scoped test 2"))
    val filter = Specs2BazelTestFilter.getTestFilter(clazz, infix)
    assertEquals(Some("ExampleSpec#\\Qscope A should::scoped test 2\\E$"), filter)
  }

  def testScopedTest_inCanScope(): Unit = {
    val clazz = configure()
    val infix = findInfix(e => TestNodeProvider.isSpecs2TestExpr(e) && e.getText.startsWith("\"scoped test 3"))
    val filter = Specs2BazelTestFilter.getTestFilter(clazz, infix)
    assertEquals(Some("ExampleSpec#\\Qscope B can::scoped test 3\\E$"), filter)
  }

  // ----- scope ref clicks (should / can) ------------------------------

  def testScope_should(): Unit = {
    val clazz = configure()
    val infix = findInfix(e => TestNodeProvider.isSpecs2ScopeExpr(e) && e.getText.startsWith("\"scope A\""))
    val filter = Specs2BazelTestFilter.getTestFilter(clazz, infix)
    assertEquals(Some("ExampleSpec#\\Qscope A should\\E::"), filter)
  }

  def testScope_can(): Unit = {
    val clazz = configure()
    val infix = findInfix(e => TestNodeProvider.isSpecs2ScopeExpr(e) && e.getText.startsWith("\"scope B\""))
    val filter = Specs2BazelTestFilter.getTestFilter(clazz, infix)
    assertEquals(Some("ExampleSpec#\\Qscope B can\\E::"), filter)
  }

  // ----- non-specs2 elements yield None -------------------------------

  def testElementOutsideTestExpr_returnsNone(): Unit = {
    val clazz = configure()
    // The class declaration itself isn't a test/scope expr.
    val filter = Specs2BazelTestFilter.getTestFilter(clazz, clazz)
    assertTrue(filter.isEmpty)
  }
}
