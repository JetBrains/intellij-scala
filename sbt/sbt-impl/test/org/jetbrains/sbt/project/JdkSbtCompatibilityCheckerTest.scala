package org.jetbrains.sbt.project

import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.template.wizard.JdkSbtCompatibilityChecker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import scala.jdk.CollectionConverters._

class JdkSbtCompatibilityCheckerTest {
  @ParameterizedTest
  @MethodSource(Array("org.jetbrains.sbt.project.JdkSbtCompatibilityCheckerTest#testDataMinimumSbtToJdkCompatibleVersion"))
  def testMinimumSbtToJdkCompatibleVersion(data: (JavaVersion, SbtVersion, Option[SbtVersion])): Unit = {
    val (jdk, sbt, expectedResult) = data
    val minimumCompatibleVersion = JdkSbtCompatibilityChecker.getMinimumSbtToJdkCompatibleVersion(jdk, sbt)
    assertEquals(expectedResult, minimumCompatibleVersion)
  }

  @ParameterizedTest
  @MethodSource(Array("org.jetbrains.sbt.project.JdkSbtCompatibilityCheckerTest#testDataHighestCompatibleJdkForSbt"))
  def testHighestCompatibleJdkForSbt(data: (JavaVersion, SbtVersion, Option[SbtVersion])): Unit = {
    val (jdk, sbt, expectedResult) = data
    val highestCompatibleVersion = JdkSbtCompatibilityChecker.getHighestCompatibleJdkForSbt(jdk, sbt)
    assertEquals(expectedResult, highestCompatibleVersion)
  }
}

object JdkSbtCompatibilityCheckerTest {
  def testDataMinimumSbtToJdkCompatibleVersion(): java.util.stream.Stream[(JavaVersion, SbtVersion, Option[SbtVersion])] = {
    Seq(
      (JavaVersion.compose(6), SbtVersion("1.0.0"), None), // not present in the compatibility hardcoded table
      (JavaVersion.compose(8), SbtVersion("1.0.0"), None),
      (JavaVersion.compose(11),  SbtVersion("1.0.4"), Some(SbtVersion("1.1.0"))),
      (JavaVersion.compose(11),  SbtVersion("1.1.1"), None),
      (JavaVersion.compose(18),  SbtVersion("1.6.5"), None),
      (JavaVersion.compose(18),  SbtVersion("1.5.5"), Some(SbtVersion("1.6.0"))),
      (JavaVersion.compose(22),  SbtVersion("1.8.0"), Some(SbtVersion("1.9.0"))),
      (JavaVersion.compose(23),  SbtVersion("1.8.5"), Some(SbtVersion("1.9.0"))),
      (JavaVersion.compose(23),  SbtVersion("1.9.2"), None),
      (JavaVersion.compose(25),  SbtVersion("1.9.0"), None), // not present in the compatibility hardcoded table
    ).asJava.stream()
  }

  def testDataHighestCompatibleJdkForSbt(): java.util.stream.Stream[(JavaVersion, SbtVersion, Option[JavaVersion])] = {
    Seq(
      (JavaVersion.compose(6), SbtVersion("1.0.0"), None), // not present in the compatibility hardcoded table
      (JavaVersion.compose(8), SbtVersion("1.0.0"), None),
      (JavaVersion.compose(11),  SbtVersion("1.0.4"), Some(JavaVersion.compose(10))),
      (JavaVersion.compose(11),  SbtVersion("1.1.1"), None),
      (JavaVersion.compose(18),  SbtVersion("1.6.5"), None),
      (JavaVersion.compose(18),  SbtVersion("1.5.5"), Some(JavaVersion.compose(16))),
      (JavaVersion.compose(22),  SbtVersion("1.8.0"), Some(JavaVersion.compose(20))),
      (JavaVersion.compose(23),  SbtVersion("1.8.5"), Some(JavaVersion.compose(20))),
      (JavaVersion.compose(23),  SbtVersion("1.9.2"), None),
      (JavaVersion.compose(25),  SbtVersion("1.9.0"), None), // not present in the compatibility hardcoded table
    ).asJava.stream()
  }
}
