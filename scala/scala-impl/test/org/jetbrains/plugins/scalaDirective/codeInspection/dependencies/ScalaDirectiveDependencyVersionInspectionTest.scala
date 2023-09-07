package org.jetbrains.plugins.scalaDirective.codeInspection.dependencies

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient
import org.jetbrains.plugins.scala.packagesearch.model.ApiPackage
import org.jetbrains.plugins.scalaDirective.ScalaDirectiveBundle

abstract class ScalaDirectiveDependencyVersionInspectionTestBase extends ScalaInspectionTestBase {
  private val groupId = "org.fancyname"
  private val artifactId = "cool-lib"
  private val versions = Seq(
    "2.0.12", "2.0.11", "2.1.0-RC1", "2.0.3", "2.0.13", "2.0.0-RC6",
    "2.0.10", "2.0.9", "2.0.8", "2.0.7", "1.0.18", "2.0.6", "2.0.5",
    "2.0.4", "1.0.17", "2.0.2", "2.0.1", "1.0.16", "2.0.0", "1.0.15",
    "1.0.14", "2.0.0-RC5", "2.0.0-RC4", "2.0.0-RC3", "2.0.0-RC2",
    "1.0.13", "2.0.0-RC1", "2.0.0-M6-2", "2.0.0-M6-1", "2.0.0-M6",
    "2.0.0-M5", "2.0.0-M4", "2.0.0-M3", "1.0.12", "2.0.0-M2", "1.0.11",
    "2.0.0-M1", "2.0.13-RC2", "1.0.10", "1.0.9", "1.0.8"
  )

  private val latestStableVersion = "2.0.13"
  private val unstableWithSameBaseVersionAsStable = "2.0.13-RC2"
  private val latestUnstableVersion = "2.1.0-RC1"
  private val outdatedStableVersion = "1.0.9"
  private val outdatedUnstableVersion = "2.0.0-M6-1"

  protected def scalaVersion: ScalaVersion
  protected def scalaVersionSuffix: String
  protected def fullScalaVersionSuffix: String = scalaVersion.minor

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaDirectiveDependencyVersionInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("packagesearch.newer.stable.version.available", groupId, artifactId)

  protected val hint =
    ScalaDirectiveBundle.message("packagesearch.update.dependency.to.newer.stable.version", latestStableVersion)

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  protected def preparePackageSearchCache(artifactId: String, versions: Option[Seq[String]]): Unit =
    PackageSearchApiClient.updateByIdCache(groupId, artifactId, versions.map(ApiPackage(groupId, artifactId, _)))

  protected def doTest(text: String, expected: String, artifactId: String = this.artifactId): Unit = {
    preparePackageSearchCache(artifactId, Some(versions))
    checkTextHasError(text)
    testQuickFix(text, expected, hint)
  }

  protected def doCheckNoErrors(text: String, versions: Option[Seq[String]] = Some(this.versions)): Unit = {
    preparePackageSearchCache(artifactId, versions)
    checkTextHasNoErrors(text)
  }

  def testOutdatedDependencyWithoutVersionSuffix(): Unit = doTest(
    text = s"//> using dep $START$groupId:$artifactId:$outdatedStableVersion$END",
    expected = s"//> using dep $groupId:$artifactId:$latestStableVersion"
  )

  def testOutdatedDependencyWithScalaVersionSuffix(): Unit = doTest(
    text = s"//> using dep $START$groupId::$artifactId:$outdatedStableVersion$END",
    expected = s"//> using dep $groupId::$artifactId:$latestStableVersion",
    artifactId = scalaVersionArtifactId
  )

  def testOutdatedDependencyWithFullScalaVersionSuffix(): Unit = doTest(
    text = s"//> using dep $START$groupId:::$artifactId:$outdatedStableVersion$END",
    expected = s"//> using dep $groupId:::$artifactId:$latestStableVersion",
    artifactId = fullScalaVersionArtifactId
  )

