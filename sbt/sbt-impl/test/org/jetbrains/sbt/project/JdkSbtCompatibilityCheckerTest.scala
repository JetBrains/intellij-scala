package org.jetbrains.sbt.project

import com.intellij.util.lang.JavaVersion
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.template.wizard.JdkSbtCompatibilityChecker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.unused

@RunWith(classOf[JUnitParamsRunner])
class JdkSbtCompatibilityCheckerTest:

  @unused("used reflectively by the @Parameters annotation")
  private def testDataMinimumSbtToJdkCompatibleVersion: Array[AnyRef] = Array(
    Array(JavaVersion.compose(6), SbtVersion("1.0.0"), None), // not present in the compatibility hardcoded table
    Array(JavaVersion.compose(8), SbtVersion("1.0.0"), None),
    Array(JavaVersion.compose(11),  SbtVersion("1.0.4"), Some(SbtVersion("1.1.0"))),
    Array(JavaVersion.compose(11),  SbtVersion("1.1.1"), None),
    Array(JavaVersion.compose(18),  SbtVersion("1.6.5"), None),
    Array(JavaVersion.compose(18),  SbtVersion("1.5.5"), Some(SbtVersion("1.6.0"))),
    Array(JavaVersion.compose(22),  SbtVersion("1.8.0"), Some(SbtVersion("1.9.0"))),
    Array(JavaVersion.compose(23),  SbtVersion("1.8.5"), Some(SbtVersion("1.9.0"))),
    Array(JavaVersion.compose(23),  SbtVersion("1.9.2"), None),
    Array(JavaVersion.compose(25),  SbtVersion("1.9.0"), None) // not present in the compatibility hardcoded table
  )

  @Test
  @Parameters(method = "testDataMinimumSbtToJdkCompatibleVersion")
  @TestCaseName("{method}[javaVersion = {0}, sbtVersion = {1}, expected = {2}]")
  def testMinimumSbtToJdkCompatibleVersion(javaVersion: JavaVersion, sbtVersion: SbtVersion, expected: Option[SbtVersion]): Unit =
    val minimumCompatibleVersion = JdkSbtCompatibilityChecker.getMinimumSbtToJdkCompatibleVersion(javaVersion, sbtVersion)
    assertEquals(expected, minimumCompatibleVersion)

  @unused("used reflectively by the @Parameters annotation")
  private def testDataHighestCompatibleJdkForSbt: Array[AnyRef] = Array(
    Array(JavaVersion.compose(6), SbtVersion("1.0.0"), None), // not present in the compatibility hardcoded table
    Array(JavaVersion.compose(8), SbtVersion("1.0.0"), None),
    Array(JavaVersion.compose(11),  SbtVersion("1.0.4"), Some(JavaVersion.compose(10))),
    Array(JavaVersion.compose(11),  SbtVersion("1.1.1"), None),
    Array(JavaVersion.compose(18),  SbtVersion("1.6.5"), None),
    Array(JavaVersion.compose(18),  SbtVersion("1.5.5"), Some(JavaVersion.compose(16))),
    Array(JavaVersion.compose(22),  SbtVersion("1.8.0"), Some(JavaVersion.compose(20))),
    Array(JavaVersion.compose(23),  SbtVersion("1.8.5"), Some(JavaVersion.compose(20))),
    Array(JavaVersion.compose(23),  SbtVersion("1.9.2"), None),
    Array(JavaVersion.compose(25),  SbtVersion("1.9.0"), None) // not present in the compatibility hardcoded table
  )

  @Test
  @Parameters(method = "testDataHighestCompatibleJdkForSbt")
  @TestCaseName("{method}[javaVersion = {0}, sbtVersion = {1}, expected = {2}]")
  def testHighestCompatibleJdkForSbt(javaVersion: JavaVersion, sbtVersion: SbtVersion, expected: Option[JavaVersion]): Unit =
    val highestCompatibleVersion = JdkSbtCompatibilityChecker.getHighestCompatibleJdkForSbt(javaVersion, sbtVersion)
    assertEquals(expected, highestCompatibleVersion)

  @unused("used reflectively by the @Parameters annotation")
  private def testDataMinimumJdkToSbtCompatibleVersion: Array[AnyRef] = Array(
    Array(JavaVersion.compose(11), SbtVersion("2.0.0-RC9"), Some(JavaVersion.compose(17))),
    Array(JavaVersion.compose(8), SbtVersion("2.0.0-RC9"), Some(JavaVersion.compose(17))),
    Array(JavaVersion.compose(17), SbtVersion("2.0.0-RC9"), None),
    Array(JavaVersion.compose(21), SbtVersion("2.0.0-RC9"), None),
    Array(JavaVersion.compose(21), SbtVersion("2.0.0-RC8"), None),
    Array(JavaVersion.compose(11), SbtVersion("1.1.0"), None),
    Array(JavaVersion.compose(11), SbtVersion("1.9.0"), None),
    Array(JavaVersion.compose(11), SbtVersion("2.0.0"), Some(JavaVersion.compose(17))),
    Array(JavaVersion.compose(11), SbtVersion("2.1.0"), Some(JavaVersion.compose(17))),
    Array(JavaVersion.compose(17), SbtVersion("2.1.0"), None),
    Array(JavaVersion.compose(8), SbtVersion("1.5.0"), None)
  )

  @Test
  @Parameters(method = "testDataMinimumJdkToSbtCompatibleVersion")
  @TestCaseName("{method}[javaVersion = {0}, sbtVersion = {1}, expected = {2}]")
  def testMinimumJdkToSbtCompatibleVersion(javaVersion: JavaVersion, sbtVersion: SbtVersion, expected: Option[JavaVersion]): Unit =
    val minRequired = JdkSbtCompatibilityChecker.getMinimumJdkToSbtCompatibleVersion(javaVersion, sbtVersion)
    assertEquals(expected, minRequired)
