package org.jetbrains.sbt.project

import com.intellij.util.SystemProperties
import org.jetbrains.plugins.scala.project.sdkdetect.repository.CoursierPaths
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}
import org.jetbrains.sbt.project.ProjectStructureDsl._

object ProjectStructureTestUtils {

  private val systemHome = SystemProperties.getUserHome
  private val ivyCacheRootHome = withoutPathSuffix(systemHome) + "/.ivy2/cache"
  private val coursierCacheRoot = withoutPathSuffix(CoursierPaths.cacheDirectory.getAbsolutePath)

  private def withoutPathSuffix(path: String) =
    path.stripSuffix("/").stripSuffix("\\")

  private def coursierCacheArtifact(relativePath: String): String =
    coursierCacheRoot + "/https/repo1.maven.org/maven2/" + relativePath

  private def ivyCacheArtifact(relativePath: String): String =
    ivyCacheRootHome + "/" + relativePath

  private def coursierCacheArtifacts(relativePaths: String*): Seq[String] =
    relativePaths.map(coursierCacheArtifact)

  private def ivyCacheArtifacts(relativePaths: String*): Seq[String] =
    relativePaths.map(ivyCacheArtifact)

  private def createScalaLibraryName(scalaVersion: ScalaVersion): String =
    s"sbt: ${DependencyManagerBase.scalaLibraryDescription(scalaVersion)}:jar"

  def expectedScalaLibrary(scalaVersion: String): library = {
    val scalaVersionFromString = ScalaVersion.fromString(scalaVersion).get
    expectedScalaLibraryFromCoursier(scalaVersionFromString, createScalaLibraryName(scalaVersionFromString), isSdk = false)
  }

  def expectedScalaLibraryWithScalaSdk(scalaVersion: String): Seq[library] = {
    val scalaVersionFromString = ScalaVersion.fromString(scalaVersion).get
    val scalaLibrary = expectedScalaLibraryFromCoursier(scalaVersionFromString, createScalaLibraryName(scalaVersionFromString), isSdk = false)
    val scalaSdkLibrary = expectedScalaLibraryFromCoursier(scalaVersionFromString, s"sbt: scala-sdk-$scalaVersion")
    Seq(scalaLibrary, scalaSdkLibrary)
  }

  private def expectedScalaLibraryFromCoursier(scalaVersion: ScalaVersion, libraryName: String, isSdk: Boolean = true): library = {
    val scalaVersionStr = scalaVersion.minor

    if (scalaVersion.languageLevel.isScala2)
      new library(libraryName) {
        if (isSdk) scalaSdkSettings := Some(ScalaSdkAttributes(
          scalaVersion.languageLevel,
          classpath = coursierCacheArtifacts(
            // TODO: build expected classpath depending on scalaVersion, currently extra classpath tested only for 2.13.5, 2.13.6
            "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
            "org/jline/jline/3.19.0/jline-3.19.0.jar",
            s"org/scala-lang/scala-compiler/$scalaVersionStr/scala-compiler-$scalaVersionStr.jar",
            s"org/scala-lang/scala-library/$scalaVersionStr/scala-library-$scalaVersionStr.jar",
            s"org/scala-lang/scala-reflect/$scalaVersionStr/scala-reflect-$scalaVersionStr.jar",
          ),
          extraClasspath = Nil,
        ))
        else {
          libClasses := coursierCacheArtifacts(s"org/scala-lang/scala-library/$scalaVersionStr/scala-library-$scalaVersionStr.jar")
          libSources := coursierCacheArtifacts(s"org/scala-lang/scala-library/$scalaVersionStr/scala-library-$scalaVersionStr-sources.jar")
          libJavadocs := coursierCacheArtifacts(s"org/scala-lang/scala-library/$scalaVersionStr/scala-library-$scalaVersionStr-javadoc.jar")
        }
      }
    else if (scalaVersion.minor == "3.0.2")
      new library(libraryName) {

        if (isSdk) scalaSdkSettings := Some(ScalaSdkAttributes(
          scalaVersion.languageLevel,
          classpath = coursierCacheArtifacts(
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
          extraClasspath = coursierCacheArtifacts(
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
        else {
          // TODO: this should also include scala-library (scala 2) after SCL-18867 and SCL-18866
          libClasses := coursierCacheArtifacts(s"org/scala-lang/scala3-library_3/3.0.2/scala3-library_3-3.0.2.jar")
          libSources := coursierCacheArtifacts(s"org/scala-lang/scala3-library_3/3.0.2/scala3-library_3-3.0.2-sources.jar")
          libJavadocs := coursierCacheArtifacts(s"org/scala-lang/scala3-library_3/3.0.2/scala3-library_3-3.0.2-javadoc.jar")
        }
      }
    else
      throw new IllegalArgumentException(s"""Unsupported expected scala version: $scalaVersion""".stripMargin)
  }

  def expectedScalaLibraryWithScalaSdkFromIvy(scalaVersion: String): Seq[library] = {
    val scalaVersionFromString = ScalaVersion.fromString(scalaVersion).get
    val scalaLibrary = expectedScalaLibraryFromIvy(scalaVersionFromString, createScalaLibraryName(scalaVersionFromString), isSdk = false)
    val scalaSdkLibrary = expectedScalaLibraryFromIvy(scalaVersionFromString, s"sbt: scala-sdk-$scalaVersion")
    Seq(scalaLibrary, scalaSdkLibrary)
  }

  private def expectedScalaLibraryFromIvy(
    scalaVersion: ScalaVersion,
    libraryName: String,
    isSdk: Boolean = true
  ): library = {
    val scalaVersionStr = scalaVersion.minor

    new library(libraryName) {
      if (isSdk) scalaSdkSettings := Some(ScalaSdkAttributes(
        scalaVersion.languageLevel,
        classpath = ivyCacheArtifacts(
          // TODO: build expected classpath depending on scalaVersion, currently extra classpath tested only for 2.12.10
          "jline/jline/jars/jline-2.14.6.jar",
          "org.fusesource.jansi/jansi/jars/jansi-1.12.jar",
          "org.scala-lang.modules/scala-xml_2.12/bundles/scala-xml_2.12-1.0.6.jar",
          "org.scala-lang/scala-compiler/jars/scala-compiler-2.12.10.jar",
          "org.scala-lang/scala-library/jars/scala-library-2.12.10.jar",
          "org.scala-lang/scala-reflect/jars/scala-reflect-2.12.10.jar",
        )
      ))
      else {
        libClasses := ivyCacheArtifacts(s"org.scala-lang/scala-library/jars/scala-library-$scalaVersionStr.jar")
        libSources := ivyCacheArtifacts(s"org.scala-lang/scala-library/srcs/scala-library-$scalaVersionStr-sources.jar")
        //For some reason IVY doesn't download javadocs: https://github.com/sbt/sbt/issues/5165#issuecomment-938817378
        //libJavadocs := ivyCacheArtifacts(s"org.scala-lang/scala-library/docs/scala-library-$scalaVersionStr-javadoc.jar")
      }
    }
  }

}
