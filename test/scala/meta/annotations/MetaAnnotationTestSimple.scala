package scala.meta.annotations

import java.io.File

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.base.DisposableScalaLibraryLoader
import org.jetbrains.plugins.scala.debugger.{Compilable, DebuggerTestUtil}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}
import org.junit.Assert

import scala.meta.ScalametaUtils

class MetaAnnotationTestSimple extends JavaCodeInsightFixtureTestCase with Compilable with ScalametaUtils {

  override def getCompileableProject: Project = myFixture.getProject
  override def getMainModule: Module = myModule
  override def getRootDisposable: Disposable = getTestRootDisposable
  override def getTestName: String = getTestName(false)

  override def setUp(): Unit = {
    super.setUp()
    setUpCompler()
  }

  def compileMetaSource(source: String) = {
    val root = extensions.inWriteAction {
      Option(myFixture.getProject.getBaseDir.findChild("meta")).getOrElse(myFixture.getProject.getBaseDir.createChildDirectory(null, "meta"))
    }
    val metaModule = PsiTestUtil.addModule(myFixture.getProject, JavaModuleType.getModuleType, "meta", root)
    val loader = new DisposableScalaLibraryLoader(getProject, metaModule, null, true, Some(DebuggerTestUtil.findJdk8()))
    loader.loadScala(scalaSdkVersion)
    addAllMetaLibraries(metaModule)
    val profile = ScalaCompilerConfiguration.instanceIn(myFixture.getProject).defaultProfile
    val settings = profile.getSettings
    val paradisePath= s"${TestUtils.getIvyCachePath}/org.scalamacros/paradise_2.11.8/jars/paradise_2.11.8-3.0.0-SNAPSHOT.jar"
    assert(new File(paradisePath).exists(), "paradise plugin not found")
    settings.plugins :+= paradisePath
    profile.setSettings(settings)
    extensions.inWriteAction {
      val modifiableRootModel = ModuleRootManager.getInstance(myModule).getModifiableModel
      modifiableRootModel.addModuleOrderEntry(metaModule)
      modifiableRootModel.commit()
    }
    extensions.inWriteAction {
      val modifiableRootModel = ModuleRootManager.getInstance(metaModule).getModifiableModel
      modifiableRootModel.setSdk(DebuggerTestUtil.findJdk8())
      modifiableRootModel.commit()
    }
    VfsTestUtil.createFile(root, "meta.scala", source)
    try {
      make()
    } finally {
      shutdownCompiler()
    }
  }

  def testBasic(): Unit = {
    compileMetaSource(
      """
        |import scala.meta._
        |
        |class main extends scala.annotation.StaticAnnotation {
        |  inline def apply()(defn: Any) = meta {
        |    val q"object $name { ..$stats }" = defn
        |    val main = q"def myNewCoolMethod(args: Array[String]): Unit = { ..$stats }"
        |    q"object $name { $main }"
        |  }
        |}
      """.stripMargin
    )
    myFixture.configureByText(ScalaFileType.SCALA_FILE_TYPE,
      s"""
        |@main
        |object Foo {
        |  println("bar")
        |}
        |Foo.<caret>
      """.stripMargin)
    val result = myFixture.completeBasic()
    Assert.assertTrue(result.exists(_.getLookupString == "myNewCoolMethod"))
  }

}
