package scala.meta.annotations

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.base.{DisposableScalaLibraryLoader, ScalaFixtureTestCase}
import org.jetbrains.plugins.scala.debugger.{Compilable, DebuggerTestUtil}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

import scala.meta.ScalametaUtils

abstract class MetaAnnotationTestBase extends ScalaFixtureTestCase(scalaVersion = ScalaSdkVersion._2_11_8,
  loadReflect = true) with Compilable with ScalametaUtils {

  override def getCompileableProject: Project = myFixture.getProject
  override def getMainModule: Module = myFixture.getModule
  override def getRootDisposable: Disposable = getTestRootDisposable
  override def getTestName: String = getTestName(false)
  override protected def rootPath = null
  val metaSourceRoot = "meta"

  lazy val root = extensions.inWriteAction {
    Option(myFixture.getProject.getBaseDir.findChild(metaSourceRoot))
      .getOrElse(myFixture.getProject.getBaseDir.createChildDirectory(null, metaSourceRoot))
  }

  override def setUp(): Unit = {
    super.setUp()
    setUpCompler()
    val metaModule = PsiTestUtil.addModule(myFixture.getProject, JavaModuleType.getModuleType, metaSourceRoot, root)
    val loader = new DisposableScalaLibraryLoader(getProject, metaModule, null, true, Some(DebuggerTestUtil.findJdk8()))
    loader.loadScala(scalaSdkVersion)
    addAllMetaLibraries(metaModule)
    extensions.inWriteAction {
      val modifiableRootModel = ModuleRootManager.getInstance(myModule).getModifiableModel
      enableParadisePlugin(myFixture.getProject)
      modifiableRootModel.setSdk(DebuggerTestUtil.findJdk8())
      modifiableRootModel.addModuleOrderEntry(metaModule)
      modifiableRootModel.commit()
      val modifiableMetaRootModel = ModuleRootManager.getInstance(metaModule).getModifiableModel
      modifiableMetaRootModel.setSdk(DebuggerTestUtil.findJdk8())
      modifiableMetaRootModel.commit()
    }
  }

  def compileMetaSource(source: String) = {
    VfsTestUtil.createFile(root, "meta.scala", source)
    try {
      make()
    } finally {
      shutdownCompiler()
    }
  }

}
