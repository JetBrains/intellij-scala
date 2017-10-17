package org.jetbrains.plugins.hocon
package includes

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots._
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures._
import org.jetbrains.jps.model.java.JavaSourceRootType

import scala.collection.JavaConverters._

/**
  * @author ghik
  */
class HoconMultiModuleIncludeResolutionTest extends UsefulTestCase with HoconIncludeResolutionTest {

  private var fixture: CodeInsightTestFixture = _

  override protected def rootPath = "testdata/hocon/includes/multimodule"

  import HoconIncludeResolutionTest.inWriteAction
  import HoconMultiModuleIncludeResolutionTest._

  override def setUp(): Unit = {
    super.setUp()

    val fixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(getName)

    val moduleBuilders = subdirectories(rootPath)
      .map((_, fixtureBuilder.addModule(classOf[JavaModuleFixtureBuilder[ModuleFixture]])))

    moduleBuilders.foreach {
      case (directory, builder) =>
        builder.addContentRoot(directory.getPath)

        def addLibrary(libraryName: String): Unit = {
          import OrderRootType._
          val mapping = Map(CLASSES -> "", SOURCES -> "src").mapValues { suffix =>
            Array(new File(directory, libraryName + suffix).getPath)
          }

          builder.addLibrary(directory.getName + libraryName, mapping.asJava)
        }

        addLibrary("lib")
        addLibrary("testlib")
    }

    fixture = JavaTestFixtureFactory.getFixtureFactory.createCodeInsightFixture(fixtureBuilder.getFixture)
    fixture.setUp()
    fixture.setTestDataPath(rootPath)

    inWriteAction {
      LocalFileSystem.getInstance().refresh(false)

      val modules = moduleBuilders.map {
        case (directory, builder) => (directory.getName, builder.getFixture.getModule)
      }.toMap

      val models = modules.mapValues { module =>
        ModuleRootManager.getInstance(module).getModifiableModel
      }
      models.values.foreach(setUpEntries)

      addDependency(models("modA"), modules("modB"))
      addDependency(models("modB"), modules("modC"))
    }
  }

  override def tearDown(): Unit = {
    fixture.tearDown()
    fixture = null
    super.tearDown()
  }

  def testIncludeFromLibrary(): Unit =
    checkFile("modC/src/including.conf")

  def testIncludeFromModuleDependency(): Unit =
    checkFile("modB/src/including.conf")

  def testIncludeFromTransitiveModuleDependency(): Unit =
    checkFile("modA/src/including.conf")

  def testIncludeInLibrary(): Unit =
    checkFile("modC/lib/including.conf")

  def testIncludeInLibraryFromModuleDependency(): Unit =
    checkFile("modB/lib/including.conf")

  def testIncludeInLibraryFromTransitiveModuleDependency(): Unit =
    checkFile("modA/lib/including.conf")

  def testIncludeInLibrarySources(): Unit =
    checkFile("modC/libsrc/including.conf")

  def testIncludeInLibrarySourcesFromModuleDependency(): Unit =
    checkFile("modB/libsrc/including.conf")

  def testIncludeInLibrarySourcesFromTransitiveModuleDependency(): Unit =
    checkFile("modA/libsrc/including.conf")

  def testIncludeInTestsFromLibrary(): Unit =
    checkFile("modC/testsrc/including.conf")

  def testIncludeInTestsFromModuleDependency(): Unit =
    checkFile("modB/testsrc/including.conf")

  def testIncludeInTestsFromTransitiveModuleDependency(): Unit =
    checkFile("modA/testsrc/including.conf")

  def testIncludeInTestLibrary(): Unit =
    checkFile("modC/testlib/including.conf")

  def testIncludeInTestLibraryFromModuleDependency(): Unit =
    checkFile("modB/testlib/including.conf")

  def testIncludeInTestLibraryFromTransitiveModuleDependency(): Unit =
    checkFile("modA/testlib/including.conf")

  def testIncludeInTestLibrarySources(): Unit =
    checkFile("modC/testlibsrc/including.conf")

  def testIncludeInTestLibrarySourcesFromModuleDependency(): Unit =
    checkFile("modB/testlibsrc/including.conf")

  def testIncludeInTestLibrarySourcesFromTransitiveModuleDependency(): Unit =
    checkFile("modA/testlibsrc/including.conf")

  def testIncludeFromNonSourceDirectory(): Unit =
    checkFile("modC/other/including.conf")

  private def checkFile(path: String): Unit =
    checkFile(path, fixture.getProject)
}

object HoconMultiModuleIncludeResolutionTest {

  private def subdirectories(path: String): Seq[File] =
    new File(path).listFiles
      .filter(_.isDirectory)
      .sortBy(_.getName)

  private def setUpEntries(model: ModifiableRootModel): Unit = {
    val contentEntry = model.getContentEntries.head

    def addSourceFolder(name: String, kind: JavaSourceRootType): Unit = {
      contentEntry.addSourceFolder(contentEntry.getFile.findChild(name), kind)
    }

    import JavaSourceRootType._
    addSourceFolder("src", SOURCE)
    addSourceFolder("testsrc", TEST_SOURCE)

    model.getOrderEntries.collect {
      case entry: LibraryOrderEntry if entry.getLibraryName.endsWith("testlib") => entry
    }.foreach(_.setScope(DependencyScope.TEST))

    model.commit()
  }

  private def addDependency(model: ModifiableRootModel, module: Module): Unit = {
    model.addModuleOrderEntry(module).setExported(true)
    model.commit()
  }
}
