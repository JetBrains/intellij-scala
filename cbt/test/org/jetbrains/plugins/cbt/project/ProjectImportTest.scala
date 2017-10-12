package org.jetbrains.plugins.cbt.project

import java.io.File
import java.nio.file._

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.{ModuleManager, Module => IdeaModule}
import com.intellij.openapi.project.{Project => IdeaProject}
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.{Library => IdeaLibrary}
import com.intellij.openapi.roots.{ModuleRootManager, OrderRootType}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.CommonProcessors.CollectProcessor
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.model.CbtProjectInfo._
import org.jetbrains.plugins.cbt.project.model.{CbtProjectConverter, CbtProjectInfo}
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.jetbrains.plugins.cbt.structure.CbtModuleExtData
import org.junit.Assert._
import org.junit.Test

import scala.collection.JavaConverters._

class ProjectImportTest {
  private val modulePrototype = CbtProjectInfo.Module(
    name = null,
    root = "ROOT".toFile,
    sourceDirs = Seq("ROOT/src".toFile),
    scalaVersion = "2.11.8",

    target = "ROOT/target".toFile,
    moduleType = ModuleType.Default,
    binaryDependencies = Seq(
      CbtProjectInfo.BinaryDependency("org.scala-lang:scala-library:2.11.8"),
      CbtProjectInfo.BinaryDependency("CBT")
    ),
    moduleDependencies = Seq.empty,
    classpath = Seq(
      "ROOT/fake_cbt_cache/lib1.jar".toFile,
      "ROOT/fake_cbt_cache/lib2.jar".toFile,
      "ROOT/fake_cbt_cache/lib3.jar".toFile
    ),
    parentBuild = None,
    scalacOptions = Seq(
      "-unchecked",
      "-deprecation"
    )
  )
  private val cbtLibs = Seq(
    Library(
      name = "CBT",
      jars = Seq(
        LibraryJar(
          jar = "ROOT/fake_cbt_cache/cbt.jar".toFile,
          jarType = JarType.Binary
        ),
        LibraryJar(
          jar = "ROOT/fake_cbt_cache/cbtLib.jar".toFile,
          jarType = JarType.Binary
        ),
        LibraryJar(
          jar = "ROOT/fake_cbt_cache/cbt-sources.jar".toFile,
          jarType = JarType.Source
        )
      )
    )
  )
  private val projectPrototype = Project(name = "NAME",
    root = "ROOT".toFile,
    modules = Seq.empty,
    libraries = Seq(
      Library(
        name = "org.scala-lang:scala-library:2.11.8",
        jars = Seq(
          LibraryJar(
            jar = "ROOT/fake_cbt_cache/scala-library-2.11.8.jar".toFile,
            jarType = JarType.Binary
          ),
          LibraryJar(
            jar = "ROOT/fake_cbt_cache/scala-library-2.11.8-sources.jar".toFile,
            jarType = JarType.Source
          )
        )
      )
    ),
    cbtLibraries = cbtLibs,
    scalaCompilers = Seq.empty)

  @Test
  def testSimple(): Unit = {
    val projectInfo = projectPrototype.copy(
      modules = Seq(
        modulePrototype.copy(
          name = "rootModule",
          moduleDependencies = Seq(CbtProjectInfo.ModuleDependency("otherModule"))
        ),
        modulePrototype.copy(
          name = "otherModule"
        )
      )
    )
    testProject("testSimple", projectInfo)
  }

  @Test
  def testMultipleModules(): Unit = {
    val projectInfo = projectPrototype.copy(
      modules = Seq(
        modulePrototype.copy(
          name = "module1",
          moduleDependencies = Seq(CbtProjectInfo.ModuleDependency("module2"))
        ),
        modulePrototype.copy(
          name = "module2",
          moduleDependencies = Seq(
            CbtProjectInfo.ModuleDependency("module3"),
            CbtProjectInfo.ModuleDependency("module4")
          )
        ),
        modulePrototype.copy(
          name = "module3",
          moduleDependencies = Seq(
            CbtProjectInfo.ModuleDependency("module4")
          )
        ),
        modulePrototype.copy(
          name = "module4"
        )
      )
    )
    testProject("multipleModules", projectInfo)
  }

