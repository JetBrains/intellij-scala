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

  //SCL-25707
  @Test
  def testExtractVersionsFromResponse(): Unit = {
    val pattern = """.+>(\d+\.\d+\.\d+)/<.*""".r // same shape as the Scala version pattern in Versions.Entity
    val validBody = Seq(
      """<a href="2.13.15/" title="2.13.15/">2.13.15/</a>""",
      """<a href="3.3.4/" title="3.3.4/">3.3.4/</a>"""
    )

    assertEquals(Some(Seq("2.13.15", "3.3.4")), Versions.extractVersionsFromResponse(200, validBody, pattern))
    // an error response body (e.g., HTTP 429 Too Many Requests from Maven Central) must not be treated as a successful download
    assertEquals(None, Versions.extractVersionsFromResponse(429, Seq("<html><body>429 Too Many Requests</body></html>"), pattern))
    // redirects are not followed by the HTTP client
    assertEquals(None, Versions.extractVersionsFromResponse(301, Seq.empty, pattern))
    // a successful response in an unexpected format
    assertEquals(None, Versions.extractVersionsFromResponse(200, Seq("<html>totally new layout</html>"), pattern))
    // a successful response with an empty body
    assertEquals(None, Versions.extractVersionsFromResponse(200, Seq.empty, pattern))
  }
}