  def testOutdatedDependencyWithoutVersionSuffix_InDoubleQuotes(): Unit = doTest(
    text = s"""//> using dep $START"$groupId:$artifactId:$outdatedStableVersion"$END""",
    expected = s"""//> using dep "$groupId:$artifactId:$latestStableVersion""""
  )

  def testOutdatedDependencyWithScalaVersionSuffix_InDoubleQuotes(): Unit = doTest(
    text = s"""//> using dep $START"$groupId::$artifactId:$outdatedStableVersion"$END""",
    expected = s"""//> using dep "$groupId::$artifactId:$latestStableVersion"""",
    artifactId = scalaVersionArtifactId
  )

  def testOutdatedDependencyWithFullScalaVersionSuffix_InDoubleQuotes(): Unit = doTest(
    text = s"""//> using dep $START"$groupId:::$artifactId:$outdatedStableVersion"$END""",
    expected = s"""//> using dep "$groupId:::$artifactId:$latestStableVersion"""",
    artifactId = fullScalaVersionArtifactId
  )

  def testOutdatedDependencyWithoutVersionSuffix_InBackticks(): Unit = doTest(
    text = s"""//> using dep $START`$groupId:$artifactId:$outdatedStableVersion`$END""",
    expected = s"""//> using dep `$groupId:$artifactId:$latestStableVersion`"""
  )

  def testOutdatedDependencyWithScalaVersionSuffix_InBackticks(): Unit = doTest(
    text = s"""//> using dep $START`$groupId::$artifactId:$outdatedStableVersion`$END""",
    expected = s"""//> using dep `$groupId::$artifactId:$latestStableVersion`""",
    artifactId = scalaVersionArtifactId
  )

  def testOutdatedDependencyWithFullScalaVersionSuffix_InBackticks(): Unit = doTest(
    text = s"""//> using dep $START`$groupId:::$artifactId:$outdatedStableVersion`$END""",
    expected = s"""//> using dep `$groupId:::$artifactId:$latestStableVersion`""",
    artifactId = fullScalaVersionArtifactId
  )

  def testOutdatedDependencyWithUnstableVersion(): Unit = doTest(
    text = s"//> using dep $START$groupId:$artifactId:$outdatedUnstableVersion$END",
    expected = s"//> using dep $groupId:$artifactId:$latestStableVersion"
  )

  def testOutdatedDependencyWithUnstableVersion_SameBaseVersionAsLatestStable(): Unit = doTest(
    text = s"//> using dep $START$groupId:$artifactId:$unstableWithSameBaseVersionAsStable$END",
    expected = s"//> using dep $groupId:$artifactId:$latestStableVersion"
  )

  def testOutdatedDependencyInMultipleDependencyList(): Unit = doTest(
    text = s"//> using deps $START$groupId:$artifactId:$outdatedUnstableVersion$END $groupId:$artifactId:$latestStableVersion",
    expected = s"//> using deps $groupId:$artifactId:$latestStableVersion $groupId:$artifactId:$latestStableVersion"
  )

  def testOutdatedDependencyInMultipleDependencyList2(): Unit = doTest(
    text = s"//> using deps $groupId:$artifactId:$latestStableVersion $START$groupId:$artifactId:$outdatedUnstableVersion$END",
    expected = s"//> using deps $groupId:$artifactId:$latestStableVersion $groupId:$artifactId:$latestStableVersion"
  )

  def testOutdatedDependenciesInMultipleDependencyList(): Unit = {
    preparePackageSearchCache(artifactId, Some(versions))

    val text = s"//> using deps $START$groupId:$artifactId:$outdatedStableVersion$END $START$groupId:$artifactId:$outdatedUnstableVersion$END"
    checkTextHasError(text)

    val expected = s"//> using deps $groupId:$artifactId:$latestStableVersion $groupId:$artifactId:$latestStableVersion"
    testQuickFixAllInFile(text, expected, hint)
  }

  def testAliasKey_Deps(): Unit = doTest(
    text = s"//> using deps $START$groupId:$artifactId:$outdatedStableVersion$END",
    expected = s"//> using deps $groupId:$artifactId:$latestStableVersion"
  )

