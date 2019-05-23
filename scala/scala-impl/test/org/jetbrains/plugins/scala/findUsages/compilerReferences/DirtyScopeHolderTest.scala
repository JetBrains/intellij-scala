package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaDirtyScopeHolder.ScopedModule
import org.junit.Assert._

class DirtyScopeHolderTest extends ScalaCompilerReferenceServiceFixture {
  private[this] var moduleA: Module = _
  private[this] var moduleB: Module = _

  override def setUp(): Unit = {
    super.setUp()
    moduleA = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType, "A", myFixture.getTempDirFixture.findOrCreateDir("A"))
    moduleB = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType, "B", myFixture.getTempDirFixture.findOrCreateDir("B"))
    PsiTestUtil.addSourceRoot(moduleA, myFixture.getTempDirFixture.findOrCreateDir("A/test"), true)
    PsiTestUtil.addSourceRoot(moduleB, myFixture.getTempDirFixture.findOrCreateDir("B/test"), true)
    ModuleRootModificationUtil.addDependency(moduleA, getModule)
    ModuleRootModificationUtil.addDependency(moduleB, getModule)
    setUpLibrariesFor(moduleA, moduleB)
  }

  override def tearDown(): Unit = {
    moduleA = null
    moduleB = null
    super.tearDown()
  }

  private[this] def dirtyScopes: Set[ScopedModule] = ScalaCompilerReferenceService(getProject).getDirtyScopeHolder.dirtyScopes

  private def moduleScopes(m: Module): Set[ScopedModule] = Set(ScopedModule.compile(m), ScopedModule.test(m))

  def testNoChanges(): Unit = {
    myFixture.addFileToProject("Foo.scala", "trait Foo")
    myFixture.addFileToProject("A/Bar.scala", "trait Bar extends Foo")
    myFixture.addFileToProject("B/Baz.scala", "trait Baz extends Foo")
    buildProject()
    assert(dirtyScopes.isEmpty)
  }

  def testRootModuleChange(): Unit = {
    val rootFile = myFixture.addFileToProject("Foo.scala", "trait Foo")
    myFixture.addFileToProject("A/Bar.scala", "trait Bar extends Foo")
    myFixture.addFileToProject("B/test/Baz.scala", "trait Baz extends Foo")
    buildProject()
    assertTrue(dirtyScopes.isEmpty)
    myFixture.openFileInEditor(rootFile.getVirtualFile)
    myFixture.`type`("/*bla bla bla*/")
    val scopes = Set(getModule, moduleA, moduleB).flatMap(moduleScopes) - ScopedModule.test(getModule)
    assertEquals(scopes, dirtyScopes)
    FileDocumentManager.getInstance().saveAllDocuments()
    assertEquals(scopes, dirtyScopes)
    compiler.compileModule(getModule)
    assertEquals(moduleScopes(moduleA) ++ moduleScopes(moduleB), dirtyScopes)
  }

  def testLeafModuleChanges(): Unit = {
    myFixture.addFileToProject("Foo.scala", "trait Foo")
    val aFile = myFixture.addFileToProject("A/test/Bar.scala", "trait Bar extends Foo")
    myFixture.addFileToProject("B/Baz.scala", "trait Baz extends Foo")
    buildProject()
    assertTrue(dirtyScopes.isEmpty)
    myFixture.openFileInEditor(aFile.getVirtualFile)
    myFixture.`type`("/*bla bla bla*/")
    assertEquals(Set(ScopedModule.test(moduleA)), dirtyScopes)
    FileDocumentManager.getInstance().saveAllDocuments()
    assertEquals(Set(ScopedModule.test(moduleA)), dirtyScopes)
    compiler.compileModule(moduleA)
    assert(dirtyScopes.isEmpty)
  }

  def testModulePathRename(): Unit = {
    myFixture.addFileToProject("A/Foo.scala", "trait Foo")
    buildProject()
    assertTrue(dirtyScopes.isEmpty)
    myFixture.renameElement(PsiManager.getInstance(getProject).findDirectory(myFixture.findFileInTempDir("A")), "XXX")
    assertEquals(moduleScopes(moduleA), dirtyScopes)
  }
}
