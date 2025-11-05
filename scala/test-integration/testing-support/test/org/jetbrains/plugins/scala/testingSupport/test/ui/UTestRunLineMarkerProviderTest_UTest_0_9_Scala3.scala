package org.jetbrains.plugins.scala.testingSupport.test.ui

import com.intellij.codeInsight.daemon.GutterMark
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.gutter.GutterMarkersTestBase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

class UTestRunLineMarkerProviderTest_UTest_0_9_Scala3 extends GutterMarkersTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders ++ Seq(
    IvyManagedLoader(("com.lihaoyi" %% "utest" % UTestTestCase.LatestVersions.UTest_0_9.presentation).transitive())
  )

  // Leave only test gutters, remove all other known
  override protected def ignoreGutterInTest(gutter: GutterMark): Boolean =
    gutter.getTooltipText.contains("Implements member from")

  def testGutterOnObjectWithDollarInTheEnd(): Unit = doTestAllGuttersShortWithText(
    """import utest._
      |
      |object MyTestObjectWithDollarInTheEnd1$ extends TestSuite {
      |  override def tests: Tests = Tests { test("test 1") {} }
      |}
      |""".stripMargin,
    """line 3 (23, 55) Run Test""".stripMargin
  )

  def testGutterOnClassWithDollarInTheEnd(): Unit = doTestAllGuttersShortWithText(
    """import utest._
      |
      |class MyTestClassWithDollarInTheEnd1$ extends TestSuite {
      |  override def tests: Tests = Tests { test("test 1") {} }
      |}
      |""".stripMargin,
    """line 3 (22, 53) Run Test""".stripMargin
  )

  def testGutterOnStandaloneObject(): Unit = doTestAllGuttersShortWithText(
    """import utest._
      |
      |object MyTestObject extends TestSuite {
      |  override def tests: Tests = Tests {test("test 1") {} }
      |}
      |""".stripMargin,
    """line 3 (23, 35) Run Test""".stripMargin
  )

  def testGutterOnStandaloneClass(): Unit = doTestAllGuttersShortWithText(
    """import utest._
      |
      |class MyTestClass extends TestSuite {
      |  override def tests: Tests = Tests {test("test 1") {} }
      |}
      |""".stripMargin,
    """line 3 (22, 33) Run Test""".stripMargin
  )

  def testGutterOnClassWithNonTestCompanion(): Unit = doTestAllGuttersShortWithText(
    """import utest._
      |
      |object MyTestClassWithCompanion
      |class MyTestClassWithCompanion extends TestSuite {
      |  override def tests: Tests = Tests {test("test 1") {} }
      |}
      |""".stripMargin,
    """line 4 (54, 78) Run Test""".stripMargin
  )

  def testGutterOnObjectWithNonTestCompanion(): Unit = doTestAllGuttersShortWithText(
    """import utest._
      |
      |class MyTestObjectWithCompanion
      |object MyTestObjectWithCompanion extends TestSuite {
      |  override def tests: Tests = Tests {test("test 1") {} }
      |}
      |""".stripMargin,
    """line 4 (55, 80) Run Test""".stripMargin
  )

  def testGutterOnBothClassAndObjectWhenBothAreTestSuites(): Unit = doTestAllGuttersShortWithText(
    """import utest._
      |
      |class MyTestObjectWithCompanionTestClass extends TestSuite {
      |  override def tests: Tests = Tests {test("test from class") {} }
      |}
      |object MyTestObjectWithCompanionTestClass extends TestSuite {
      |  override def tests: Tests = Tests {test("test from object") {} }
      |}
      |""".stripMargin,
    // NOTE: we actually could show just one gutter for MyTestObjectWithCompanionTestClass
    // because under the hood uTest will only recognize it, but it doesn't worth implementing it.
    """line 3 (22, 56) Run Test
      |line 6 (152, 186) Run Test""".stripMargin
  )

  def testGutterOnClassWithParamAndDefaultConstructor(): Unit = doTestAllGuttersShortWithText(
    """import utest._
      |
      |class MyGoodClassWithParamAndDefaultConstructor(param: Int) extends TestSuite {
      |  def this() = this(0)
      |  override def tests: Tests = Tests { test("test1") {}}
      |}
      |""".stripMargin,
    """line 3 (22, 63) Run Test""".stripMargin
  )

  def testNoGutterOnClassNotInheritingTestSuite(): Unit = doTestNoLineMarkers(
    """import utest._
      |
      |class MyImpostorClassNotInherits(param: Int) {
      |  def tests: Tests = Tests { test("test1") {}}
      |}
      |""".stripMargin
  )

  def testNoGutterOnClassWithParamWithoutDefaultConstructor(): Unit = doTestNoLineMarkers(
    """import utest._
      |
      |class MyImpostorClassWithParam(param: Int) extends TestSuite {
      |  override def tests: Tests = Tests { test("test1") {}}
      |}
      |""".stripMargin
  )

  def testNoGutterOnAbstractClass(): Unit = doTestNoLineMarkers(
    """import utest._
      |
      |abstract class MyImpostorClassAbstract extends TestSuite {
      |  override def tests: Tests = Tests { test("test1") {}}
      |}
      |""".stripMargin
  )

  def testNoGutterOnLocalClassInObject(): Unit = doTestNoLineMarkers(
    """import utest._
      |
      |object ObjectWrapper {
      |  class MyImpostorClassLocalInObject extends TestSuite {
      |    override def tests: Tests = Tests { test("test1") {}}
      |  }
      |}
      |""".stripMargin
  )

  def testNoGutterOnLocalObjectInObject(): Unit = doTestNoLineMarkers(
    """import utest._
      |
      |object ObjectWrapper {
      |  object MyImpostorObjectLocalInObject extends TestSuite {
      |    override def tests: Tests = Tests { test("test1") {}}
      |  }
      |}
      |""".stripMargin
  )

  def testNoGutterOnLocalClassInClass(): Unit = doTestNoLineMarkers(
    """import utest._
      |
      |class ObjectWrapper {
      |  class MyImpostorClassLocalInClass extends TestSuite {
      |    override def tests: Tests = Tests { test("test1") {}}
      |  }
      |}
      |""".stripMargin
  )

  def testNoGutterOnLocalObjectInClass(): Unit = doTestNoLineMarkers(
    """import utest._
      |
      |class ObjectWrapper {
      |  object MyImpostorObjectLocalInClass extends TestSuite {
      |    override def tests: Tests = Tests { test("test1") {}}
      |  }
      |}
      |""".stripMargin
  )
}
