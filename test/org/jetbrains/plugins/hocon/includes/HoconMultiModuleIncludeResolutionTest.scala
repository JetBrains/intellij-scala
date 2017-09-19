package org.jetbrains.plugins.hocon.includes

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{DependencyScope, LibraryOrderEntry, OrderRootType}
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures._
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ModuleExt

import scala.collection.JavaConverters.mapAsJavaMapConverter

/**
 * @author ghik
 */
class HoconMultiModuleIncludeResolutionTest extends UsefulTestCase with HoconIncludeResolutionTest {
  private var fixture: CodeInsightTestFixture = null
  private var modules: Map[String, Module] = null
  private val testdataPath = "testdata/hocon/includes/multimodule"

  protected def project: Project = fixture.getProject

  protected def contentRoots: Array[VirtualFile] =
    Array(LocalFileSystem.getInstance.findFileByPath(testdataPath))

  override def setUp(): Unit = {
    super.setUp()

    val fixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(getName)
    fixture = JavaTestFixtureFactory.getFixtureFactory.createCodeInsightFixture(fixtureBuilder.getFixture)

    val baseDir = new File(testdataPath)
    val moduleDirs = baseDir.listFiles.sortBy(_.getName).iterator.filter(_.isDirectory)
    val moduleFixtures = moduleDirs.map { dir =>
      val builder = fixtureBuilder.addModule(classOf[JavaModuleFixtureBuilder[ModuleFixture]])
      builder.addContentRoot(dir.getPath)

      def subpath(name: String) = new File(dir, name).getPath
      def libMapping(lib: String) =
        Map(OrderRootType.CLASSES -> lib, OrderRootType.SOURCES -> (lib + "src"))
          .mapValues(s => Array(subpath(s))).asJava

      builder.addLibrary(dir.getName + "lib", libMapping("lib"))
      builder.addLibrary(dir.getName + "testlib", libMapping("testlib"))
      (dir.getName, builder.getFixture)
    }.toMap

    fixture.setUp()
    fixture.setTestDataPath(testdataPath)

    modules = moduleFixtures.mapValues(_.getModule)

    inWriteAction {
      LocalFileSystem.getInstance().refresh(false)

      modules.values
        .map(_.modifiableModel)
        .foreach { model =>
        val contentEntry = model.getContentEntries.head
        contentEntry.addSourceFolder(contentEntry.getFile.findChild("src"), JavaSourceRootType.SOURCE)
        contentEntry.addSourceFolder(contentEntry.getFile.findChild("testsrc"), JavaSourceRootType.TEST_SOURCE)
        model.getOrderEntries.foreach {
          case loe: LibraryOrderEntry if loe.getLibraryName.endsWith("testlib") =>
            loe.setScope(DependencyScope.TEST)
          case _ =>
        }
        model.commit()
      }

      def addDependency(dependingModule: Module, dependencyModule: Module): Unit = {
        val model = dependingModule.modifiableModel
        model.addModuleOrderEntry(dependencyModule).setExported(true)
        model.commit()
      }

      addDependency(modules("modA"), modules("modB"))
      addDependency(modules("modB"), modules("modC"))
    }
  }

  override def tearDown(): Unit = {
    modules = null
    fixture.tearDown()
    fixture = null
    super.tearDown()
  }

  def testIncludeFromLibrary(): Unit = {
    checkFile("modC/src/including.conf")
  }

  def testIncludeFromModuleDependency(): Unit = {
    checkFile("modB/src/including.conf")
  }

  def testIncludeFromTransitiveModuleDependency(): Unit = {
    checkFile("modA/src/including.conf")
  }

  def testIncludeInLibrary(): Unit = {
    checkFile("modC/lib/including.conf")
  }

  def testIncludeInLibraryFromModuleDependency(): Unit = {
    checkFile("modB/lib/including.conf")
  }

  def testIncludeInLibraryFromTransitiveModuleDependency(): Unit = {
    checkFile("modA/lib/including.conf")
  }

  def testIncludeInLibrarySources(): Unit = {
    checkFile("modC/libsrc/including.conf")
  }

  def testIncludeInLibrarySourcesFromModuleDependency(): Unit = {
    checkFile("modB/libsrc/including.conf")
  }

  def testIncludeInLibrarySourcesFromTransitiveModuleDependency(): Unit = {
    checkFile("modA/libsrc/including.conf")
  }

  def testIncludeInTestsFromLibrary(): Unit = {
    checkFile("modC/testsrc/including.conf")
  }

  def testIncludeInTestsFromModuleDependency(): Unit = {
    checkFile("modB/testsrc/including.conf")
  }

  def testIncludeInTestsFromTransitiveModuleDependency(): Unit = {
    checkFile("modA/testsrc/including.conf")
  }

  def testIncludeInTestLibrary(): Unit = {
    checkFile("modC/testlib/including.conf")
  }

  def testIncludeInTestLibraryFromModuleDependency(): Unit = {
    checkFile("modB/testlib/including.conf")
  }

  def testIncludeInTestLibraryFromTransitiveModuleDependency(): Unit = {
    checkFile("modA/testlib/including.conf")
  }

  def testIncludeInTestLibrarySources(): Unit = {
    checkFile("modC/testlibsrc/including.conf")
  }

  def testIncludeInTestLibrarySourcesFromModuleDependency(): Unit = {
    checkFile("modB/testlibsrc/including.conf")
  }

  def testIncludeInTestLibrarySourcesFromTransitiveModuleDependency(): Unit = {
    checkFile("modA/testlibsrc/including.conf")
  }

  def testIncludeFromNonSourceDirectory(): Unit = {
    checkFile("modC/other/including.conf")
  }
}
