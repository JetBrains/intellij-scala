package org.jetbrains.sbt.lang.completion

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.DefaultInvocationCount
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchClientTesting
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil.{Scala2CompilerArtifactId, Scala3CompilerArtifactId, ScalaCompilerGroupId}

final class SbtScalaVersionCompletionTest
  extends SbtCompletionTestBase
    with PackageSearchClientTesting {

  private val scala2UnstableVersion = "2.13.0-RC1"
  private val scala2StableVersion = "2.12.15"
  private val scala2Versions = Seq(scala2StableVersion, scala2UnstableVersion)

  private val scala3UnstableVersion = "3.1.2-RC3"
  private val scala3StableVersion = "3.3.0"
  private val scala3Versions = Seq(scala3UnstableVersion, scala3StableVersion)

  private def setupCaches(): Unit =
    DependencyUtil.updateMockVersionCompletionCache(
      (ScalaCompilerGroupId, Scala2CompilerArtifactId) -> scala2Versions,
      (ScalaCompilerGroupId, Scala3CompilerArtifactId) -> scala3Versions,
    )

  private def doTest(fileText: String, resultText: String,
                     version: String, invocationCount: Int = DefaultInvocationCount): Unit = {
    setupCaches()
    doCompletionTest(
      fileText = fileText,
      resultText = resultText,
      item = version,
      invocationCount = invocationCount
    )
  }

  private def doTestNoCompletion(fileText: String, version: String): Unit = {
    setupCaches()
    checkNoBasicCompletion(
      fileText = fileText,
      item = version
    )
  }

  def testScala2Version(): Unit = doTest(
    s"""scalaVersion := "$CARET"""",
    s"""scalaVersion := "$scala2StableVersion$CARET"""",
    scala2StableVersion
  )

  def testScala2Version2(): Unit = doTest(
    s"""scalaVersion := "2.$CARET"""",
    s"""scalaVersion := "$scala2StableVersion$CARET"""",
    scala2StableVersion
  )

  def testScala2Version3(): Unit = doTest(
    s"""scalaVersion := "2.${CARET}12.6"""",
    s"""scalaVersion := "$scala2StableVersion$CARET"""",
    scala2StableVersion
  )

  def testNoCompletionForScala2VersionOutsideOfStringLiteral(): Unit = doTestNoCompletion(
    s"""scalaVersion := $CARET""",
    scala2StableVersion
  )

  def testNoCompletionForScala2VersionOutsideOfStringLiteral2(): Unit = doTestNoCompletion(
    s"""scalaVersion := 2.$CARET""",
    scala2StableVersion
  )

  def testNoCompletionForScala2VersionOutsideOfStringLiteral3(): Unit = doTestNoCompletion(
    s"""scalaVersion := 2.${CARET}12.6""",
    scala2StableVersion
  )

  def testNoCompletionForScala2UnstableVersion(): Unit = doTestNoCompletion(
    s"""scalaVersion := "$CARET"""",
    scala2UnstableVersion
  )

  def testNoCompletionForScala2UnstableVersion2(): Unit = doTestNoCompletion(
    s"""scalaVersion := "2$CARET"""",
    scala2UnstableVersion
  )

  def testScala2UnstableVersionOnSecondInvocation(): Unit = doTest(
    s"""scalaVersion := "$CARET"""",
    s"""scalaVersion := "$scala2UnstableVersion$CARET"""",
    scala2UnstableVersion,
    invocationCount = 2
  )

  def testScala2UnstableVersionOnSecondInvocation2(): Unit = doTest(
    s"""scalaVersion := "2.$CARET"""",
    s"""scalaVersion := "$scala2UnstableVersion$CARET"""",
    scala2UnstableVersion,
    invocationCount = 2
  )

  def testScala3Version(): Unit = doTest(
    s"""scalaVersion := "$CARET"""",
    s"""scalaVersion := "$scala3StableVersion$CARET"""",
    scala3StableVersion
  )

  def testScala3Version2(): Unit = doTest(
    s"""scalaVersion := "3.$CARET"""",
    s"""scalaVersion := "$scala3StableVersion$CARET"""",
    scala3StableVersion
  )

  def testScala3Version3(): Unit = doTest(
    s"""scalaVersion := "3.${CARET}0.1"""",
    s"""scalaVersion := "$scala3StableVersion$CARET"""",
    scala3StableVersion
  )

  def testNoCompletionForScala3UnstableVersion(): Unit = doTestNoCompletion(
    s"""scalaVersion := "$CARET"""",
    scala3UnstableVersion
  )

  def testNoCompletionForScala3UnstableVersion2(): Unit = doTestNoCompletion(
    s"""scalaVersion := "3$CARET"""",
    scala3UnstableVersion
  )

  def testScala3UnstableVersionOnSecondInvocation(): Unit = doTest(
    s"""scalaVersion := "$CARET"""",
    s"""scalaVersion := "$scala3UnstableVersion$CARET"""",
    scala3UnstableVersion,
    invocationCount = 2
  )

  def testScala3UnstableVersionOnSecondInvocation2(): Unit = doTest(
    s"""scalaVersion := "3.$CARET"""",
    s"""scalaVersion := "$scala3UnstableVersion$CARET"""",
    scala3UnstableVersion,
    invocationCount = 2
  )
}
