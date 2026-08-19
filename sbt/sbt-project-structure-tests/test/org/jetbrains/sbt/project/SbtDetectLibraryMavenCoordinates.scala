package org.jetbrains.sbt.project

import com.intellij.java.library.{JavaLibraryUtil, MavenCoordinates}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions._
import org.junit.Assert.{assertEquals, fail}

/**
 * Also see BSP version:
 * org.jetbrains.bsp.projectHighlighting.SbtOverBspDetectLibraryMavenCoordinates
 */
class SbtDetectLibraryMavenCoordinates extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/registerLibraryMavenCoordinates"

  // Resolve the testdata build's own dependencies through the JetBrains Maven Central mirror,
  // to avoid HTTP Error 429 Too Many Requests in the CI (SCL-25750).
  override protected def overrideBuildRepositories: Boolean = true

  def testLibraryMavenCoordinatesAreDetected(): Unit = {
    importProject(false)

    val project = this.getMyProject
    val libraryRegistrar = LibraryTablesRegistrar.getInstance
    val libraries = libraryRegistrar.getLibraryTable(project).getLibraries.toSeq

    def coordinates(groupId: String, artifactId: String, version: String): MavenCoordinates =
      new MavenCoordinates(groupId, artifactId, version)

    val expectedLibraryMavenCoordinates = Seq[(String, MavenCoordinates)](
      "sbt: org.scala-lang:scala3-library_3:3.3.3:jar" -> coordinates("org.scala-lang", "scala3-library_3", "3.3.3"),
      "sbt: org.scala-lang:scala-library:2.13.12:jar" -> coordinates("org.scala-lang", "scala-library", "2.13.12"),
      "sbt: org.scala-lang:scala-library:2.13.14:jar" -> coordinates("org.scala-lang", "scala-library", "2.13.14"),
      "sbt: org.scalameta:munit_3:1.2.1:jar" -> coordinates("org.scalameta", "munit_3", "1.2.1"),
      "sbt: junit:junit:4.13.2:jar" -> coordinates("junit", "junit", "4.13.2"),
      "sbt: org.typelevel:cats-core_2.13:2.13.0:jar" -> coordinates("org.typelevel", "cats-core_2.13", "2.13.0"),
      // Currently, we don't register maven coordinates for Scala SDK, we do it only for libraries.
      // No specific reasons "why", mostly just to isolate the scope of changes.
      // We can revise it and add the coordinates to Scala SDK, especially if we see how it can help product features
      "sbt: scala-sdk-2.13.14" -> null,
      "sbt: scala-sdk-3.3.3" -> null
    )
    //
    // Test `com.intellij.java.library.JavaLibraryUtil.getMavenCoordinates`
    //
    val libNameToMavenCoordinates = libraries.map { lib => lib.getName -> JavaLibraryUtil.getMavenCoordinates(lib) }
    assertCollectionEquals(
      "Project libraries maven coordinates",
      expectedLibraryMavenCoordinates.sortBy(_._1),
      libNameToMavenCoordinates.sortBy(_._1)
    )

    //
    // Test `com.intellij.java.library.JavaLibraryUtil.getLibraryVersion`
    //
    val allModules = ModuleManager.getInstance(project).getModules

    val scala213Module = findModule(allModules, "root.withScala213.main")
    val scala33Module = findModule(allModules, "root.withScala33.main")

    assertLibraryVersion(scala33Module, "org.scala-lang:scala3-library_3", "3.3.3")
    assertLibraryVersion(scala33Module, "org.scalameta:munit_3", "1.2.1")
    assertLibraryVersion(scala33Module, "junit:junit", "4.13.2")
    assertLibraryVersion(scala33Module, "org.scala-lang:scala-library", "2.13.12")
    assertLibraryVersion(scala213Module, "org.scala-lang:scala-library", "2.13.14")
    assertLibraryVersion(scala213Module, "org.typelevel:cats-core_2.13", "2.13.0")
  }

  private def findModule(modules: Array[Module], name: String): Module =
    modules.find(_.getName == name).getOrElse {
      fail(s"$name module not found in project modules: ${modules.map(_.getName).mkString(",")}").asInstanceOf[Nothing]
    }

  private def assertLibraryVersion(module: Module, libraryCoordinates: String, expectedVersion: String): Unit = {
    val actualVersion = JavaLibraryUtil.getLibraryVersion(module, libraryCoordinates)
    assertEquals(
      s"Library $libraryCoordinates version in $module",
      expectedVersion,
      actualVersion,
    )
  }
}
