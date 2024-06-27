package org.jetbrains.plugins.scala.runner

import com.intellij.codeInsight.daemon.GutterMark
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.gutter.GutterMarkersTestBase

/**
 * See also [[org.jetbrains.plugins.scala.runner.ScalaApplicationConfigurationProducerTestBase]]
 */
abstract class ScalaRunLineMarkerContributorTestBase extends GutterMarkersTestBase {

  override protected def filterGutter(marker: GutterMark): Boolean =
    marker.getIcon == ScalaRunLineMarkerContributor.RunIcon

  protected def tooltipText(name: String): String = {
    //TODO: uncomment when IJPL-157360 is fixed
    return null
    s"""Run '$name'
       |Debug '$name'
       |Run '$name' with Coverage
       |Run with Profiler""".stripMargin
  }
}

class ScalaRunLineMarkerContributorTest_Scala2 extends ScalaRunLineMarkerContributorTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testScala2Style(): Unit = doTestAllGuttersShort(
    s"""object MyMain {
       |  def main(args: Array[String]): Unit = {}
       |}
       |""".stripMargin,
    Seq(
      ExpectedGutter(1, (7, 13), tooltipText("MyMain")),
      ExpectedGutter(2, (22, 26), tooltipText("MyMain")),
    )
  )

  def testScala2Style_WithCompanionClass(): Unit = doTestAllGuttersShort(
    s"""object MyMain {
       |  def main(args: Array[String]): Unit = {}
       |}
       |class MyMain
       |""".stripMargin,
    Seq(
      ExpectedGutter(1, (7, 13), tooltipText("MyMain")),
      ExpectedGutter(2, (22, 26), tooltipText("MyMain")),
      ExpectedGutter(4, (67, 73), null)
    )
  )

  def testScala2Style_InheritedMainMethod(): Unit = doTestAllGuttersShort(
    s"""object MyMain extends MyBaseClassWithMain
       |
       |class MyBaseClassWithMain {
       |  def main(args: Array[String]): Unit = ()
       |}
       |""".stripMargin,
    Seq(
      ExpectedGutter(1, (7, 13), tooltipText("MyMain")),
    )
  )

  def testScala2Style_InheritedMainMethod_WithCompanionClass(): Unit = doTestAllGuttersShort(
    s"""object MyMain extends MyBaseClassWithMain
       |class MyMain
       |
       |class MyBaseClassWithMain {
       |  def main(args: Array[String]): Unit = ()
       |}
       |""".stripMargin,
    Seq(
      ExpectedGutter(1, (7, 13), tooltipText("MyMain")),
      ExpectedGutter(2, (48, 54), tooltipText("MyMain")),
    )
  )

  def testScala2Style_InheritedMainMethod_App(): Unit = doTestAllGuttersShort(
    s"""object MyMain extends App
       |""".stripMargin,
    Seq(
      ExpectedGutter(1, (7, 13), tooltipText("MyMain")),
    )
  )

  def testScala2Style_InheritedMainMethod_WithCompanionClass_App(): Unit = doTestAllGuttersShort(
    s"""object MyMain extends App
       |class MyMain
       |""".stripMargin,
    Seq(
      ExpectedGutter(1, (7, 13), tooltipText("MyMain")),
      ExpectedGutter(2, (32, 38), tooltipText("MyMain")),
    )
  )

  // NOTE: Scala doesn't support main methods in nested objects even if they have a stable path
  // It doesn't generate static main method
  def testScala2Style_NestedObjects(): Unit = doTestNoLineMarkers(
    s"""object ObjectOuter {
       |  object ObjectInner {
       |    def main(args: Array[String]): Unit = {
       |
       |    }
       |  }
       |}
       |""".stripMargin,
  )
}

class ScalaRunLineMarkerContributorTest_Scala3 extends ScalaRunLineMarkerContributorTest_Scala2 {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testScala3Style_TopLevel(): Unit = doTestAllGuttersShort(
    s"""
       |@main
       |def mainFoo(args: String*): Unit = {
       |}
       |
       |@main
       |def mainFooWithCustomParams(param1: Int, param2: String, other: String): Unit = {
       |}
       |
       |@main
       |def mainFooWithCustomParamsWithVararg(param1: Int, param2: String, other: String): Unit = {
       |}
       |
       |@main
       |def mainFooWithoutParams(): Unit = {
       |}
       |""".stripMargin,
    Seq(
      ExpectedGutter(3, (11, 18), tooltipText("mainFoo")),
      ExpectedGutter(7, (57, 80), tooltipText("mainFooWithCustomParams")),
      ExpectedGutter(11, (148, 181), tooltipText("mainFooWithCustomParamsWithVararg")),
      ExpectedGutter(15, (249, 269), tooltipText("mainFooWithoutParams")),
    )
  )

  def testScala3Style_Nested(): Unit = doTestAllGuttersShort(
    s"""
       |object MyObject1 {
       |  @main
       |  def mainFooInObject1(args: String*): Unit = {
       |  }
       |
       |  object MyObject2 {
       |    @main
       |    def mainFooInObject2(args: String*): Unit = {
       |    }
       |  }
       |}
       |""".stripMargin,
    Seq(
      ExpectedGutter(4, (34, 50), tooltipText("mainFooInObject1")),
      ExpectedGutter(9, (120, 136), tooltipText("mainFooInObject2")),
    )
  )
}