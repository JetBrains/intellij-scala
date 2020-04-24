package org.jetbrains.plugins.scala.components.libextensions

import java.io.File

import org.jetbrains.plugins.scala.DependencyManagerBase.ResolvedDependency
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.components.libextensions.api.psi.Inspection
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._


class LibraryExtensionsManagerTest extends SimpleTestCase {

  private val jarPath = s"${TestUtils.getTestDataPath}/libextension/librarymanager-test-ijext.jar"

  private lazy val manager = LibraryExtensionsManager.getInstance(fixture.getProject)

  private def loadExtensions(): Unit = {
    manager.processResolvedExtension(new File(jarPath))
  }

  def testLoadFromJar(): Unit = {
    loadExtensions()
    assertEquals(s"No extension loaded from $jarPath", 3, manager.getExtensions(classOf[Inspection]).size)
  }

  def testIdeaVersionFiltering(): Unit = {
    loadExtensions()
    val extensions = manager.getExtensions(classOf[Inspection])
    assertFalse("Extension from outdated IDEA build has been loaded",
      extensions.exists(_.getClass.getSimpleName == "TestInspectionE"))
  }

  def testPluginIdFiltering(): Unit = {
    loadExtensions()
    val extensions = manager.getExtensions(classOf[Inspection])
    assertFalse("Extension for non-existing plugin has been loaded",
      extensions.exists(_.getClass.getSimpleName == "TestInspectionC"))
  }

  def testExplicitScalaExtension(): Unit = {
    loadExtensions()
    val extensions = manager.getExtensions(classOf[Inspection])
    assertTrue("Extension for non-existing plugin has been loaded",
      extensions.exists(_.getClass.getSimpleName == "TestInspectionD"))
  }

  def testVersionMatching(): Unit = {
    import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version
    import Version.parse
    val current = parse("2018.2.3")
    val old     = parse("2013.1.1")
    val future  = parse("2020.7.4")
    val since   = parse("2018.0.0")
    val until   = parse("2019.0.0")
    assertTrue(current.compareTo(since) >= 0 && current.compareTo(until) <= 0)
    assertFalse(old.compareTo(since) >= 0 && old.compareTo(until) <= 0)
    assertFalse(future.compareTo(since) >= 0 && future.compareTo(until) <= 0)
    assertTrue(current.compareTo(current) == 0)
  }

}
