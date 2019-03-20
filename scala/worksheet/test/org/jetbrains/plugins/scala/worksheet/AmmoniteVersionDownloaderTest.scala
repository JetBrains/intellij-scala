package org.jetbrains.plugins.scala
package worksheet

import org.jetbrains.plugins.scala.project.Version
import org.junit.Assert.fail

import scala.util.Success

/**
 * User: Dmitry.Naydanov
 * Date: 26.01.18.
 */
class AmmoniteVersionDownloaderTest extends base.ScalaLightPlatformCodeInsightTestCaseAdapter {

  import ammonite.ImportAmmoniteDependenciesFix._

  private def downloadAndTestScalaVersionImpl(forScala: MyScalaVersion): Unit =
    loadScalaVersions(forScala) match {
      case Success(Some(_)) =>
      case other => fail(s"Cannot download Scala version for $forScala, got: $other")
    }

  private def downloadAndTestAmmoniteVersionImpl(forScala: MyScalaVersion, forString: String): Unit =
    loadAmmoniteVersion(forScala, forString) match {
      case Success(_) =>
      case other => fail(s"Cannot download Ammonite version for $forScala, got: $other")
    }

  def testDownloadScalaVersionExact210(): Unit = downloadAndTestScalaVersionImpl(ExactVersion('0', Version("2.10.6")))

  def testDownloadScalaVersionExact211(): Unit = downloadAndTestScalaVersionImpl(ExactVersion('1', Version("2.11.8")))

  def testDownloadScalaVersionExact212(): Unit = downloadAndTestScalaVersionImpl(ExactVersion('2', Version("2.12.4")))

  def testDownloadScalaVersionMajor210(): Unit = downloadAndTestScalaVersionImpl(MajorVersion('0'))

  def testDownloadScalaVersionMajor211(): Unit = downloadAndTestScalaVersionImpl(MajorVersion('1'))

  def testDownloadScalaVersionMajor212(): Unit = downloadAndTestScalaVersionImpl(MajorVersion('2'))

  def testDownloadAmmoniteVersion210(): Unit = downloadAndTestAmmoniteVersionImpl(MajorVersion('0'), "2.10.6")

  def testDownloadAmmoniteVersion211(): Unit = downloadAndTestAmmoniteVersionImpl(MajorVersion('1'), "2.11.7")

  def testDownloadAmmoniteVersion212(): Unit = downloadAndTestAmmoniteVersionImpl(MajorVersion('2'), "2.12.2")
}
