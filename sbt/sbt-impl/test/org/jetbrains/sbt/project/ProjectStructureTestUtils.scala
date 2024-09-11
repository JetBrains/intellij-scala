package org.jetbrains.sbt.project

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
      .getOrElse(withoutPathSuffix(CoursierPaths.cacheDirectory.getAbsolutePath))

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

  private def createScalaLibraryName(scalaVersion: ScalaVersion): String =
    s"sbt: ${DependencyManagerBase.scalaLibraryDescription(scalaVersion)}:jar"

  def expectedScalaLibrary(useEnv: Boolean)(scalaVersion: String): library = {
    val scalaVersionFromString = ScalaVersion.fromString(scalaVersion).get
    expectedScalaLibraryFromCoursier(useEnv)(scalaVersionFromString, createScalaLibraryName(scalaVersionFromString))
  }

  def expectedScalaLibraryWithScalaSdk(useEnv: Boolean)(scalaVersion: String): Seq[library] = {
    val scalaVersionFromString = ScalaVersion.fromString(scalaVersion).get
    val scalaLibrary = expectedScalaLibraryFromCoursier(useEnv)(scalaVersionFromString, createScalaLibraryName(scalaVersionFromString))
    val scalaSdkLibrary = expectedScalaSdkLibraryFromCoursier(useEnv)(scalaVersionFromString)
    Seq(scalaLibrary, scalaSdkLibrary)
  }

  private def expectedScalaLibraryFromCoursier(useEnv: Boolean)(scalaVersion: ScalaVersion, libraryName: String): library = {
    val version = scalaVersion.minor
    val artifact = if (scalaVersion.languageLevel.isScala2) "scala-library" else "scala3-library_3"
    new library(libraryName) {
      libClasses := coursierCacheArtifacts(useEnv)(s"org/scala-lang/$artifact/$version/$artifact-$version.jar")
      libSources := coursierCacheArtifacts(useEnv)(s"org/scala-lang/$artifact/$version/$artifact-$version-sources.jar")
      //SCL-8356
      //libJavadocs := coursierCacheArtifacts(s"org/scala-lang/$artifact/$version/$artifact-$version-javadoc.jar")
    }
  }

  private def expectedScalaSdkLibraryFromCoursier(useEnv: Boolean)(scalaVersion: ScalaVersion): library = {
    val scalaVersionStr = scalaVersion.minor

    val sdkLibraryName = s"sbt: scala-sdk-$scalaVersionStr"
    if (Set("2.13.5", "2.13.6").contains(scalaVersionStr))
      new library(sdkLibraryName) {
        scalaSdkSettings := Some(ScalaSdkAttributes(
          scalaVersion.languageLevel,
          classpath = coursierCacheArtifacts(useEnv)(
            // TODO: build expected classpath depending on scalaVersion, currently extra classpath tested only for 2.13.5, 2.13.6
            "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
            "org/jline/jline/3.19.0/jline-3.19.0.jar",
            s"org/scala-lang/scala-compiler/$scalaVersionStr/scala-compiler-$scalaVersionStr.jar",
            s"org/scala-lang/scala-library/$scalaVersionStr/scala-library-$scalaVersionStr.jar",
            s"org/scala-lang/scala-reflect/$scalaVersionStr/scala-reflect-$scalaVersionStr.jar",
          ),
          extraClasspath = Nil,
        ))
      }
    else if (scalaVersionStr == "2.13.14")
      new library(sdkLibraryName) {
        scalaSdkSettings := Some(ScalaSdkAttributes(
          scalaVersion.languageLevel,
          classpath = coursierCacheArtifacts(useEnv)(
            // expected classpath depending on scalaVersion 2.13.14, used in `NewPlaySbtProjectWizardTest`
            "io/github/java-diff-utils/java-diff-utils/4.12/java-diff-utils-4.12.jar",
            "net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar",
            "org/jline/jline/3.25.1/jline-3.25.1.jar",
            "org/scala-lang/scala-compiler/2.13.14/scala-compiler-2.13.14.jar",
            "org/scala-lang/scala-library/2.13.14/scala-library-2.13.14.jar",
            "org/scala-lang/scala-reflect/2.13.14/scala-reflect-2.13.14.jar"
          ),
          extraClasspath = Nil
        ))
      }
    else if (scalaVersion.minor == "3.0.2")
      new library(sdkLibraryName) {
        scalaSdkSettings := Some(ScalaSdkAttributes(
          scalaVersion.languageLevel,
          classpath = coursierCacheArtifacts(useEnv)(
            """org/scala-lang/scala-library/2.13.6/scala-library-2.13.6.jar
              |org/scala-lang/scala3-library_3/3.0.2/scala3-library_3-3.0.2.jar
              |com/google/protobuf/protobuf-java/3.7.0/protobuf-java-3.7.0.jar
              |net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar
              |org/jline/jline-reader/3.19.0/jline-reader-3.19.0.jar
              |org/jline/jline-terminal-jna/3.19.0/jline-terminal-jna-3.19.0.jar
              |org/jline/jline-terminal/3.19.0/jline-terminal-3.19.0.jar
              |org/scala-lang/modules/scala-asm/9.1.0-scala-1/scala-asm-9.1.0-scala-1.jar
              |org/scala-lang/scala3-compiler_3/3.0.2/scala3-compiler_3-3.0.2.jar
              |org/scala-lang/scala3-interfaces/3.0.2/scala3-interfaces-3.0.2.jar
              |org/scala-lang/tasty-core_3/3.0.2/tasty-core_3-3.0.2.jar
              |org/scala-sbt/compiler-interface/1.3.5/compiler-interface-1.3.5.jar
              |org/scala-sbt/util-interface/1.3.0/util-interface-1.3.0.jar
              |""".stripMargin.linesIterator.filter(_.nonEmpty).toSeq: _*
          ),
          extraClasspath = coursierCacheArtifacts(useEnv)(
            """com/fasterxml/jackson/core/jackson-annotations/2.2.3/jackson-annotations-2.2.3.jar
              |com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar
              |com/fasterxml/jackson/core/jackson-databind/2.2.3/jackson-databind-2.2.3.jar
              |com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.9.8/jackson-dataformat-yaml-2.9.8.jar
              |com/vladsch/flexmark/flexmark-ext-anchorlink/0.42.12/flexmark-ext-anchorlink-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-autolink/0.42.12/flexmark-ext-autolink-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-emoji/0.42.12/flexmark-ext-emoji-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-gfm-strikethrough/0.42.12/flexmark-ext-gfm-strikethrough-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-gfm-tables/0.42.12/flexmark-ext-gfm-tables-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-gfm-tasklist/0.42.12/flexmark-ext-gfm-tasklist-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-ins/0.42.12/flexmark-ext-ins-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-superscript/0.42.12/flexmark-ext-superscript-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-tables/0.42.12/flexmark-ext-tables-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-wikilink/0.42.12/flexmark-ext-wikilink-0.42.12.jar
              |com/vladsch/flexmark/flexmark-ext-yaml-front-matter/0.42.12/flexmark-ext-yaml-front-matter-0.42.12.jar
              |com/vladsch/flexmark/flexmark-formatter/0.42.12/flexmark-formatter-0.42.12.jar
              |com/vladsch/flexmark/flexmark-html-parser/0.42.12/flexmark-html-parser-0.42.12.jar
              |com/vladsch/flexmark/flexmark-jira-converter/0.42.12/flexmark-jira-converter-0.42.12.jar
              |com/vladsch/flexmark/flexmark-util/0.42.12/flexmark-util-0.42.12.jar
              |com/vladsch/flexmark/flexmark/0.42.12/flexmark-0.42.12.jar
              |nl/big-o/liqp/0.6.7/liqp-0.6.7.jar
              |org/antlr/ST4/4.0.7/ST4-4.0.7.jar
              |org/antlr/antlr-runtime/3.5.1/antlr-runtime-3.5.1.jar
              |org/antlr/antlr/3.5.1/antlr-3.5.1.jar
              |org/jsoup/jsoup/1.13.1/jsoup-1.13.1.jar
              |org/nibor/autolink/autolink/0.6.0/autolink-0.6.0.jar
              |org/scala-lang/scala3-tasty-inspector_3/3.0.2/scala3-tasty-inspector_3-3.0.2.jar
              |org/scala-lang/scaladoc_3/3.0.2/scaladoc_3-3.0.2.jar
              |org/yaml/snakeyaml/1.23/snakeyaml-1.23.jar
              |""".stripMargin.stripMargin.linesIterator.filter(_.nonEmpty).toSeq: _*
          ),
        ))
      }
    else
      throw new IllegalArgumentException(s"""Unsupported expected scala version: $scalaVersion""".stripMargin)
  }

  def expectedScalaLibraryWithScalaSdkFromIvy(useEnv: Boolean)(scalaVersion: String): Seq[library] = {
    val scalaVersionFromString = ScalaVersion.fromString(scalaVersion).get
    val scalaLibrary = expectedScalaLibraryFromIvy(useEnv)(scalaVersionFromString, createScalaLibraryName(scalaVersionFromString))
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
        case _ => fail(s"The module called $fullName doesn't exist")
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
    new library(libraryName) {
      scalaSdkSettings := Some(ScalaSdkAttributes(
        scalaVersion.languageLevel,
        classpath = ivyCacheArtifacts(useEnv)(
          // TODO: build expected classpath depending on scalaVersion, currently extra classpath tested only for 2.12.10
          "jline/jline/jars/jline-2.14.6.jar",
          "org.fusesource.jansi/jansi/jars/jansi-1.12.jar",
          "org.scala-lang.modules/scala-xml_2.12/bundles/scala-xml_2.12-1.0.6.jar",
          "org.scala-lang/scala-compiler/jars/scala-compiler-2.12.10.jar",
          "org.scala-lang/scala-library/jars/scala-library-2.12.10.jar",
          "org.scala-lang/scala-reflect/jars/scala-reflect-2.12.10.jar",
        )
      ))
    }
  }

}
