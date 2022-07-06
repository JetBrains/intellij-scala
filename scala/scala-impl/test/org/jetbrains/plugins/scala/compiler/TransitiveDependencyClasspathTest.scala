package org.jetbrains.plugins.scala.compiler


import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.{DependencyScope, ModuleRootModificationUtil}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyManagedLoader
import org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaCompilerReferenceServiceFixture

class TransitiveDependencyClasspathTest extends ScalaCompilerReferenceServiceFixture {
  def testClasspathIncludesTransitiveModules(): Unit = {
    val moduleA = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType, "A", myFixture.getTempDirFixture.findOrCreateDir("A"))
    val moduleB = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType, "B", myFixture.getTempDirFixture.findOrCreateDir("B"))
    ModuleRootModificationUtil.addDependency(moduleA, getModule)
    ModuleRootModificationUtil.addDependency(moduleB, getModule)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, DependencyScope.COMPILE, true)
    setUpLibrariesFor(moduleA, moduleB)

    val libLoader = IvyManagedLoader("org.scalatest" %% "scalatest" % "3.2.0")
    libLoader.init(moduleB, version)

    val remoteServerConnectorBase = new RemoteServerConnectorBase(moduleA, None, new java.io.File("/tmp")) {
      def getResult(): Seq[java.io.File] = assemblyRuntimeClasspath()
    }

    val r = remoteServerConnectorBase.getResult()
    assert(r.exists(_.toString.contains("scalatest")))
  }

}
