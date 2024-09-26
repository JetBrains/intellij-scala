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
      s"""Scala versions: ${ScalaVersion.Latest.Scala_2_13.minor}
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
      s"""Scala versions: ${ScalaVersion.Latest.Scala_3_3.minor} (2), ${ScalaVersion.Latest.Scala_2_13.minor} (3), ${ScalaVersion.Latest.Scala_2_12.minor} (2), ${ScalaVersion.Latest.Scala_2_11.minor}
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