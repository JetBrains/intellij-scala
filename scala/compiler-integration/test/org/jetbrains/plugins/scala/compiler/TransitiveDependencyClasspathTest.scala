package org.jetbrains.plugins.scala.compiler

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.roots.{DependencyScope, ModuleRootModificationUtil}
import com.intellij.testFramework.PsiTestUtil
import junit.framework.TestCase.assertTrue
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyManagedLoader
import org.jetbrains.plugins.scala.compiler.references.ScalaCompilerReferenceServiceFixture

import java.nio.file.Path

class TransitiveDependencyClasspathTest extends ScalaCompilerReferenceServiceFixture {
  def testClasspathIncludesTransitiveModules(): Unit = {
    val moduleA = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType.asInstanceOf[ModuleType[_ <: ModuleBuilder]], "A", myFixture.getTempDirFixture.findOrCreateDir("A"))
    val moduleB = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType.asInstanceOf[ModuleType[_ <: ModuleBuilder]], "B", myFixture.getTempDirFixture.findOrCreateDir("B"))
    ModuleRootModificationUtil.addDependency(moduleA, getModule)
    ModuleRootModificationUtil.addDependency(moduleB, getModule)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, DependencyScope.COMPILE, true)
    setUpLibrariesFor(moduleA, moduleB)

    val libLoader = IvyManagedLoader("org.scalatest" %% "scalatest" % "3.2.0")
    libLoader.init(moduleB, version)

    val remoteServerConnectorBase = new TestRemoteServerConnectorBase(moduleA, Path.of(System.getProperty("java.io.tmpdir")))

    val r = remoteServerConnectorBase.result()
    assertTrue(r.exists(_.toString.contains("scalatest")))
  }

  private final class TestRemoteServerConnectorBase (module: Module, outputDir: Path)
    extends RemoteServerConnectorBase(module, None, outputDir) {
    def result(): Seq[Path] = assemblyRuntimeClasspath()
  }
}
