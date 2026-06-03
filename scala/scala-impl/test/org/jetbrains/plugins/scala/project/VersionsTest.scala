package org.jetbrains.plugins.scala.project

import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.jetbrains.plugins.scala.LatestScalaVersions

class VersionsTest {

  @Test
  def testRemoveOldCandidateVersionsForEachMajor(): Unit = {
    val versions = Seq(
      "2.13.17-RC1",
      "2.13.16",
      "2.13.15-M1",
      "2.12.21-M1",
      "2.12.20",
      "2.12.18-M2",
      "2.11.9",
      "2.11.9-M2",
      "2.11.8",
    ).map(Version(_))

    val versionsFilteredExpected =
      Seq(
        "2.13.17-RC1",
        "2.13.16",
        "2.12.21-M1",
        "2.12.20",
        "2.11.9",
        "2.11.8",
      ).map(Version(_))

    val versionsFilteredActual = Versions.removeOldCandidateVersionsForEachMajor(versions)
    assertEquals(
      versionsFilteredExpected.sorted.reverse,
      versionsFilteredActual.sorted.reverse,
    )
  }

  //SCL-24309
  @Test
  def testScala3HardcodedVersionsContainLatestScalaNextVersions(): Unit = {
    val hardcodedVersions = Versions.scala3HardcodedVersions
    val expectedLatestScalaNextVersions = LatestScalaVersions.allScalaNext.map(_.minor)
    expectedLatestScalaNextVersions.foreach { version =>
      assertTrue(s"Hardcoded Scala 3 versions should contain $version", hardcodedVersions.contains(version))
    }
  }
}