  def testAliasKey_Dependencies(): Unit = doTest(
    text = s"//> using dependencies $START$groupId:$artifactId:$outdatedStableVersion$END",
    expected = s"//> using dependencies $groupId:$artifactId:$latestStableVersion"
  )

  def testAliasKey_TestDep(): Unit = doTest(
    text = s"//> using test.dep $START$groupId:$artifactId:$outdatedStableVersion$END",
    expected = s"//> using test.dep $groupId:$artifactId:$latestStableVersion"
  )

  def testAliasKey_TestDeps(): Unit = doTest(
    text = s"//> using test.deps $START$groupId:$artifactId:$outdatedStableVersion$END",
    expected = s"//> using test.deps $groupId:$artifactId:$latestStableVersion"
  )

  def testAliasKey_TestDependencies(): Unit = doTest(
    text = s"//> using test.dependencies $START$groupId:$artifactId:$outdatedStableVersion$END",
    expected = s"//> using test.dependencies $groupId:$artifactId:$latestStableVersion"
  )

  def testAliasKey_CompileOnlyDep(): Unit = doTest(
    text = s"//> using compileOnly.dep $START$groupId:$artifactId:$outdatedStableVersion$END",
    expected = s"//> using compileOnly.dep $groupId:$artifactId:$latestStableVersion"
  )

  def testAliasKey_CompileOnlyDeps(): Unit = doTest(
    text = s"//> using compileOnly.deps $START$groupId:$artifactId:$outdatedStableVersion$END",
    expected = s"//> using compileOnly.deps $groupId:$artifactId:$latestStableVersion"
  )

  def testAliasKey_CompileOnlyDependencies(): Unit = doTest(
    text = s"//> using compileOnly.dependencies $START$groupId:$artifactId:$outdatedStableVersion$END",
    expected = s"//> using compileOnly.dependencies $groupId:$artifactId:$latestStableVersion"
  )

  def testOutdatedDependencyWithoutVersion(): Unit = doTest(
    text = s"//> using dep $START$groupId:$artifactId:$END",
    expected = s"//> using dep $groupId:$artifactId:$latestStableVersion"
  )

  def testNoErrorsAlreadyLatestStableVersion(): Unit = doCheckNoErrors(
    text = s"//> using dep $groupId:$artifactId:$latestStableVersion"
  )

  def testNoErrorsUnstableVersionGreaterThanLatestStableVersion(): Unit = doCheckNoErrors(
    text = s"//> using dep $groupId:$artifactId:$latestUnstableVersion"
  )

  def testNoErrorsKeyIsUnrelated(): Unit = doCheckNoErrors(
    text = s"//> using somekey $groupId:$artifactId:$outdatedStableVersion"
  )

  def testNoErrorsApiClientReturnedNothing(): Unit = doCheckNoErrors(
    text = s"//> using dep $groupId:$artifactId:$outdatedStableVersion",
    versions = None
  )

  def testNoErrorsWithoutVersionAndColon(): Unit = doCheckNoErrors(
    text = s"//> using dep $START$groupId:$artifactId$END"
  )

  private def scalaVersionArtifactId: String = s"${artifactId}_$scalaVersionSuffix"
  private def fullScalaVersionArtifactId: String = s"${artifactId}_$fullScalaVersionSuffix"
}

final class ScalaDirectiveDependencyVersionInspectionTest_2_13
  extends ScalaDirectiveDependencyVersionInspectionTestBase {
  override protected val scalaVersion: ScalaVersion = LatestScalaVersions.Scala_2_13
  override protected val scalaVersionSuffix: String = "2.13"
}

final class ScalaDirectiveDependencyVersionInspectionTest_3
  extends ScalaDirectiveDependencyVersionInspectionTestBase {
  override protected val scalaVersion: ScalaVersion = LatestScalaVersions.Scala_3
  override protected val scalaVersionSuffix: String = "3"
}
