package org.jetbrains.plugins.scala.project.template

import junit.framework.TestCase
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}

import java.io.File

class ScalaVersionDownloadingDialogTest extends TestCase {

  private val dependencyManager = new DependencyManagerBase {}
  def testScala2VersionResolveResultJars(): Unit = {
    val scalaVersion = new ScalaVersion(ScalaLanguageLevel.Scala_2_11, "0")
    val scalaVersionResult = ScalaVersionDownloadingDialog.createScalaVersionResolveResult(scalaVersion, dependencyManager)
    val expectedJarNames = Seq(
      "scala-library-2.11.0-sources.jar",
      "scala-compiler-2.11.0.jar",
      "scala-library-2.11.0.jar",
      "scala-reflect-2.11.0.jar",
      "scala-xml_2.11-1.0.1.jar",
      "scala-parser-combinators_2.11-1.0.1.jar"
    )
    checkWhetherJarNamesAreEqual(scalaVersionResult.allJars, expectedJarNames)
  }

  def testScala3VersionResolveResultJars(): Unit = {
    val scalaVersion = new ScalaVersion(ScalaLanguageLevel.Scala_3_0, "0")
    val scalaVersionResult = ScalaVersionDownloadingDialog.createScalaVersionResolveResult(scalaVersion, dependencyManager)
    val expectedJarNames = Seq(
      //sources jars
      "scala3-library_3-3.0.0-sources.jar",
      "scala-library-2.13.5-sources.jar",
      //classes jars
      "scala3-library_3-3.0.0.jar",
      "scala-library-2.13.5.jar",

      "scala3-compiler_3-3.0.0.jar",
      "scala3-interfaces-3.0.0.jar",
      "tasty-core_3-3.0.0.jar",
      "scala-asm-9.1.0-scala-1.jar",
      "compiler-interface-1.3.5.jar",
      "protobuf-java-3.7.0.jar",
      "util-interface-1.3.0.jar",
      "jline-reader-3.19.0.jar",
      "jline-terminal-3.19.0.jar",
      "jline-terminal-jna-3.19.0.jar",
      "jna-5.3.1.jar",
    )
    checkWhetherJarNamesAreEqual(scalaVersionResult.allJars, expectedJarNames)
  }

  private def checkWhetherJarNamesAreEqual(allJars: Seq[File], expected: Seq[String]): Unit = {
    val jarsNames = allJars.map(_.getName).sorted
    val expectedOrdered = expected.sorted
    assertCollectionEquals("Downloaded jar names are not equal", expectedOrdered, jarsNames)
  }

}
