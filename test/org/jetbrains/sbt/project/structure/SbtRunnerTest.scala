package org.jetbrains.sbt
package project.structure

import java.io.File

import com.intellij.testFramework.UsefulTestCase
import junit.framework.Assert._
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * @author Nikolay Obedin
 * @since 8/18/15.
 */
class SbtRunnerTest extends UsefulTestCase {

  def testSbtLaunch_0_12_4: Unit =
    doTestLauncherVersionDetection("0.12.4")

  def testSbtLaunch_0_13_0: Unit =
    doTestLauncherVersionDetection("0.13.0")

  def testSbtLaunch_0_13_1: Unit =
    doTestLauncherVersionDetection("0.13.1")

  def testSbtLaunch_0_13_2: Unit =
    doTestLauncherVersionDetection("0.13.2")

  def testSbtLaunch_0_13_5: Unit =
    doTestLauncherVersionDetection("0.13.5")

  def testSbtLaunch_0_13_6: Unit =
    doTestLauncherVersionDetection("0.13.6")

  def testSbtLaunch_0_13_7: Unit =
    doTestLauncherVersionDetection("0.13.7")

  def testSbtLaunch_0_13_8: Unit =
    doTestLauncherVersionDetection("0.13.8")

  def testSbtLaunch_0_13_9: Unit =
    doTestLauncherVersionDetection("0.13.9")

  private def doTestLauncherVersionDetection(sbtVersion: String): Unit = {
    val sbtLaunchJar = getSbtLaunchJarForVersion(sbtVersion)
    assertTrue(s"$sbtLaunchJar is not found. Make sure it is downloaded by Ivy.", sbtLaunchJar.exists())
    val actualVersion = SbtRunner.sbtVersionInBootPropertiesOf(sbtLaunchJar).orNull
    assertEquals(sbtVersion, actualVersion)
  }

  private def getSbtLaunchJarForVersion(sbtVersion: String): File =
    new File(TestUtils.getIvyCachePath) / "org.scala-sbt" / "sbt-launch" / "jars" / s"sbt-launch-$sbtVersion.jar"
}
