package org.jetbrains.plugins.scala.compiler.references

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.lang.CompoundRuntimeException
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.references.ScalaDirtyScopeHolder.ScopedModule
import org.junit.Assert.*

import scala.compiletime.uninitialized

class DirtyScopeHolderTest_IdeaIncrementality extends DirtyScopeHolderTestBase {
  override protected def incrementalityType: IncrementalityType = IncrementalityType.IDEA
}

class DirtyScopeHolderTest_SbtIncrementality extends DirtyScopeHolderTestBase {
  override protected def incrementalityType: IncrementalityType = IncrementalityType.SBT
}

abstract class DirtyScopeHolderTestBase extends ScalaCompilerReferenceServiceFixture {
  private var moduleA: Module = uninitialized
  private var moduleB: Module = uninitialized

  override def setUp(): Unit = {
    super.setUp()
    moduleA = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType.asInstanceOf[ModuleType[? <: ModuleBuilder]], "A", myFixture.getTempDirFixture.findOrCreateDir("A"))
    moduleB = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType.asInstanceOf[ModuleType[? <: ModuleBuilder]], "B", myFixture.getTempDirFixture.findOrCreateDir("B"))
    PsiTestUtil.addSourceRoot(moduleA, myFixture.getTempDirFixture.findOrCreateDir("A/test"), true)
    PsiTestUtil.addSourceRoot(moduleB, myFixture.getTempDirFixture.findOrCreateDir("B/test"), true)
    ModuleRootModificationUtil.addDependency(moduleA, getModule)
    ModuleRootModificationUtil.addDependency(moduleB, getModule)
    setUpLibrariesFor(moduleA, moduleB)
  }

  override def tearDown(): Unit = {
    moduleA = null
    moduleB = null
    try super.tearDown()
    catch {
      case compound: CompoundRuntimeException if compound.getExceptions.size() == 1 =>
        compound.getExceptions.get(0) match {
          case ae: AssertionError if ae.getMessage.startsWith("Listeners leaked for interface com.intellij.openapi.editor.event.DocumentListener") =>
            // The DocumentListener is leaked by the grazie/AI Assistant plugin. The tests have already passed. Suppress the error.
          case _ =>
            // Otherwise rethrow the error.
            throw compound
        }
    }
  }

  private def dirtyScopes: Set[ScopedModule] = ScalaCompilerReferenceService(getProject).getDirtyScopeHolder.dirtyScopes

  private def moduleScopes(m: Module): Set[ScopedModule] = Set(ScopedModule.compile(m), ScopedModule.test(m))

  private def assertEmptyScopes(scopes: Set[ScopedModule]): Unit = {
    assertEquals("Dirty scopes are not empty", Set.empty, scopes)
  }

  def testNoChanges(): Unit = {
    myFixture.addFileToProject("Foo.scala", "trait Foo")
    myFixture.addFileToProject("A/Bar.scala", "trait Bar extends Foo")
    myFixture.addFileToProject("B/Baz.scala", "trait Baz extends Foo")
    buildProject()
    assertEmptyScopes(dirtyScopes)
  }

  def testRootModuleChange(): Unit = {
    val rootFile = myFixture.addFileToProject("Foo.scala", "trait Foo")
    myFixture.addFileToProject("A/Bar.scala", "trait Bar extends Foo")
    myFixture.addFileToProject("B/test/Baz.scala", "trait Baz extends Foo")
    buildProject()
    assertEmptyScopes(dirtyScopes)
    myFixture.openFileInEditor(rootFile.getVirtualFile)
    myFixture.`type`("/*bla bla bla*/")
    val scopes = Set(getModule, moduleA, moduleB).flatMap(moduleScopes) - ScopedModule.test(getModule)
    assertEquals(scopes, dirtyScopes)
    FileDocumentManager.getInstance().saveAllDocuments()
    assertEquals(scopes, dirtyScopes)
    buildModule(getModule)
    assertEquals(moduleScopes(moduleA) ++ moduleScopes(moduleB), dirtyScopes)
  }

  def testLeafModuleChanges(): Unit = {
    myFixture.addFileToProject("Foo.scala", "trait Foo")
    val aFile = myFixture.addFileToProject("A/test/Bar.scala", "trait Bar extends Foo")
    myFixture.addFileToProject("B/Baz.scala", "trait Baz extends Foo")
    buildProject()
    assertEmptyScopes(dirtyScopes)
    myFixture.openFileInEditor(aFile.getVirtualFile)
    myFixture.`type`("/*bla bla bla*/")
    assertEquals(Set(ScopedModule.test(moduleA)), dirtyScopes)
    FileDocumentManager.getInstance().saveAllDocuments()
    assertEquals(Set(ScopedModule.test(moduleA)), dirtyScopes)
    buildModule(moduleA)
    assertEmptyScopes(dirtyScopes)
  }

  def testModulePathRename(): Unit = {
    myFixture.addFileToProject("A/Foo.scala", "trait Foo")
    buildProject()
    assertEmptyScopes(dirtyScopes)
    myFixture.renameElement(PsiManager.getInstance(getProject).findDirectory(myFixture.findFileInTempDir("A")), "XXX")
    assertEquals(moduleScopes(moduleA), dirtyScopes)
  }
}
