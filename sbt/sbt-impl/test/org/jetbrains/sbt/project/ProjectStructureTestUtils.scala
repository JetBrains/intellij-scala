package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.{JavaModuleType, ModuleType}
import com.intellij.openapi.project.Project
import com.intellij.util.SystemProperties
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.sdkdetect.repository.CoursierPaths
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.settings.DisplayModuleName
import org.junit.Assert.{assertEquals, fail}

object ProjectStructureTestUtils {

  private val systemHome = SystemProperties.getUserHome

  private def ivyCacheRootHome(useEnv: Boolean): String =
    sys.env.get("TC_SBT_IVY_HOME").map(p => s"$p/cache").filter(_ => useEnv)
      .getOrElse(withoutPathSuffix(systemHome) + "/.ivy2/cache")

  private def coursierCacheRoot(useEnv: Boolean): String =
    sys.env.get("TC_SBT_COURSIER_HOME").map(p => s"$p/cache").filter(_ => useEnv)
      .getOrElse(withoutPathSuffix(CoursierPaths.cacheDirectory.toAbsolutePath.toString))

  private def withoutPathSuffix(path: String) =
    path.stripSuffix("/").stripSuffix("\\")

  private def coursierCacheArtifact(useEnv: Boolean)(relativePath: String): String =
    coursierCacheRoot(useEnv) + "/https/repo1.maven.org/maven2/" + relativePath

  private def ivyCacheArtifact(useEnv: Boolean)(relativePath: String): String =
    ivyCacheRootHome(useEnv) + "/" + relativePath

  private def coursierCacheArtifacts(useEnv: Boolean)(relativePaths: String*): Seq[String] =
    relativePaths.map(coursierCacheArtifact(useEnv))

  private def ivyCacheArtifacts(useEnv: Boolean)(relativePaths: String*): Seq[String] =
    relativePaths.map(ivyCacheArtifact(useEnv))

  private def createScalaLibraryName(scalaVersion: ScalaVersion, projectSystemId: ProjectSystemId): String = {
    val prefix = projectSystemId.getReadableName
    val insideValue =
      if (projectSystemId == SbtProjectSystem.Id) s"${DependencyManagerBase.scalaLibraryDescription(scalaVersion)}:jar"
      else createScalaLibraryTitleBSP(scalaVersion)

    s"$prefix: $insideValue"
  }

  private def createScalaLibraryTitleBSP(scalaVersion: ScalaVersion): String = {
    val id =
      if (scalaVersion.isScala3) "scala3-library_3"
      else "scala-library"

    s"$id-${scalaVersion.minor}"
  }

  def expectedScalaLibraryForSbt(useEnv: Boolean)(scalaVersion: String): library =
    expectedScalaLibrary(useEnv)(scalaVersion, SbtProjectSystem.Id)

  def expectedScalaLibrary(useEnv: Boolean)(scalaVersion: String, projectSystemId: ProjectSystemId): library = {
    val scalaVersionFromString = ScalaVersion.fromString(scalaVersion).get
    expectedScalaLibraryFromCoursier(useEnv)(scalaVersionFromString, createScalaLibraryName(scalaVersionFromString, projectSystemId))
  }

  def expectedScalaLibraryWithScalaSdkForSbt(useEnv: Boolean)(scalaVersion: String): Seq[library] =
    expectedScalaLibraryWithScalaSdk(useEnv)(scalaVersion, SbtProjectSystem.Id)

  def expectedScalaLibraryWithScalaSdk(useEnv: Boolean)(scalaVersionStr: String, projectSystemId: ProjectSystemId): Seq[library] = {
    val scalaVersion = ScalaVersion.fromString(scalaVersionStr).get

    val scalaLibrary = expectedScalaLibraryFromCoursier(useEnv: Boolean)(scalaVersion, createScalaLibraryName(scalaVersion, projectSystemId))
    val scalaLibraryTransitive = scalaVersionStr match {
      case "3.0.2" => Seq(expectedScalaLibrary(useEnv)("2.13.6", projectSystemId))
      case "3.3.3" => Seq(expectedScalaLibrary(useEnv)("2.13.12", projectSystemId))
      case "3.6.2" => Seq(expectedScalaLibrary(useEnv)("2.13.15", projectSystemId))
      case _ => Nil
    }
    val scalaSdkLibrary = expectedScalaSdkLibraryFromCoursier(useEnv: Boolean)(scalaVersion, projectSystemId)

    scalaSdkLibrary +: scalaLibraryTransitive :+ scalaLibrary
  }

  private def expectedScalaLibraryFromCoursier(useEnv: Boolean)(scalaVersion: ScalaVersion, libraryName: String): library = {
    val jars = expectedScalaLibraryJars(scalaVersion)
    new library(libraryName) {
      libClasses := coursierCacheArtifacts(useEnv)(jars.libClasses: _*)
      libSources := coursierCacheArtifacts(useEnv)(jars.libSources: _*)
      //SCL-8356
      //libJavadocs := coursierCacheArtifacts(s"org/scala-lang/$artifact/$version/$artifact-$version-javadoc.jar")
    }
  }

  /**
   * @param libClasses relative paths of library classes jars in Maven/Coursier format relative to the repo root
   * @param libSources relative paths of library sources jars in Maven/Coursier format relative to the repo root
   */
  case class ScalaLibraryJars(libClasses: Seq[String], libSources: Seq[String])

