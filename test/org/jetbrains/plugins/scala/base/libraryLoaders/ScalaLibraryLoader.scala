package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.{JarFileSystem, VirtualFile}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaLoader
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyLibraryLoader._
import org.jetbrains.plugins.scala.debugger.ScalaVersion
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.project.template.Artifact.ScalaCompiler.versionOf
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.JavaConverters._

case class ScalaLibraryLoader(isIncludeReflectLibrary: Boolean = false)
                             (implicit val module: Module)
  extends LibraryLoader {

  import ScalaLibraryLoader._

  private var library: Library = _
  private val syntheticClassesLoader = SyntheticClassesLoader()

  def init(implicit version: ScalaVersion): Unit = {
    syntheticClassesLoader.init

    addScalaSdk
    LibraryLoader.storePointers()
  }

  override def clean(): Unit = {
    if (library != null) {
      inWriteAction {
        module.detach(library)
      }
    }

    syntheticClassesLoader.clean()
  }

  private def addScalaSdk(implicit version: ScalaVersion): Unit = {
    val loaders = Seq(ScalaCompilerLoader(), ScalaRuntimeLoader()) ++
      (if (isIncludeReflectLibrary) Seq(ScalaReflectLoader()) else Seq.empty)

    val files = loaders.map(_.path).map(new File(_))

    val classRoots = loaders.flatMap(_.rootFiles)
    val srcRoots = ScalaRuntimeLoader(Sources).rootFiles

    library = PsiTestUtil.addProjectLibrary(module, "scala-sdk", classRoots.asJava, srcRoots.asJava)

    inWriteAction {
      library.convertToScalaSdkWith(languageLevel(files.head), files)

      module.attach(library)
    }
  }

  private def languageLevel(compiler: File) =
    versionOf(compiler)
      .flatMap(_.toLanguageLevel)
      .getOrElse(ScalaLanguageLevel.Default)
}

object ScalaLibraryLoader {

  ScalaLoader.loadScala()

  private case class SyntheticClassesLoader()(implicit val module: Module)
    extends LibraryLoader {

    def init(implicit version: ScalaVersion): Unit =
      Some(module.getProject)
        .map(_.getComponent(classOf[SyntheticClasses]))
        .filterNot(_.isClassesRegistered)
        .foreach(_.registerClasses())
  }

  abstract class ScalaLibraryLoaderAdapter(implicit module: Module)
    extends IvyLibraryLoader {

    override val vendor: String = "org.scala-lang"

    override def path(implicit version: ScalaVersion): String = super.path

    def rootFiles(implicit version: ScalaVersion): Seq[VirtualFile] = {
      val fileSystem = JarFileSystem.getInstance
      Option(fileSystem.refreshAndFindFileByPath(s"$path!/")).toSeq
    }

    override def init(implicit version: ScalaVersion): Unit = {}

    override def folder(implicit version: ScalaVersion): String =
      name

    override def fileName(implicit version: ScalaVersion): String =
      s"$name-${version.minor}"
  }

  case class ScalaCompilerLoader()(implicit val module: Module)
    extends ScalaLibraryLoaderAdapter {

    override val name: String = "scala-compiler"
  }

  case class ScalaRuntimeLoader(override val ivyType: IvyType = Jars)
                               (implicit val module: Module)
    extends ScalaLibraryLoaderAdapter {

    override val name: String = "scala-library"

    override def fileName(implicit version: ScalaVersion): String = {
      val suffix = ivyType match {
        case Sources => "-sources"
        case _ => ""
      }
      super.fileName + suffix
    }
  }

  case class ScalaReflectLoader()(implicit val module: Module)
    extends ScalaLibraryLoaderAdapter {

    override val name: String = "scala-reflect"
  }

}

case class JdkLoader(jdk: Sdk = TestUtils.createJdk())
                    (implicit val module: Module) extends LibraryLoader {

  override def init(implicit version: ScalaVersion): Unit = {
    val model = module.modifiableModel
    model.setSdk(jdk)
    inWriteAction {
      model.commit()
    }
  }

  override def clean(): Unit = {
    val model = module.modifiableModel
    model.setSdk(null)
    inWriteAction {
      model.commit()
      ProjectJdkTable.getInstance().removeJdk(jdk)
    }
  }
}