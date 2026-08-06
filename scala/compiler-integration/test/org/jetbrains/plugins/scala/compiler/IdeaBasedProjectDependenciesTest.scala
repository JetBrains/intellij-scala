package org.jetbrains.plugins.scala.compiler

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.roots.{LibraryOrderEntry, ModuleRootModificationUtil}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.HeavyPlatformTestCase.createChildDirectory
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyManagedLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class IdeaBasedProjectDependenciesTest extends ScalaCompilerTestBase  {

  override def defaultJdkVersion: LanguageLevel = LanguageLevel.JDK_17

  override protected def incrementalityType = IncrementalityType.IDEA

  def testMultiModuleIDEABasedProjectWithDependencies(): Unit = {
    addFileToProjectSources("AbstractGreeter.scala",
      s"""
         |import org.apache.commons.text.AlphabetConverter
         |
         |abstract class AbstractGreeter(override val greeting: String) extends Greeter {
         |  AlphabetConverter.createConverter(Array.empty, Array.empty, Array.empty)
         |}
         |
          """.stripMargin
    )

    val abstractModule = createAbstractModule()
    ModuleRootModificationUtil.addDependency(getModule, abstractModule)

    compiler.rebuild().assertNoProblems()
  }

  private def createAbstractModule(): Module = {
    val moduleName = "abstract"
    val moduleDirectory = createChildDirectory(getBaseDir, moduleName)
    val module = PsiTestUtil.addModule(
      getProject,
      JavaModuleType.getModuleType.asInstanceOf[ModuleType[_ <: ModuleBuilder]],
      moduleName,
      moduleDirectory
    )

    setUpLibraries(module)
    setUpApacheCommonsLibrary(module)

    val fileContent =
      s"""
         |import org.apache.commons.text.AlphabetConverter
         |
         |trait Greeter {
         |  def greeting: String
         |}
          """.stripMargin
    VfsTestUtil.createFile(moduleDirectory, "Greeter.scala", fileContent)

    module
  }

  private def setUpApacheCommonsLibrary(module: Module): Unit = {
    val artifactId = "commons-text"
    val libLoader = IvyManagedLoader("org.apache.commons" % artifactId % "1.12.0")
    libLoader.init(module, version)

    val isApacheCommonsLibrary = (orderEntry: LibraryOrderEntry) => Option(orderEntry.getLibraryName).exists(_.contains(artifactId))
    ModuleRootModificationUtil.updateModel(module, model =>
      model.getOrderEntries.collect { case orderEntry: LibraryOrderEntry if isApacheCommonsLibrary(orderEntry) =>
        orderEntry.setExported(true)
      }
    )
  }
}
