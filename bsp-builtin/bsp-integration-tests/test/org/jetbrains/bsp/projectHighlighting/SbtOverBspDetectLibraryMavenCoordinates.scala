package org.jetbrains.bsp.projectHighlighting

import com.intellij.java.library.{JavaLibraryUtil, MavenCoordinates}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.bsp.SbtOverBspExternalSystemImportingTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.*
import org.junit.Assert.{assertEquals, fail}

//
// ATTENTION!!!
// This test is effectively muted - for BSP it's hard to register maven coordinates as this information is lost during the import
// To propagate it, we would need to refactor a lot of stuff in BSP
// I noticed it only after adding the test so decided to leave it for the future, if someone wants to implement this
//
/**
 * Also see an SBT version:
 * org.jetbrains.sbt.project.SbtDetectLibraryMavenCoordinates
 */
class SbtOverBspDetectLibraryMavenCoordinates extends SbtOverBspExternalSystemImportingTestCase {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/registerLibraryMavenCoordinates"

  def testLibraryMavenCoordinatesAreDetected(): Unit = {
    // MUTED (see class comment)
    return

    importProject(false)

    val project = this.getMyProject
    val libraryRegistrar = LibraryTablesRegistrar.getInstance
    val actualLibraries = libraryRegistrar.getLibraryTable(project).getLibraries.toSeq

    def coordinates(groupId: String, artifactId: String, version: String): MavenCoordinates =
      new MavenCoordinates(groupId, artifactId, version)

    val expectedLibraryMavenCoordinates = Seq[(String, MavenCoordinates)](
      "BSP: scala3-library_3-3.3.3" -> coordinates("org.scala-lang", "scala3-library_3", "3.3.3"),
      "BSP: scala-library-2.13.12" -> coordinates("org.scala-lang", "scala-library", "2.13.12"),
      "BSP: scala-library-2.13.14" -> coordinates("org.scala-lang", "scala-library", "2.13.14"),
      "BSP: munit_3-1.2.1" -> coordinates("org.scalameta", "munit_3", "1.2.1"),
      "BSP: junit-4.13.2" -> coordinates("junit", "junit", "4.13.2"),
      "BSP: cats-core_2.13-2.13.0" -> coordinates("org.typelevel", "cats-core_2.13", "2.13.0"),
      // Currently, we don't register maven coordinates for Scala SDK, we do it only for libraries.
      // No specific reasons "why", mostly just to isolate the scope of changes.
      // We can revise it and add the coordinates to Scala SDK, especially if we see how it can help product features
      "BSP: scala-sdk-2.13.14" -> null,
      "BSP: scala-sdk-3.3.3" -> null
    )

    val expectedLibrariesNames =
      expectedLibraryMavenCoordinates.map(_._1)

    val actualLibrariesNames =
      actualLibraries.map(_.getName).distinct

    if ((expectedLibrariesNames.toSet -- actualLibrariesNames.toSet).nonEmpty) {
      assertCollectionEquals(
        "Not all expected libraries were found in project libraries (actual libraries in the diff view can contain extra libraries)",
        expectedLibrariesNames.sorted,
        actualLibrariesNames.sorted
      )
    }

    //
    // Test `com.intellij.java.library.JavaLibraryUtil.getMavenCoordinates`
    //
    val libNameToMavenCoordinates = actualLibraries
      .filter(lib => expectedLibrariesNames.contains(lib.getName))
      .map(lib => lib.getName -> JavaLibraryUtil.getMavenCoordinates(lib))

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
