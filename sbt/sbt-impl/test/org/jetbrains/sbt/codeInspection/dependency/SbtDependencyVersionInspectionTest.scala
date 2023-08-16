package org.jetbrains.sbt.codeInspection.dependency

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient
import org.jetbrains.plugins.scala.packagesearch.model.ApiPackage
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.codeInspection.SbtDependencyVersionInspection
import org.jetbrains.sbt.language.SbtFileType
import org.jetbrains.sbt.{MockSbtBuildModule, MockSbt_1_0, Sbt, SbtHighlightingUtil}

class SbtDependencyVersionInspectionTest extends ScalaInspectionTestBase with MockSbt_1_0 with MockSbtBuildModule {
  override val sbtVersion: Version = Sbt.LatestVersion
  override protected val classOfInspection = classOf[SbtDependencyVersionInspection]
  override protected val description: String = ""
  override protected val fileType: LanguageFileType = SbtFileType

  override protected def descriptionMatches(s: String): Boolean = Option(s).exists(x => x.startsWith("Newer stable version for") && x.endsWith("is available"))

  protected override def setUp(): Unit = {
    super.setUp()
    SbtHighlightingUtil.enableHighlightingOutsideBuildModule(getProject)
  }

  def testDependencyVersionInspection(): Unit = {
    val groupId = "org.scalatest"
    val artifactId = s"scalatest_${version.major}"

    PackageSearchApiClient.updateByIdCache(groupId, artifactId,
      Some(ApiPackage(groupId, artifactId, Seq("3.0.7", "3.0.8", "3.0.6"))))

    val text =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % $START"3.0.7"$END
     """.stripMargin
    val expected =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8"
     """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, expected, "Update dependency to newer stable version 3.0.8")
  }
}
