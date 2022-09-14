package org.jetbrains.plugins.scala.compiler

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.roots.{DependencyScope, ModuleRootModificationUtil}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyManagedLoader
import org.jetbrains.plugins.scala.compiler.references.ScalaCompilerReferenceServiceFixture

import java.io.File

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

    val remoteServerConnectorBase = new TestRemoteServerConnectorBase(moduleA, None, new java.io.File("/tmp"))

    val r = remoteServerConnectorBase.result()
    assert(r.exists(_.toString.contains("scalatest")))
  }

}

class TestRemoteServerConnectorBase (module: Module, filesToCompile: Option[Seq[File]], outputDir: File)
    extends RemoteServerConnectorBase(module, filesToCompile, outputDir) {
  def result(): Seq[java.io.File] = assemblyRuntimeClasspath()
}
