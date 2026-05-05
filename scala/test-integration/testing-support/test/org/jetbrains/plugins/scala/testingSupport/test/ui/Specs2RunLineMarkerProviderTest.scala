package org.jetbrains.plugins.scala.testingSupport.test.ui

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.annotator.gutter.GutterMarkersTestBase
import org.jetbrains.plugins.scala.testingSupport.specs2.WithSpecs2_4

class Specs2RunLineMarkerProviderTest
  extends GutterMarkersTestBase
    with WithSpecs2_4 {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13

  def testSpecs2GuttersAllNodesAndLeaves(): Unit = {
    doTestAllGuttersShortWithText(
      """import org.specs2.mutable.Specification
        |
        |class ExampleSpec extends Specification {
        |  "scope A" should {
        |    "test 1" in { ok }
        |    "test 2" >> { ok }
        |    "test 3" ! { ok }
        |  }
        |  "scope B" can {
        |    "test 4" in { ok }
        |  }
        |}
        |""".stripMargin,
      """line 3 (47, 58) Run Test
        |line 4 (95, 101) Run Test
        |line 5 (117, 119) Run Test
        |line 6 (140, 142) Run Test
        |line 7 (163, 164) Run Test
        |line 9 (188, 191) Run Test
        |line 10 (207, 209) Run Test""".stripMargin
    )
  }
}
