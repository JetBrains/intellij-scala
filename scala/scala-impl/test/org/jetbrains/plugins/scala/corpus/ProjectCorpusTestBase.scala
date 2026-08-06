package org.jetbrains.plugins.scala.corpus

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiPackage
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, ScalaReflectLibraryLoader, SmartJDKLoader}
import org.jetbrains.plugins.scala.corpus.ProjectCorpusTestBase.ArtifactsWithoutSources
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path
import java.util.Collections
import scala.jdk.CollectionConverters.SeqHasAsJava


/**
 * Defines the configuration for a project corpus test
 */
abstract class ProjectCorpusTestDef {
  implicit val scalaVersion: ScalaVersion
  val packages: Seq[String]
  val dependencies: Seq[DependencyDescription]
  val includeScalaReflect: Boolean = false
  val includeScalaCompiler: Boolean = false
}

/**
 * Base class for project corpus tests that verify parsing and type-checking of external library code.
 *
 * This test base sets up a test project with the specified Scala version and external dependencies,
 * including both class files and source files (where available). It uses IvyManagedLoader to resolve
 * dependencies and automatically attaches them to the test module.
 *
 * The test ensures that:
 * - Dependencies are resolved for the correct Scala version
 * - Source files are attached when available (some artifacts are excluded via ArtifactsWithoutSources)
 * - JDK 17 is used as the project SDK
 * - The test only runs on compatible Scala versions (Scala 2 or Scala 3 matching the project definition)
 *
 * @param projectDef the project definition specifying Scala version, dependencies, and additional libraries
 */
@RunWith(classOf[JUnit4])
abstract class ProjectCorpusTestBase(val projectDef: ProjectCorpusTestDef) extends ScalaFixtureTestCase {

  override def getProject: Project = super.getProject
  def getMyFixture: CodeInsightTestFixture = myFixture

  override protected val includeCompilerAsLibrary = projectDef.includeScalaCompiler

  override protected def supportedIn(version: ScalaVersion) =
    version.isScala3 == projectDef.scalaVersion.isScala3 && version >= projectDef.scalaVersion

  override protected lazy val jdk: Sdk = SmartJDKLoader.createJdk(LanguageLevel.JDK_17)

  override def librariesLoaders =
    super.librariesLoaders :++
      (if (projectDef.includeScalaReflect) Seq(ScalaReflectLibraryLoader) else Seq.empty)

  override protected def setUpLibraries(module: Module): Unit = {
    super.setUpLibraries(module)

    val classes = IvyManagedLoader(projectDef.dependencies.map(_.transitive()): _*).resolve(version)
    val sources = IvyManagedLoader(
      classes.filter(it => !ArtifactsWithoutSources(it.info.org, it.info.artId)).map(_.info.sources()): _*
    ).resolve(version)
    classes.foreach { cls =>
      val source = sources.find(_.info == cls.info.sources())
      val classRoots = Collections.singletonList(findJarFile(cls.file))
      val sourceRoots = source.map(it => findJarFile(it.file)).toSeq.asJava
      PsiTestUtil.addProjectLibrary(module, cls.info.toString, classRoots, sourceRoots)
    }
  }

  private def findJarFile(file: Path): VirtualFile =
    JarFileSystem.getInstance.refreshAndFindFileByPath(file.toCanonicalPath.toString + "!/")

  protected def allClasses(excludePackages: Set[String]): Seq[ScTypeDefinition] = {
    val manager = ScalaPsiManager.instance(getProject)
    projectDef.packages
      .map(name => manager.getCachedPackage(name).getOrElse(throw new AssertionError(s"Package not found: $name")))
      .flatMap(classesIn(_, excludePackages))
  }

  protected def allSources(excludePackages: Set[String]): Set[ScFile] =
    allClasses(excludePackages)
      .map(_.getSourceMirrorClass.getContainingFile)
      .collect { case file: ScFile if !file.isCompiled => file }
      .toSet

  protected def classesIn(pkg: PsiPackage, excludePackages: Set[String]): Seq[ScTypeDefinition] = {
    val packageClasses = pkg.getClasses
      .collect { case c: ScTypeDefinition if c.isInCompiledFile && !(c.is[ScObject] && c.baseCompanion.isDefined) => c }
      .sortBy(_.qualifiedName)

    val subpackageClasses = pkg.getSubPackages
      .filter(pkg => !excludePackages(pkg.getQualifiedName))
      .sortBy(_.getQualifiedName)
      .flatMap(classesIn(_, excludePackages))

    packageClasses.toSeq ++ subpackageClasses.toSeq
  }
}

object ProjectCorpusTestBase {
  private object ArtifactsWithoutSources {
    private val artifacts = Set(
      ("com.google.guava", "listenablefuture"),
      ("guru.nidi", "graphviz-java-min-deps")
    )

    def apply(org: String, artId: String): Boolean = artifacts.contains((org, artId))
  }
}