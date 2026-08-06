package org.jetbrains.plugins.scala.testingSupport.munit

import com.intellij.codeInsight.daemon.GutterMark
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.gutter.GutterMarkersTestBase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
abstract class MUnitRunLineMarkerProviderTestBase extends GutterMarkersTestBase {

  protected def munitVersion: String

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders ++ Seq(
    IvyManagedLoader(("org.scalameta" %% "munit" % munitVersion).transitive()),
    IvyManagedLoader(("org.scalameta" %% "munit-scalacheck" % munitVersion).transitive())
  )

  // Leave only test gutters, remove all other known
  override protected def ignoreGutterInTest(gutter: GutterMark): Boolean =
    Option(gutter.getTooltipText).exists(_.contains("Implements member from"))

  @Test
  def gutterOnFunSuiteTest(): Unit = doTestAllGuttersShortWithText(
    """import munit.FunSuite
      |
      |class MyFunSuite extends FunSuite {
      |  test("t1") {}
      |}
      |""".stripMargin,
    """line 3 (29, 39) Run Test
      |line 4 (61, 65) Run Test""".stripMargin
  )

  @Test
  def gutterOnScalaCheckSuiteProperty(): Unit = doTestAllGuttersShortWithText(
    """import munit.ScalaCheckSuite
      |
      |class MyScalaCheckSuite extends ScalaCheckSuite {
      |  property("p1") {}
      |}
      |""".stripMargin,
    """line 3 (36, 53) Run Test
      |line 4 (82, 90) Run Test""".stripMargin
  )

  @Test
  def gutterOnFunFixtureTest(): Unit = doTestAllGuttersShortWithText(
    """import munit.FunSuite
      |
      |class MyFunFixtureUsage extends FunSuite {
      |  val fix = FunFixture[Int](_ => 0, _ => ())
      |
      |  fix.test("t1") { _ => }
      |}
      |""".stripMargin,
    """line 3 (29, 46) Run Test
      |line 6 (118, 122) Run Test""".stripMargin
  )

  // The helper class is shaped like FunFixture (a value used inside an MUnit suite)
  // and has `test` / `property` methods with the same signatures as the framework
  // ones, but it is unrelated to `munit.Suite` / `munit.FunFixtures.FunFixture`.
  // We expect the class-level gutter on the enclosing MUnit suite, but no gutters
  // on the `helper.test(...)` / `helper.property(...)` calls.
  @Test
  def noGutterOnImpostorTestAndProperty(): Unit = doTestAllGuttersShortWithText(
    """import munit.FunSuite
      |
      |class Helper {
      |  def test(name: String)(body: => Any): Unit = ()
      |  def property(name: String)(body: => Any): Unit = ()
      |}
      |
      |class MyImpostorUsage extends FunSuite {
      |  val helper = new Helper
      |  helper.test("not real") {}
      |  helper.property("not real") {}
      |}
      |""".stripMargin,
    """line 8 (151, 166) Run Test""".stripMargin
  )
}

class MUnit_0_7_RunLineMarkerProviderTest extends MUnitRunLineMarkerProviderTestBase {
  override protected def munitVersion: String = "0.7.29"
}

class MUnit_1_0_RunLineMarkerProviderTest extends MUnitRunLineMarkerProviderTestBase {
  override protected def munitVersion: String = "1.0.0"
}
