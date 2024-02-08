package org.jetbrains.plugins.scala.internal

import junit.framework.TestCase
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.Version
import org.junit.Assert.assertEquals

class ScalaGeneralTroubleInfoCollectorTest extends TestCase {

  def testBuildText_empty(): Unit = {
    assertEquals(
      """""",
      ScalaGeneralTroubleInfoCollector.buildText(
        Map(),
        Map(),
      )
    )
  }

  def testBuildText_SingleVersion(): Unit = {
    assertEquals(
      """Scala versions: 2.13.12
        |SBT version: 1.9.2
        |""".stripMargin.trim,
      ScalaGeneralTroubleInfoCollector.buildText(
        Map(ScalaVersion.Latest.Scala_2_13 -> Seq(1)),
        Map(Version("1.9.2") -> Seq(1)),
      )
    )
  }

  def testBuildText_MultipleVersions(): Unit = {
    assertEquals(
      """Scala versions: 3.3.1 (2), 2.13.12 (3), 2.12.18 (2), 2.11.12
        |SBT version: 1.9.2 (3), 1.9.1
        |""".stripMargin.trim,
      ScalaGeneralTroubleInfoCollector.buildText(
        Map(
          ScalaVersion.Latest.Scala_3_3 -> Seq(1, 1),
          ScalaVersion.Latest.Scala_2_13 -> Seq(1, 1, 1),
          ScalaVersion.Latest.Scala_2_12 -> Seq(1, 1),
          ScalaVersion.Latest.Scala_2_11 -> Seq(1),
        ),
        Map(
          Version("1.9.2") -> Seq(1, 1, 1),
          Version("1.9.1") -> Seq(1),
        ),
      )
    )
  }
}