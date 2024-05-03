package org.jetbrains.sbt.language.completion

import com.intellij.lang.properties.PropertiesFileType
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.DefaultInvocationCount
import org.jetbrains.plugins.scala.packagesearch.api.{PackageSearchClient, PackageSearchClientTesting}
import org.jetbrains.sbt.language.completion.SbtVersionCompletionContributor.{SbtGroupId, SbtLaunchArtifactId}

final class SbtVersionPropertyCompletionTest
  extends ScalaCompletionTestBase
    with PackageSearchClientTesting {

  protected override def setUp(): Unit = {
    super.setUp()
    scalaFixture.setDefaultFileType(PropertiesFileType.INSTANCE)
  }

  private val sbtUnstableVersion = "1.10.0-RC2"
  private val sbtStableVersion = "1.9.9"
  private val sbtVersions = Seq(sbtStableVersion, sbtUnstableVersion)

  private def setupCaches(): Unit = {
    PackageSearchClient.instance()
      .updateByIdCache(SbtGroupId, SbtLaunchArtifactId,
        apiMavenPackage(SbtGroupId, SbtLaunchArtifactId, versionsContainer(sbtUnstableVersion, Some(sbtStableVersion), sbtVersions)))
  }

  private def doTest(fileText: String, resultText: String,
                     version: String, invocationCount: Int = DefaultInvocationCount): Unit = {
    setupCaches()
    doCompletionTest(
      fileText = fileText,
      resultText = resultText,
      item = version,
      invocationCount = invocationCount,
    )
  }

  private def doTestNoCompletion(fileText: String, version: String): Unit = {
    setupCaches()
    checkNoBasicCompletion(
      fileText = fileText,
      item = version,
    )
  }

  def testSbtVersion(): Unit = doTest(
    s"sbt.version=$CARET",
    s"sbt.version=$sbtStableVersion$CARET",
    sbtStableVersion
  )

  def testSbtVersionKeepSpaces(): Unit = doTest(
    s"sbt.version= $CARET",
    s"sbt.version= $sbtStableVersion$CARET",
    sbtStableVersion
  )

  def testSbtVersionKeepSpaces2(): Unit = doTest(
    s"sbt.version =$CARET",
    s"sbt.version =$sbtStableVersion$CARET",
    sbtStableVersion
  )

  def testSbtVersionKeepSpaces3(): Unit = doTest(
    s"sbt.version = $CARET",
    s"sbt.version = $sbtStableVersion$CARET",
    sbtStableVersion
  )

  def testSbtVersion2(): Unit = doTest(
    s"sbt.version=1.$CARET",
    s"sbt.version=$sbtStableVersion$CARET",
    sbtStableVersion
  )

  def testSbtVersion3(): Unit = doTest(
    s"sbt.version=1.${CARET}9.0",
    s"sbt.version=$sbtStableVersion$CARET",
    sbtStableVersion
  )

  def testNoCompletionForSbtUnstableVersion(): Unit = doTestNoCompletion(
    s"sbt.version=$CARET",
    sbtUnstableVersion
  )

  def testNoCompletionForSbtUnstableVersion2(): Unit = doTestNoCompletion(
    s"sbt.version=1.$CARET",
    sbtUnstableVersion
  )

  def testSbtUnstableVersionOnSecondInvocation(): Unit = doTest(
    s" sbt.version=$CARET",
    s"sbt.version=$sbtUnstableVersion$CARET",
    sbtUnstableVersion,
    invocationCount = 2
  )

  def testSbtUnstableVersionOnSecondInvocation2(): Unit = doTest(
    s"sbt.version=1.$CARET",
    s"sbt.version=$sbtUnstableVersion$CARET",
    sbtUnstableVersion,
    invocationCount = 2
  )
}
