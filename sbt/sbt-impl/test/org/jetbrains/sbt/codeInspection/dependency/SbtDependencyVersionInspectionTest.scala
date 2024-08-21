package org.jetbrains.sbt.codeInspection.dependency

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchClientTesting
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.codeInspection.SbtDependencyVersionInspection
import org.jetbrains.sbt.language.SbtFileType
import org.jetbrains.sbt.{MockSbtBuildModule, MockSbt_1_0, Sbt, SbtBundle, SbtHighlightingUtil}

class SbtDependencyVersionInspectionTest
  extends ScalaInspectionTestBase
    with MockSbt_1_0
    with MockSbtBuildModule
    with PackageSearchClientTesting {
  override val sbtVersion: Version = Sbt.LatestVersion
  override protected val classOfInspection = classOf[SbtDependencyVersionInspection]
  override protected val description: String = ""
  override protected val fileType: LanguageFileType = SbtFileType

  override protected def descriptionMatches(s: String): Boolean = Option(s).exists(x => x.startsWith("Newer version for") && x.endsWith("is available"))

  protected override def setUp(): Unit = {
    super.setUp()
    SbtHighlightingUtil.enableHighlightingOutsideBuildModule(getProject)
  }

  private def quickFixHint(version: String): String =
    SbtBundle.message("packagesearch.update.dependency.to.newer.version", version)

  def testDependencyVersionInspection(): Unit = {
    val groupId = "org.scalatest"
    val artifactId = s"scalatest_${version.major}"

    DependencyUtil.updateMockVersionCompletionCache((groupId -> artifactId) -> Seq("3.0.7", "3.0.8", "3.0.6"))

    val text =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % $START"3.0.7"$END
     """.stripMargin
    val expected =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8"
     """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, expected, quickFixHint("3.0.8"))
  }

  def testDependencyVersionInspection_UpdateUnstableToGreaterStable(): Unit = {
    val groupId = "org.scalatest"
    val artifactId = s"scalatest_${version.major}"

    DependencyUtil.updateMockVersionCompletionCache((groupId -> artifactId) -> Seq("3.0.7", "3.0.7-RC1", "3.0.8", "3.0.8-RC2", "3.0.6"))

    val text =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % $START"3.0.7-RC1"$END
     """.stripMargin
    val expected =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8"
     """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, expected, quickFixHint("3.0.8"))
  }

  def testDependencyVersionInspection_UpdateUnstableToGreaterUnstableIfNoStableIsApplicable(): Unit = {
    val groupId = "org.scalatest"
    val artifactId = s"scalatest_${version.major}"

    DependencyUtil.updateMockVersionCompletionCache((groupId -> artifactId) -> Seq("3.0.7", "3.0.7-RC1", "3.0.8-RC2", "3.0.6"))

    val text =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % $START"3.0.7-RC1"$END
     """.stripMargin
    val expected =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8-RC2"
     """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, expected, quickFixHint("3.0.8-RC2"))
  }
}