  @Test
  def testMultipleLibraries(): Unit = {
    val projectInfo = projectPrototype.copy(
      modules = Seq(
        modulePrototype.copy(
          name = "rootModule",
          binaryDependencies = modulePrototype.binaryDependencies ++
            Seq(
              BinaryDependency("lib1"),
              BinaryDependency("lib2"),
              BinaryDependency("lib3")
            )
        )
      ),
      libraries = projectPrototype.libraries ++
        Seq(
          Library(
            name = "lib1",
            jars = Seq(
              LibraryJar(
                jar = "ROOT/fake_cbt_cache/lib1.jar".toFile,
                jarType = JarType.Binary
              ),
              LibraryJar(
                jar = "ROOT/fake_cbt_cache/lib1_.jar".toFile,
                jarType = JarType.Binary
              ),
              LibraryJar(
                jar = "ROOT/fake_cbt_cache/lib1-sources.jar".toFile,
                jarType = JarType.Source
              )
            )
          ),
          Library(
            name = "lib2",
            jars = Seq(
              LibraryJar(
                jar = "ROOT/fake_cbt_cache/lib2.jar".toFile,
                jarType = JarType.Binary
              ),
              LibraryJar(
                jar = "ROOT/fake_cbt_cache/lib2-sources.jar".toFile,
                jarType = JarType.Source
              )
            )
          ),
          Library(
            name = "lib3",
            jars = Seq(
              LibraryJar(
                jar = "ROOT/fake_cbt_cache/lib3.jar".toFile,
                jarType = JarType.Binary
              ),
              LibraryJar(
                jar = "ROOT/fake_cbt_cache/lib3_.jar".toFile,
                jarType = JarType.Binary
              ),
              LibraryJar(
                jar = "ROOT/fake_cbt_cache/lib3-sources.jar".toFile,
                jarType = JarType.Source
              ),
              LibraryJar(
                jar = "ROOT/fake_cbt_cache/lib3-sources_.jar".toFile,
                jarType = JarType.Source
              )
            )
          )
        )
    )
    testProject("multipleLibraries", projectInfo)
  }

  private def testProject(name: String, projectInfo: CbtProjectInfo.Project): Unit = {
    val testFixture =
      IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(name).getFixture
    testFixture.setUp()
    try {
      val project = testFixture.getProject
      val settings =
        new CbtExecutionSettings(project.getBasePath, false, true, true, Seq.empty)
      val projectInfoWithActualPaths = replacePaths(projectInfo, name, project.getBasePath)
      createJarFiles(projectInfoWithActualPaths)
      val projectNode = CbtProjectConverter(projectInfoWithActualPaths, settings)
        .map { node =>
          ServiceManager.getService(classOf[ProjectDataManager]).importData(node, project, true)
          node
        }
        .get
      projectEquals(project, projectNode, projectInfoWithActualPaths)
    } finally {
      EdtTestUtil.runInEdtAndWait(() =>
        testFixture.tearDown()
      )
    }
  }

