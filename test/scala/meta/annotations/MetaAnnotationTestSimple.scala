package scala.meta.annotations

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.base.{DisposableScalaLibraryLoader, ScalaLightCodeInsightFixtureTestAdapter}
import org.jetbrains.plugins.scala.debugger.{Compilable, DebuggerTestUtil}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion
import org.jetbrains.plugins.scala.{ScalaFileType, extensions}

class MetaAnnotationTestSimple extends JavaCodeInsightFixtureTestCase with Compilable {

  override def getCompileableProject: Project = myFixture.getProject
  override def getMainModule: Module = myModule
  override def getRootDisposable: Disposable = myTestRootDisposable
  override def getTestName: String = getTestName(false)

  val metaVersion = "0.23.0"

  protected def scalaSdkVersion: ScalaSdkVersion = ScalaSdkVersion._2_11_8

  override def setUp(): Unit = {
    super.setUp()
    setUpCompler()
  }


  override def tearDown(): Unit = {
    shutdownCompiler()
    super.tearDown()
  }

  def getMetaLibraries: Seq[(String, String, String)] = {
    val scala = scalaSdkVersion.getMajor
    def getLibEntry(component: String) = {
      (s"scalameta-$component", s"org.scalameta/${component}_$scala/jars", s"${component}_$scala-$metaVersion.jar")
    }
    Seq("common", "dialects", "inline",
      "inputs", "parsers", "quasiquotes", "scalameta",
      "tokenizers", "tokens", "transversers", "trees").map(getLibEntry)
  }

  def compileMetaSource(source: String) = {
    val root = extensions.inWriteAction {
      Option(myFixture.getProject.getBaseDir.findChild("meta")).getOrElse(myFixture.getProject.getBaseDir.createChildDirectory(null, "meta"))
    }
    val metaModule = PsiTestUtil.addModule(myFixture.getProject, JavaModuleType.getModuleType, "meta", root)
    val loader = new DisposableScalaLibraryLoader(getProject, metaModule, null, true, Some(DebuggerTestUtil.findJdk8()))
    loader.loadScala(scalaSdkVersion)
    for ((name, folder, jarFile) <- getMetaLibraries) {
      addIvyCacheLibraryToModule(metaModule, name, folder, jarFile)
    }
    val profile = ScalaCompilerConfiguration.instanceIn(myFixture.getProject).defaultProfile
    val settings = profile.getSettings
    settings.plugins :+= s"${TestUtils.getIvyCachePath}/org.scalamacros/paradise_2.11.8/jars/paradise_2.11.8-3.0.0-SNAPSHOT.jar"
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
    make()
  }

  def testBasic(): Unit = {
    compileMetaSource(
      """
        |import scala.meta._
        |
        |class main extends scala.annotation.StaticAnnotation {
        |  inline def apply()(defn: Any) = meta {
        |    val q"object $name { ..$stats }" = defn
        |    val main = q"def main(args: Array[String]): Unit = { ..$stats }"
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
    ""
  }

}
