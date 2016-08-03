package scala.meta.annotations

import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.{PsiTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.base.DisposableScalaLibraryLoader
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

class MetaAnnotationTestSimple extends ScalaCompilerTestBase {

  val metaVersion = "0.23.0"

  override protected def scalaSdkVersion: ScalaSdkVersion = ScalaSdkVersion._2_11

  def getMetaLibraries: Seq[(String, String, String)] = {
    val scala = scalaSdkVersion.getMajor
    def getLibEntry(component: String) = {
      (s"scalameta-$component", s"org.scalameta/${component}_$scala/jars", s"${component}_$scala-$metaVersion.jar")
    }
    Seq("common", "dialects", "inline",
      "inputs", "parsers", "quasiquotes", "scalameta",
      "tokenizers", "tokens", "transversers", "trees").map(getLibEntry)
  }

  def compileMetaModule(source: String) = {
    val root = extensions.inWriteAction {
      Option(myProject.getBaseDir.findChild("meta")).getOrElse(myProject.getBaseDir.createChildDirectory(null, "meta"))
    }
    val metaModule = PsiTestUtil.addModule(myProject, JavaModuleType.getModuleType, "meta", root)
    val loader = new DisposableScalaLibraryLoader(getProject, metaModule, null, true, Some(getTestProjectJdk))
    loader.loadScala(scalaSdkVersion)
    for ((name, folder, jarFile) <- getMetaLibraries) {
      addIvyCacheLibrary(name, folder, jarFile)
    }
    val profile = ScalaCompilerConfiguration.instanceIn(myProject).defaultProfile
    val settings = profile.getSettings
    settings.plugins :+= s"${TestUtils.getIvyCachePath}/org.scalamacros/paradise_2.11.8/jars/paradise_2.11.8-3.0.0-M1.jar"
    profile.setSettings(settings)
    extensions.inWriteAction {
      val modifiableRootModel = ModuleRootManager.getInstance(myModule).getModifiableModel
      modifiableRootModel.addModuleOrderEntry(metaModule)
      modifiableRootModel.commit()
    }
    extensions.inWriteAction {
      val modifiableRootModel = ModuleRootManager.getInstance(metaModule).getModifiableModel
      modifiableRootModel.setSdk(getTestProjectJdk)
      modifiableRootModel.commit()
    }
    VfsTestUtil.createFile(root, "meta.scala", source)
    make()
    ""
  }

  def testBasic(): Unit = {
    compileMetaModule(
      """
        |package foo.bar
        |class Foo
        |/*import scala.annotation.StaticAnnotation
        |import scala.meta._
        |class main extends StaticAnnotation {
        |  inline def apply()(defn: Any) = meta {
        |    val q"..$mods object $name extends { ..$early } with ..$base { $self => ..$stats }" = defn
        |    val main = q"def main(args: Array[String]): Unit = { ..$stats }"
        |    q"..$mods object $name extends { ..$early } with ..$base { $self => $main }"
        |  }
        |}*/
        |
      """.stripMargin)
    ""
  }

}
