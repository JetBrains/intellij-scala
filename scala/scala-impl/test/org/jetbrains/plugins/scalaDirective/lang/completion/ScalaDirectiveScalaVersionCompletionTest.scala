package org.jetbrains.plugins.scalaDirective.lang.completion

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.DefaultInvocationCount
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient
import org.jetbrains.plugins.scala.packagesearch.model.ApiPackage
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveScalaVersionCompletionContributor.{Scala2CompilerArtifactId, Scala3CompilerArtifactId, ScalaCompilerGroupId}

final class ScalaDirectiveScalaVersionCompletionTest extends ScalaCompletionTestBase {
  private val scala2UnstableVersion = "2.13.0-RC1"
  private val scala2StableVersion = "2.12.15"
  private val scala2Versions = Seq(scala2StableVersion, scala2UnstableVersion)

  private val scala3UnstableVersion = "3.1.2-RC3"
  private val scala3StableVersion = "3.3.0"
  private val scala3Versions = Seq(scala3UnstableVersion, scala3StableVersion)

  private def setupCaches(): Unit = {
    PackageSearchApiClient.updateByIdCache(ScalaCompilerGroupId, Scala2CompilerArtifactId, Some(ApiPackage(ScalaCompilerGroupId, Scala2CompilerArtifactId, scala2Versions)))
    PackageSearchApiClient.updateByIdCache(ScalaCompilerGroupId, Scala3CompilerArtifactId, Some(ApiPackage(ScalaCompilerGroupId, Scala3CompilerArtifactId, scala3Versions)))
  }

  private def doTest(fileText: String, resultText: String, version: String, invocationCount: Int = DefaultInvocationCount): Unit = {
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
    s"//> using scala $CARET",
    s"//> using scala $scala2StableVersion$CARET",
    scala2StableVersion
  )

  def testScala2Version2(): Unit = doTest(
    s"//> using scala 2.$CARET",
    s"//> using scala $scala2StableVersion$CARET",
    scala2StableVersion
  )

  def testNoCompletionForScala2UnstableVersion(): Unit = doTestNoCompletion(
    s"//> using scala $CARET",
    scala2UnstableVersion
  )

  def testNoCompletionForScala2UnstableVersion2(): Unit = doTestNoCompletion(
    s"//> using scala 2$CARET",
    scala2UnstableVersion
  )

  def testScala2UnstableVersionOnSecondInvocation(): Unit = doTest(
    s"//> using scala $CARET",
    s"//> using scala $scala2UnstableVersion$CARET",
    scala2UnstableVersion,
    invocationCount = 2
  )

  def testScala2UnstableVersionOnSecondInvocation2(): Unit = doTest(
    s"//> using scala 2.$CARET",
    s"//> using scala $scala2UnstableVersion$CARET",
    scala2UnstableVersion,
    invocationCount = 2
  )

  def testScala3Version(): Unit = doTest(
    s"//> using scala $CARET",
    s"//> using scala $scala3StableVersion$CARET",
    scala3StableVersion
  )

  def testScala3Version2(): Unit = doTest(
    s"//> using scala 3.$CARET",
    s"//> using scala $scala3StableVersion$CARET",
    scala3StableVersion
  )

  def testNoCompletionForScala3UnstableVersion(): Unit = doTestNoCompletion(
    s"//> using scala $CARET",
    scala3UnstableVersion
  )

  def testNoCompletionForScala3UnstableVersion2(): Unit = doTestNoCompletion(
    s"//> using scala 3$CARET",
    scala3UnstableVersion
  )

  def testScala3UnstableVersionOnSecondInvocation(): Unit = doTest(
    s"//> using scala $CARET",
    s"//> using scala $scala3UnstableVersion$CARET",
    scala3UnstableVersion,
    invocationCount = 2
  )

  def testScala3UnstableVersionOnSecondInvocation2(): Unit = doTest(
    s"//> using scala 3.$CARET",
    s"//> using scala $scala3UnstableVersion$CARET",
    scala3UnstableVersion,
    invocationCount = 2
  )
}
