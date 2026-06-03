package org.jetbrains.sbt.project

import com.intellij.util.lang.JavaVersion
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.project.template.wizard.JdkScalaCompatibilityChecker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(classOf[JUnitParamsRunner])
class JdkScalaCompatibilityCheckerTest:

  private def testDataMinimumScalaToJdkCompatibleVersion: Array[AnyRef] = Array(
    Array(JavaVersion.compose(8),  ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12"), None),
    Array(JavaVersion.compose(8),  ScalaVersion(ScalaLanguageLevel.Scala_2_11, "5"), None),
    Array(JavaVersion.compose(11), ScalaVersion(ScalaLanguageLevel.Scala_2_11, "11"), Some(ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12"))),
    Array(JavaVersion.compose(16), ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12"), None),
    Array(JavaVersion.compose(25), ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12"), None),
    Array(JavaVersion.compose(17), ScalaVersion(ScalaLanguageLevel.Scala_2_11, "11"), None),
    Array(JavaVersion.compose(11), ScalaVersion(ScalaLanguageLevel.Scala_2_12, "3"), Some(ScalaVersion(ScalaLanguageLevel.Scala_2_12, "4"))),
    Array(JavaVersion.compose(11), ScalaVersion(ScalaLanguageLevel.Scala_2_13, "0"), None),
    Array(JavaVersion.compose(15), ScalaVersion(ScalaLanguageLevel.Scala_2_13, "5"), None),
    Array(JavaVersion.compose(17), ScalaVersion(ScalaLanguageLevel.Scala_2_12, "14"), Some(ScalaVersion(ScalaLanguageLevel.Scala_2_12, "15"))),
    Array(JavaVersion.compose(16), ScalaVersion(ScalaLanguageLevel.Scala_2_12, "14"), None),
    Array(JavaVersion.compose(17), ScalaVersion(ScalaLanguageLevel.Scala_2_13, "6"), None),
    Array(JavaVersion.compose(21), ScalaVersion(ScalaLanguageLevel.Scala_2_13, "10"), Some(ScalaVersion(ScalaLanguageLevel.Scala_2_13, "11"))),
    Array(JavaVersion.compose(21), ScalaVersion(ScalaLanguageLevel.Scala_3_3,  "0"), Some(ScalaVersion(ScalaLanguageLevel.Scala_3_3,  "1"))),
    Array(JavaVersion.compose(21), ScalaVersion(ScalaLanguageLevel.Scala_3_4,  "0"), None),
    Array(JavaVersion.compose(25), ScalaVersion(ScalaLanguageLevel.Scala_3_7,  "0"), Some(ScalaVersion(ScalaLanguageLevel.Scala_3_7,  "1"))),
    Array(JavaVersion.compose(25), ScalaVersion(ScalaLanguageLevel.Scala_3_3,  "5"), Some(ScalaVersion(ScalaLanguageLevel.Scala_3_3,  "6"))),
    // For Scala < 2.11, always return None (indicating compatibility), since the compatibility table only covers Scala 2.11 and later
    Array(JavaVersion.compose(8),  ScalaVersion(ScalaLanguageLevel.Scala_2_10, "3"), None),
    Array(JavaVersion.compose(11),  ScalaVersion(ScalaLanguageLevel.Scala_2_10, "3"), None),
    Array(JavaVersion.compose(25),  ScalaVersion(ScalaLanguageLevel.Scala_2_9, "3"), None),
    // When a new JDK version is released (e.g., JDK 27), Scala versions compatible with the previous JDK (26) remain compatible.
    Array(JavaVersion.compose(27), ScalaVersion(ScalaLanguageLevel.Scala_2_13, "17"), None),
    Array(JavaVersion.compose(27), ScalaVersion(ScalaLanguageLevel.Scala_2_13, "16"), Some(ScalaVersion(ScalaLanguageLevel.Scala_2_13, "17"))),
    Array(JavaVersion.compose(27), ScalaVersion(ScalaLanguageLevel.Scala_3_7,  "1"), None),
    // There are no minimum Scala versions for Scala 3.8+
    Array(JavaVersion.compose(11), ScalaVersion(ScalaLanguageLevel.Scala_3_8, "0"), None),
    Array(JavaVersion.compose(21), ScalaVersion(ScalaLanguageLevel.Scala_3_8,  "1"), None),
  )

  @Test
  @Parameters(method = "testDataMinimumScalaToJdkCompatibleVersion")
  @TestCaseName("{method}[javaVersion = {0}, scalaVersion = {1}, expected = {2}]")
  def testMinimumScalaToJdkCompatibleVersion(javaVersion: JavaVersion, scalaVersion: ScalaVersion, expected: Option[ScalaVersion]): Unit =
    val minimumCompatibleVersion = JdkScalaCompatibilityChecker.getMinimumScalaToJdkCompatibleVersion(javaVersion, scalaVersion)
    assertEquals(expected, minimumCompatibleVersion)

  private def testDataHighestCompatibleJdkForScala: Array[AnyRef] = Array(
    Array(JavaVersion.compose(21), ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12"), None),
    Array(JavaVersion.compose(11), ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12"), None),
    Array(JavaVersion.compose(10), ScalaVersion(ScalaLanguageLevel.Scala_2_11, "12"), None),
    Array(JavaVersion.compose(21), ScalaVersion(ScalaLanguageLevel.Scala_3_3,  "0"),  Some(JavaVersion.compose(20))),
    Array(JavaVersion.compose(25), ScalaVersion(ScalaLanguageLevel.Scala_2_12, "19"), Some(JavaVersion.compose(24))),
    Array(JavaVersion.compose(23), ScalaVersion(ScalaLanguageLevel.Scala_3_4,  "0"),  None),
    Array(JavaVersion.compose(20), ScalaVersion(ScalaLanguageLevel.Scala_2_13, "6"), None),
    Array(JavaVersion.compose(11), ScalaVersion(ScalaLanguageLevel.Scala_3_8, "0"), None),
    Array(JavaVersion.compose(21), ScalaVersion(ScalaLanguageLevel.Scala_3_8,  "1"), None),
  )

  @Test
  @Parameters(method = "testDataHighestCompatibleJdkForScala")
  @TestCaseName("{method}[javaVersion = {0}, scalaVersion = {1}, expected = {2}]")
  def testHighestCompatibleJdkForScala(javaVersion: JavaVersion, scalaVersion: ScalaVersion, expected: Option[JavaVersion]): Unit =
    val highestCompatibleVersion = JdkScalaCompatibilityChecker.getHighestCompatibleJdkForScala(javaVersion, scalaVersion)
    assertEquals(expected, highestCompatibleVersion)

  private def testDataMinimumJdkRequiredForScala: Array[AnyRef] = Array(
    Array(JavaVersion.compose(11), ScalaVersion(ScalaLanguageLevel.Scala_3_8,  "0"), Some(JavaVersion.compose(17))),
    Array(JavaVersion.compose(8), ScalaVersion(ScalaLanguageLevel.Scala_3_8,  "0"), Some(JavaVersion.compose(17))),
    Array(JavaVersion.compose(17), ScalaVersion(ScalaLanguageLevel.Scala_3_8,  "0"), None),
    Array(JavaVersion.compose(21), ScalaVersion(ScalaLanguageLevel.Scala_3_8,  "0"), None)
  )

  @Test
  @Parameters(method = "testDataMinimumJdkRequiredForScala")
  @TestCaseName("{method}[javaVersion = {0}, scalaVersion = {1}, expected = {2}]")
  def testMinimumJdkRequiredForScala(javaVersion: JavaVersion, scalaVersion: ScalaVersion, expected: Option[JavaVersion]): Unit =
    val minRequired = JdkScalaCompatibilityChecker.getMinimumJdkRequiredForScala(javaVersion, scalaVersion)
    assertEquals(expected, minRequired)
