package org.jetbrains.sbt

import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.junit.Test

class MinorVersionGeneratorTest {

  @Test
  def testGenerateAllMinorVersions(): Unit = {
    val versions = Seq(
      SbtVersion("1.4.5"),
      SbtVersion("1.7.0"),
      SbtVersion("1.11.5"),

      SbtVersion("2.0.0-RC7"),

      SbtVersion("2.1.0-RC3"),
      SbtVersion("2.1.2"),

      SbtVersion("2.2.3-RC3"),
    )
    val allMinorVersions = MinorVersionGenerator.generateAllMinorVersions(versions, (v: Version) => v.presentation)
    val expectedAllMinorVersions = Seq(
      "1.4.0",
      "1.4.1",
      "1.4.2",
      "1.4.3",
      "1.4.4",
      "1.4.5",
      "1.7.0",

      "1.11.0",
      "1.11.1",
      "1.11.2",
      "1.11.3",
      "1.11.4",
      "1.11.5",

      "2.0.0-RC7",

      "2.1.0-RC3",
      "2.1.0",
      "2.1.1",
      "2.1.2",

      "2.2.0",
      "2.2.1",
      "2.2.2",
      "2.2.3-RC3",
    )
    assertCollectionEquals(expectedAllMinorVersions, allMinorVersions)
  }

}