  def expectedScalaLibraryJars(version: ScalaVersion): ScalaLibraryJars = {
    val versionStr = version.minor
    val baseJarName = if (version.isScala2)
      s"org/scala-lang/scala-library/$versionStr/scala-library-$versionStr"
    else
      s"org/scala-lang/scala3-library_3/$versionStr/scala3-library_3-$versionStr"
    ScalaLibraryJars(
      Seq(s"$baseJarName.jar"),
      Seq(s"$baseJarName-sources.jar")
    )
  }

  private def expectedScalaSdkLibraryFromCoursier(useEnv: Boolean)(scalaVersion: ScalaVersion, projectSystemId: ProjectSystemId): library = {
    val scalaVersionStr = scalaVersion.minor

    val sdkLibraryName = s"${projectSystemId.getReadableName}: scala-sdk-$scalaVersionStr"
    val expectedData = ScalaSdkExpectedClasspath.Coursier.getForVersion(scalaVersion)
    new library(sdkLibraryName) {
      scalaSdkSettings := Some(toScalaSdkAttributesCoursier(expectedData, scalaVersion, useEnv, projectSystemId))
    }
  }

  def expectedScalaLibraryWithScalaSdkFromIvy(useEnv: Boolean)(scalaVersion: String): Seq[library] = {
    val scalaVersionFromString = ScalaVersion.fromString(scalaVersion).get
    val scalaLibrary = expectedScalaLibraryFromIvy(useEnv)(scalaVersionFromString, createScalaLibraryName(scalaVersionFromString, SbtProjectSystem.Id))
    val scalaSdkLibrary = expectedScalaSdkLibraryFromIvy(useEnv)(scalaVersionFromString, s"sbt: scala-sdk-$scalaVersion")
    Seq(scalaLibrary, scalaSdkLibrary)
  }

  /**
   *
   * @param expected map in which the keys are the full module names and the values are the display module names
   */
  def checkDisplayModuleNames(project: Project, expected: Map[String, String]): Unit = {
    val modules = project.modules.filter(ModuleType.get(_).getName == JavaModuleType.getModuleName)
    assertEquals("The amount of expected and actual modules is not the same", modules.size, expected.size)
    expected.foreach { case (fullName, expectedDisplayName) =>
      val moduleOpt = modules.find(_.getName == fullName)
      moduleOpt match {
        case Some(module) =>
          val displayName = DisplayModuleName.getInstance(module).name
          assertEquals("The expected display module name is different than the actual one", expectedDisplayName, displayName)
        case _ =>
          fail(s"The module called $fullName doesn't exist")
      }
    }
  }

  private def expectedScalaLibraryFromIvy(useEnv: Boolean)(scalaVersion: ScalaVersion, libraryName: String): library = {
    val scalaVersionStr = scalaVersion.minor

    new library(libraryName) {
      libClasses := ivyCacheArtifacts(useEnv)(s"org.scala-lang/scala-library/jars/scala-library-$scalaVersionStr.jar")
      libSources := ivyCacheArtifacts(useEnv)(s"org.scala-lang/scala-library/srcs/scala-library-$scalaVersionStr-sources.jar")
    }
  }

  private def expectedScalaSdkLibraryFromIvy(useEnv: Boolean)(scalaVersion: ScalaVersion, libraryName: String): library = {
    val expectedData = ScalaSdkExpectedClasspath.Ivy.getForVersion(scalaVersion)
    new library(libraryName) {
      scalaSdkSettings := Some(toScalaSdkAttributesIvy(expectedData, scalaVersion, useEnv, SbtProjectSystem.Id))
    }
  }

  private def toScalaSdkAttributesCoursier(
    expectedData: ScalaSdkExpectedClasspath,
    scalaVersion: ScalaVersion,
    useEnv: Boolean,
    projectSystemId: ProjectSystemId
  ): ScalaSdkAttributes = {
    toScalaSdkAttributesImpl(
      expectedData,
      scalaVersion,
      projectSystemId,
      relativePathToAbsolute = coursierCacheArtifact(useEnv)
    )
  }

  private def toScalaSdkAttributesIvy(
    expectedData: ScalaSdkExpectedClasspath,
    scalaVersion: ScalaVersion,
    useEnv: Boolean,
    projectSystemId: ProjectSystemId
  ): ScalaSdkAttributes = toScalaSdkAttributesImpl(
    expectedData,
    scalaVersion,
    projectSystemId,
    relativePathToAbsolute = ivyCacheArtifact(useEnv)
  )

  private def toScalaSdkAttributesImpl(
    expectedData: ScalaSdkExpectedClasspath,
    scalaVersion: ScalaVersion,
    projectSystemId: ProjectSystemId,
    relativePathToAbsolute: String => String
  ): ScalaSdkAttributes = {
    val classpathAbsolute = expectedData.classpath.map(relativePathToAbsolute)

    // note: in BSP extraClasspath is always empty
    val extraClasspathForBuildSystemAbsolut =
      if (projectSystemId == SbtProjectSystem.Id)
        expectedData.extraClasspath.map(relativePathToAbsolute)
      else
        Seq.empty

    ScalaSdkAttributes(
      scalaVersion.languageLevel,
      classpath = classpathAbsolute,
      extraClasspath = extraClasspathForBuildSystemAbsolut
    )
  }
}
