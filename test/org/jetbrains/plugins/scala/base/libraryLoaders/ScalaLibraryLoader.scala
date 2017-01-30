package org.jetbrains.plugins.scala.base.libraryLoaders

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{ModuleRootManager, OrderRootType}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.{JarFileSystem, LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.ScalaLoader
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.project.template.Artifact
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

import scala.collection.mutable.ArrayBuffer

/**
  * Nikolay.Tropin
  * 5/29/13
  */
class ScalaLibraryLoader(project: Project, module: Module, rootPath: String, isIncludeReflectLibrary: Boolean = false,
                         javaSdk: Option[Sdk] = None) {

  private val addedLibraries = ArrayBuffer[Library]()

  def loadScala(libVersion: TestUtils.ScalaSdkVersion) {
    initScalaComponents()

    addSyntheticClasses()

    if (rootPath != null) {
      FileUtil.createIfDoesntExist(new File(rootPath))
      val testDataRoot: VirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
      assert(testDataRoot != null)
      PsiTestUtil.addSourceRoot(module, testDataRoot)
    }

    addScalaSdk(module, libVersion, isIncludeReflectLibrary)

    javaSdk.foreach { sdk =>
      val rootModel = ModuleRootManager.getInstance(module).getModifiableModel
      rootModel.setSdk(sdk)
      inWriteAction(rootModel.commit())
    }
  }

  def initScalaComponents(): Unit = {
    ScalaLoader.loadScala()
  }

  def addSyntheticClasses(): Unit = {
    val syntheticClasses: SyntheticClasses = project.getComponent(classOf[SyntheticClasses])
    if (!syntheticClasses.isClassesRegistered) {
      syntheticClasses.registerClasses()
    }
  }

  def clean() {
    if (rootPath != null) {
      val testDataRoot: VirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
      PsiTestUtil.removeSourceRoot(module, testDataRoot)
    }
    disposeLibraries()
  }

  def disposeLibraries(): Unit = {
    inWriteAction {
      addedLibraries.foreach(module.detach)
    }
  }

  def addScalaSdk(module: Module, sdkVersion: ScalaSdkVersion, loadReflect: Boolean) = {
    val compilerPath = TestUtils.getScalaCompilerPath(sdkVersion)
    val libraryPath = TestUtils.getScalaLibraryPath(sdkVersion)
    val reflectPath = TestUtils.getScalaReflectPath(sdkVersion)

    import scala.collection.JavaConversions._
    val scalaSdkJars = Seq(libraryPath, compilerPath) ++ (if (loadReflect) Seq(reflectPath) else Seq.empty)

    val fileSystem = JarFileSystem.getInstance
    val classRoots = scalaSdkJars.map(_ + "!/")
      .flatMap(path => fileSystem.refreshAndFindFileByPath(path).toOption)

    val scalaLibrarySrc = TestUtils.getScalaLibrarySrc(sdkVersion)
    val srcsRoots = Option(fileSystem.refreshAndFindFileByPath(scalaLibrarySrc + "!/")).toSeq
    val scalaSdkLib = PsiTestUtil.addProjectLibrary(module, "scala-sdk", classRoots, srcsRoots)
    val languageLevel = Artifact.ScalaCompiler.versionOf(new File(compilerPath))
      .flatMap(ScalaLanguageLevel.from).getOrElse(ScalaLanguageLevel.Default)

    inWriteAction {
      scalaSdkLib.convertToScalaSdkWith(languageLevel, scalaSdkJars.map(new File(_)))
      module.attach(scalaSdkLib)
      addedLibraries += scalaSdkLib
    }

    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
  }

  private def addLibrary(module: Module, libraryName: String, mockLib: String): Unit = {
    if (module.libraries.exists(_.getName == libraryName)) return

    VfsRootAccess.allowRootAccess(mockLib)

    val rootModel = ModuleRootManager.getInstance(module).getModifiableModel
    val libraryTable = rootModel.getModuleLibraryTable
    val library = libraryTable.createLibrary(libraryName)
    val libModel = library.getModifiableModel

    val libRoot: File = new File(mockLib)
    assert(libRoot.exists, s"Mock library not found at ${libRoot.getAbsolutePath}")
    libModel.addRoot(VfsUtil.getUrlForLibraryRoot(libRoot), OrderRootType.CLASSES)

    inWriteAction {
      libModel.commit()
      rootModel.commit()
    }

    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
  }
}

object ScalaLibraryLoader {
  def getSdkNone: Option[Sdk] = None

  def withMockJdk(project: Project, module: Module, rootPath: String, isIncludeReflectLibrary: Boolean = false): ScalaLibraryLoader = {

    val mockJdk = TestUtils.getDefaultJdk
    VfsRootAccess.allowRootAccess(mockJdk)
    val javaSdk = Some(JavaSdk.getInstance.createJdk("java sdk", mockJdk, false))
    new ScalaLibraryLoader(project, module, rootPath, isIncludeReflectLibrary, javaSdk)
  }
}