  private def projectEquals(project: IdeaProject,
                            projectNode: DataNode[ProjectData],
                            projectInfo: CbtProjectInfo.Project): Unit = {
    def moduleEquals(module: IdeaModule, moduleInfo: CbtProjectInfo.Module): Unit = {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      val moduleNode = projectNode.getChildren.asScala
        .find { dn =>
          dn.getData() match {
            case md: ModuleData => md.getInternalName == moduleInfo.name
            case _ => false
          }
        }
        .get
      val extData = moduleNode.getChildren.asScala
        .find(_.getKey == CbtModuleExtData.Key)
        .map(_.getData(CbtModuleExtData.Key))
        .get

      def contentRootsEquals(): Unit = {
        val actulaSourceDirs =
          moduleRootManager.getContentEntries
            .flatMap(_.getSourceFolders)
            .map(_.getJpsElement.getFile)
            .toSet
        moduleInfo.sourceDirs.toSet safeEquals actulaSourceDirs
        moduleInfo.moduleDependencies.map(_.name).toSet safeEquals
          moduleRootManager.getDependencies.map(_.getName).toSet
      }

      def librariesEqual(): Unit = {
        val actual = {
          val collector = new CollectProcessor[IdeaLibrary]
          ApplicationManager.getApplication.runReadAction(runnable {
            moduleRootManager.getModifiableModel.orderEntries().librariesOnly()
              .forEachLibrary(collector)
          })
          collector.getResults.asScala
            .map (_.getName.stripPrefix("CBT: "))
            .toSet
        }
        val expected = moduleInfo.binaryDependencies
          .map(_.name)
          .toSet
        expected safeEquals actual
      }

      def scalacOptionsEqual(): Unit = {
        val expected = moduleInfo.scalacOptions.toSet
        val actual = extData.scalacOptions.toSet
        expected safeEquals actual
      }

      def scalaVersionsEquals(): Unit = {
        val expected = moduleInfo.scalaVersion
        val actual = extData.scalaVersion.presentation
        expected safeEquals actual
      }

      def moduleDependenciesEqual(): Unit = {
        val expected = moduleInfo.moduleDependencies
          .map(_.name)
          .toSet
        val actual = moduleRootManager.getDependencies
          .map(_.getName)
          .toSet
        expected safeEquals actual
      }

      def classpathEquals(): Unit = {
        val expected = moduleInfo.classpath.toSet
        val actual = extData.scalacClasspath.toSet
        expected safeEquals actual
      }

      moduleInfo.name safeEquals module.getName
      contentRootsEquals()
      librariesEqual()
      scalacOptionsEqual()
      moduleDependenciesEqual()
      classpathEquals()
      scalaVersionsEquals()
    }
    def librariesEquals(): Unit = {
      def toPath(vFile: VirtualFile) = Paths.get(vFile.getCanonicalPath.stripSuffix("!/"))

      def libraryEquals(library: IdeaLibrary, libraryInfo: CbtProjectInfo.Library): Unit = {
        libraryInfo.name safeEquals library.getName.stripPrefix("CBT: ")
        val actualJars = {
          val binaryJars = library.getFiles(OrderRootType.CLASSES)
            .map(j => (toPath(j), JarType.Binary))
            .toSet
          val sourceJars = library.getFiles(OrderRootType.SOURCES)
            .map(j => (toPath(j), JarType.Source))
            .toSet
          binaryJars ++ sourceJars
        }
        val expectedJars = libraryInfo.jars
          .map(j =>(j.jar.getCanonicalFile.toPath, j.jarType))
          .toSet
        expectedJars safeEquals actualJars
      }

      val libraries = ProjectLibraryTable.getInstance(project).getLibraries
        .toList
        .sortBy(_.getName)
      libraries.length safeEquals projectInfo.libraries.length
      libraries.zip(projectInfo.libraries.sortBy(_.name))
        .foreach{ case (l, li) => libraryEquals(l, li) }
    }
    val modules = ModuleManager.getInstance(project).getModules.toSeq.sortBy(_.getName)
    val moduleInfos = projectInfo.modules.sortBy(_.name)
    modules.length safeEquals moduleInfos.length
    modules.zip(moduleInfos).foreach { case (m, mi) => moduleEquals(m, mi) }
    librariesEquals()
  }

  private def replacePaths(project: CbtProjectInfo.Project,
                           name: String,
                           path: String): CbtProjectInfo.Project = {
    import CbtProjectInfo._
    def replace(obj: AnyRef): AnyRef = obj match {
      case project: Project =>
        project.copy(root = replace(project.root).asInstanceOf[File],
          modules = project.modules.map(replace(_).asInstanceOf[Module]),
          libraries = project.libraries.map(replace(_).asInstanceOf[Library]),
          cbtLibraries = project.cbtLibraries.map(replace(_).asInstanceOf[Library])
        )
      case module: Module =>
        module.copy(root = replace(module.root).asInstanceOf[File],
          sourceDirs = module.sourceDirs.map(replace(_).asInstanceOf[File]),
          target = replace(module.target).asInstanceOf[File]
        )
      case library: Library =>
        library.copy(jars = library.jars.map(replace(_).asInstanceOf[LibraryJar]))
      case libraryJar: LibraryJar =>
        libraryJar.copy(jar = replace(libraryJar.jar).asInstanceOf[File])
      case file: File =>
        file.getPath.replaceAllLiterally("NAME", name).replaceAllLiterally("ROOT", path).toFile
    }

    replace(project).asInstanceOf[Project]
  }

  private def createJarFiles(project: CbtProjectInfo.Project): Unit = {
    for (library <- project.libraries ++ project.cbtLibraries; jar <- library.jars) jar match {
      case CbtProjectInfo.LibraryJar(file, _) =>
        Files.createDirectories(file.toPath.getParent)
        file.createNewFile()
    }
  }

  implicit class SafeEquals[T](val expected: T) {
    def safeEquals(actual: T): Unit =
      assertEquals(expected, actual)
  }

}
