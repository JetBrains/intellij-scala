package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.ScalaLoader
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.project.template.Artifact
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

import scala.collection.mutable.ArrayBuffer

class ScalaLibraryLoader(project: Project, implicit val module: Module, isIncludeReflectLibrary: Boolean = false,
                         javaSdk: Option[Sdk] = None) extends LibraryLoader {

  private val addedLibraries = ArrayBuffer[Library]()

  def init(implicit version: ScalaSdkVersion) {
    ScalaLoader.loadScala()

    addSyntheticClasses()

    addScalaSdk
    LibraryLoader.storePointers()

    javaSdk.foreach { sdk =>
      val rootModel = ModuleRootManager.getInstance(module).getModifiableModel
      rootModel.setSdk(sdk)
      inWriteAction(rootModel.commit())
    }
  }

  private def addSyntheticClasses(): Unit =
    project.getComponent(classOf[SyntheticClasses]) match {
      case classes if !classes.isClassesRegistered =>
        classes.registerClasses()
      case _ =>
    }

  override def clean(): Unit = {
    disposeLibraries()
  }

  protected def disposeLibraries(): Unit = {
    inWriteAction {
      addedLibraries.foreach(module.detach)
    }
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
  def getSdkNone: Option[Sdk] = None

  def withMockJdk(project: Project, module: Module, isIncludeReflectLibrary: Boolean = false): ScalaLibraryLoader = {
    val mockJdk = TestUtils.getDefaultJdk
    VfsRootAccess.allowRootAccess(mockJdk)
    val javaSdk = Some(JavaSdk.getInstance.createJdk("java sdk", mockJdk, false))
    new ScalaLibraryLoader(project, module, isIncludeReflectLibrary, javaSdk)
  }
}