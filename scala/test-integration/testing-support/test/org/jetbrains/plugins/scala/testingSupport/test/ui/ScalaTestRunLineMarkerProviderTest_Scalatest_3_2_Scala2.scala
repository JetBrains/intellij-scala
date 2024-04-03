package org.jetbrains.plugins.scala.testingSupport.test.ui

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.gutter.GutterMarkersTestBase
import org.jetbrains.plugins.scala.testingSupport.scalatest.WithScalaTest_3_2

class ScalaTestRunLineMarkerProviderTest_Scalatest_3_2_Scala2 extends ScalaTestRunLineMarkerProviderTestBase_Scalatest_3_2 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class ScalaTestRunLineMarkerProviderTest_Scalatest_3_2_Scala3 extends ScalaTestRunLineMarkerProviderTestBase_Scalatest_3_2 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class ScalaTestRunLineMarkerProviderTestBase_Scalatest_3_2
  extends GutterMarkersTestBase
    with WithScalaTest_3_2 {

  def testFeatureSpec(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.scalatest.Tag
        |import org.scalatest.featurespec.AnyFeatureSpecLike
        |
        |class ExampleFeatureSpec extends AnyFeatureSpecLike {
        |  Feature("Feature 1") {
        |    Scenario("Scenario 1") {
        |    }
        |    Scenario("Scenario 2") {
        |    }
        |  }
        |
        |  Feature("Feature 2") {
        |    Scenario("Scenario 3") {
        |    }
        |    Scenario("Scenario 4") {
        |    }
        |  }
        |
        |  Feature("empty") {
        |  }
        |
        |  Feature("Feature 3") {
        |    Scenario("Tagged", FeatureSpecTag) {}
        |  }
        |}
        |
        |object FeatureSpecTag extends Tag("MyTag")
        |""".stripMargin,
      """line 4 (84, 102) Run Test
        |line 5 (134, 141) Run Test
        |line 6 (161, 169) Run Test
        |line 8 (196, 204) Run Test
        |line 12 (234, 241) Run Test
        |line 13 (261, 269) Run Test
        |line 15 (296, 304) Run Test
        |line 19 (334, 341) Run Test
        |line 22 (360, 367) Run Test
        |line 23 (387, 395) Run Test""".stripMargin
    )
  }

  def testFeatureSpec_DeprecatedSyntax(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.scalatest.featurespec.AnyFeatureSpecLike
        |
        |class ExampleFeatureSpec_DeprecatedSyntax extends AnyFeatureSpecLike {
        |  feature("feature 1") {
        |    scenario("scenario 1") {
        |    }
        |    scenario("scenario 2") {
        |    }
        |  }
        |
        |  feature("feature 2") {
        |    scenario("scenario 3") {
        |    }
        |    scenario("scenario 4") {
        |    }
        |  }
        |}""".stripMargin,
      """line 3 (59, 94) Run Test
        |line 4 (126, 133) Run Test
        |line 5 (153, 161) Run Test
        |line 7 (188, 196) Run Test
        |line 11 (226, 233) Run Test
        |line 12 (253, 261) Run Test
        |line 14 (288, 296) Run Test""".stripMargin
    )
  }

  def testFlatSpec(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.scalatest.Tag
        |import org.scalatest.flatspec.AnyFlatSpecLike
        |
        |class ExampleFlatSpec extends AnyFlatSpecLike {
        |  it should "test 1" in {
        |  }
        |
        |  it should "test 2" in {
        |  }
        |
        |  "test name1" should "test 1" in {
        |  }
        |
        |  "test name2" should "test 2" in {
        |  }
        |
        |  it should "run tagged tests" taggedAs(FlatSpecTag) in {}
        |}
        |
        |object FlatSpecTag extends Tag("MyTag")
        |""".stripMargin,
      """line 4 (78, 93) Run Test
        |line 5 (141, 143) Run Test
        |line 8 (172, 174) Run Test
        |line 11 (213, 215) Run Test
        |line 14 (254, 256) Run Test
        |line 17 (317, 319) Run Test""".stripMargin
    )
  }

  def testFreeSpec(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.scalatest.Tag
        |import org.scalatest.freespec.AnyFreeSpecLike
        |
        |class ExampleFreeSpec extends AnyFreeSpecLike {
        |
        |  "spec level 1" - {
        |    "spec level 2" - {
        |
        |      "spec level 3" - {
        |      }
        |    }
        |  }
        |
        |  "another spec level 1" - {
        |    "spec level 2" - {
        |
        |      "spec level 3" - {
        |      }
        |    }
        |
        |    "can be tagged" taggedAs(FreeSpecTag) in {}
        |  }
        |}
        |
        |object FreeSpecTag extends Tag("MyTag")""".stripMargin,
      """line 4 (78, 93) Run Test
        |line 6 (138, 139) Run Test
        |line 7 (161, 162) Run Test
        |line 9 (187, 188) Run Test
        |line 14 (235, 236) Run Test
        |line 15 (258, 259) Run Test
        |line 17 (284, 285) Run Test
        |line 21 (345, 347) Run Test""".stripMargin
    )
  }

  def testFreeSpecPath(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.scalatest.freespec.PathAnyFreeSpec
        |
        |class ExampleFreeSpecPath extends PathAnyFreeSpec {
        |
        |  "spec level 1" - {
        |    "spec level 2" - {
        |
        |      "spec level 3" - {
        |      }
        |    }
        |  }
        |
        |  "another spec level 1" - {
        |    "spec level 2" - {
        |
        |      "spec level 3" - {
        |      }
        |    }
        |  }
        |}
        |""".stripMargin,
      """line 3 (53, 72) Run Test
        |line 5 (117, 118) Run Test
        |line 6 (140, 141) Run Test
        |line 8 (166, 167) Run Test
        |line 13 (214, 215) Run Test
        |line 14 (237, 238) Run Test
        |line 16 (263, 264) Run Test""".stripMargin
    )
  }

  def testFunSpec(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.scalatest.Tag
        |import org.scalatest.funspec.AnyFunSpecLike
        |
        |class ExampleFunSpec extends AnyFunSpecLike {
        |
        |  describe("level 1.1"){
        |    it ("test1") {
        |    }
        |    it ("test2") {
        |    }
        |    describe("test 1.2") {
        |      it("test3") {
        |      }
        |    }
        |  }
        |
        |  describe("level 2.1"){
        |    it ("test1") {
        |    }
        |    it ("test2") {
        |    }
        |    describe("test 2.2") {
        |      it("test3") {
        |      }
        |
        |      it ("is tagged", FunSpecTag) {}
        |    }
        |  }
        |}
        |
        |object FunSpecTag extends Tag("MyTag")
        |""".stripMargin,
      """line 4 (76, 90) Run Test
        |line 6 (119, 127) Run Test
        |line 7 (146, 148) Run Test
        |line 9 (171, 173) Run Test
        |line 11 (196, 204) Run Test
        |line 12 (225, 227) Run Test
        |line 17 (260, 268) Run Test
        |line 18 (287, 289) Run Test
        |line 20 (312, 314) Run Test
        |line 22 (337, 345) Run Test
        |line 23 (366, 368) Run Test
        |line 26 (395, 397) Run Test""".stripMargin
    )
  }

  def testFunSuite(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.scalatest.Tag
        |import org.scalatest.funsuite.AnyFunSuiteLike
        |
        |class ExampleFunSuite extends AnyFunSuiteLike {
        |  test("test 1") {
        |  }
        |
        |  test("test 2") {
        |  }
        |
        |  test("tagged", FunSuiteTag) {}
        |}
        |
        |object FunSuiteTag extends Tag("MyTag")
        |""".stripMargin,
      """line 4 (78, 93) Run Test
        |line 5 (122, 126) Run Test
        |line 8 (146, 150) Run Test
        |line 11 (170, 174) Run Test""".stripMargin
    )
  }

  def testPropSpec(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.scalatest.Tag
        |import org.scalatest.propspec.AnyPropSpecLike
        |
        |class ExamplePropSpec extends AnyPropSpecLike {
        |  property("test 1") {
        |  }
        |
        |  property("test 2", PropSpecTag) {
        |  }
        |}
        |
        |object PropSpecTag extends Tag("MyTag")""".stripMargin,
      """line 4 (78, 93) Run Test
        |line 5 (122, 130) Run Test
        |line 8 (150, 158) Run Test""".stripMargin
    )
  }

  def testWorldSpec(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.scalatest.Tag
        |import org.scalatest.wordspec.AnyWordSpecLike
        |
        |class ExampleWorldSpec extends AnyWordSpecLike {
        |  "level 1" should {
        |    "test 1" in {
        |    }
        |    "test 2" in {
        |    }
        |  }
        |
        |  "level 2" should {
        |    "test 1" in {
        |    }
        |    "test 2" in {
        |    }
        |  }
        |
        |  "empty" should {
        |    ()
        |  }
        |
        |  "tagged" should {
        |    "be tagged" taggedAs (WordSpecTag) in {}
        |  }
        |}
        |
        |object WordSpecTag extends Tag("MyTag")
        |""".stripMargin,
      """line 4 (78, 94) Run Test
        |line 5 (133, 139) Run Test
        |line 6 (155, 157) Run Test
        |line 8 (179, 181) Run Test
        |line 12 (207, 213) Run Test
        |line 13 (229, 231) Run Test
        |line 15 (253, 255) Run Test
        |line 19 (279, 285) Run Test
        |line 23 (311, 317) Run Test
        |line 24 (359, 361) Run Test""".stripMargin
    )
  }
}
