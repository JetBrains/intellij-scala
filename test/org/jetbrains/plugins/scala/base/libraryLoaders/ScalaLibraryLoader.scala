package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.ScalaLoader
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.project.template.Artifact
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

import scala.collection.mutable.ArrayBuffer

case class ScalaLibraryLoader(isIncludeReflectLibrary: Boolean = false)
                             (implicit val module: Module, project: Project)
  extends LibraryLoader {

  private val addedLibraries = ArrayBuffer[Library]()
  private val syntheticClassesLoader = ScalaLibraryLoader.SyntheticClassesLoader()

  def init(implicit version: ScalaSdkVersion): Unit = {
    syntheticClassesLoader.init

    addScalaSdk
    LibraryLoader.storePointers()
  }

  override def clean(): Unit = {
    inWriteAction {
      addedLibraries.foreach(module.detach)
    }
    syntheticClassesLoader.clean()
  }

  private def addScalaSdk(implicit version: ScalaSdkVersion) = {
    val compilerPath = TestUtils.getScalaCompilerPath(version)
    val libraryPath = TestUtils.getScalaLibraryPath(version)
    val reflectPath = TestUtils.getScalaReflectPath(version)

    import scala.collection.JavaConversions._
    val scalaSdkJars = Seq(libraryPath, compilerPath) ++ (if (isIncludeReflectLibrary) Seq(reflectPath) else Seq.empty)

    val fileSystem = JarFileSystem.getInstance
    val classRoots = scalaSdkJars.map(_ + "!/")
      .flatMap(path => fileSystem.refreshAndFindFileByPath(path).toOption)

    val scalaLibrarySrc = TestUtils.getScalaLibrarySrc(version)
    val srcsRoots = Option(fileSystem.refreshAndFindFileByPath(scalaLibrarySrc + "!/")).toSeq
    val scalaSdkLib = PsiTestUtil.addProjectLibrary(module, "scala-sdk", classRoots, srcsRoots)
    val languageLevel = Artifact.ScalaCompiler.versionOf(new File(compilerPath))
      .flatMap(ScalaLanguageLevel.from).getOrElse(ScalaLanguageLevel.Default)

    inWriteAction {
      scalaSdkLib.convertToScalaSdkWith(languageLevel, scalaSdkJars.map(new File(_)))
      module.attach(scalaSdkLib)
      addedLibraries += scalaSdkLib
    }
  }
}

object ScalaLibraryLoader {

  ScalaLoader.loadScala()

  case class SyntheticClassesLoader(implicit val module: Module, project: Project) extends LibraryLoader {

    override def init(implicit version: ScalaSdkVersion): Unit =
      project.getComponent(classOf[SyntheticClasses]) match {
        case classes if !classes.isClassesRegistered => classes.registerClasses()
        case _ =>
      }
  }

}

case class JdkLoader(jdk: Sdk = TestUtils.createJdk())
                    (implicit val module: Module) extends LibraryLoader {

  override def init(implicit version: ScalaSdkVersion): Unit = {
    val model = ModuleRootManager.getInstance(module).getModifiableModel
    model.setSdk(jdk)
    inWriteAction {
      model.commit()
    }
  }
}