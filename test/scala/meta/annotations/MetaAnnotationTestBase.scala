package scala.meta.annotations

import java.io.File

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.base.DisposableScalaLibraryLoader
import org.jetbrains.plugins.scala.debugger.Compilable
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import scala.meta.ScalametaUtils

abstract class MetaAnnotationTestBase extends JavaCodeInsightFixtureTestCase with Compilable with ScalametaUtils {

  override def getCompileableProject: Project = myFixture.getProject
  override def getMainModule: Module = myModule
  override def getRootDisposable: Disposable = getTestRootDisposable
  override def getTestName: String = getTestName(false)

  override protected def getTestDataPath: String = TestUtils.getTestDataPath + "/scalameta"

  override def setUp(): Unit = {
    super.setUp()
    setUpCompler()
  }

  def compileMetaSource(source: String): List[String] = {
    implicit val project = getProject

    val root = extensions.inWriteAction {
      val baseDir = project.getBaseDir
      Option(baseDir.findChild("meta")).getOrElse(baseDir.createChildDirectory(null, "meta"))
    }

    implicit val metaModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType, "meta", root)
    val loader = new DisposableScalaLibraryLoader()
    loader.init(scalaSdkVersion)

    addAllMetaLibraries
    enableParadisePlugin

    extensions.inWriteAction {
      val modifiableRootModel = ModuleRootManager.getInstance(myModule).getModifiableModel
      modifiableRootModel.addModuleOrderEntry(metaModule)
      modifiableRootModel.commit()
    }

    VfsTestUtil.createFile(root, "meta.scala", source)
    try {
      make()
    } finally {
      shutdownCompiler()
    }
  }

  def compileMetaSource(): List[String] = compileMetaSource(FileUtil.loadFile(new File(getTestDataPath, s"$getTestName.scala")))

  def checkExpansionEquals(code: String, expectedExpansion: String): Unit = {
    myFixture.configureByText(s"Usage$getTestName.scala", code)
    val holder = ScalaPsiUtil.getParentOfType(myFixture.getElementAtCaret, classOf[ScAnnotationsHolder]).asInstanceOf[ScAnnotationsHolder]
    holder.getMetaExpansion match {
      case Right(tree)                      => Assert.assertEquals(expectedExpansion, tree.toString())
      case Left(reason) if reason.nonEmpty  => Assert.fail(reason)
      case Left("")                         => Assert.fail("Expansion was empty - did annotation even run?")
    }
  }
}
