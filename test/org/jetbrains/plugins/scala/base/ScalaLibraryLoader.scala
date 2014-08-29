package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.roots._
import com.intellij.openapi.roots.libraries.{LibraryTable, Library}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.TestUtils
import java.util
import java.io.File
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile, VfsUtil}
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion
import configuration._

/**
 * Nikolay.Tropin
 * 5/29/13
 */
class ScalaLibraryLoader(project: Project, module: Module, rootPath: String,
                         isIncludeScalazLibrary: Boolean = false, isIncludeReflectLibrary: Boolean = false,
                         javaSdk: Option[Sdk] = None) {

  var contentEntry: ContentEntry = null

  def loadLibrary(libVersion: TestUtils.ScalaSdkVersion) {
    val syntheticClasses: SyntheticClasses = project.getComponent(classOf[SyntheticClasses])
    if (!syntheticClasses.isClassesRegistered) {
      syntheticClasses.registerClasses()
    }

    var rootModel: ModifiableRootModel = null
    val rootManager: ModuleRootManager = ModuleRootManager.getInstance(module)
    if (rootPath != null) {
      rootModel = rootManager.getModifiableModel
      val testDataRoot: VirtualFile = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
      assert(testDataRoot != null)
      refreshDirectory(testDataRoot)
      contentEntry = rootModel.addContentEntry(testDataRoot)
      contentEntry.addSourceFolder(testDataRoot, false)
    }
    
    if (libVersion == ScalaSdkVersion._2_11) inWriteAction {
      val scalaSdk = project.createScalaSdk("scala_sdk",
        Seq.empty, Seq.empty, Seq.empty, Seq.empty, ScalaLanguageLevel.SCALA_2_11)

      module.attach(scalaSdk)
    }
    
    val libs: OrderEnumerator = rootManager.orderEntries.librariesOnly
    val libModels: util.ArrayList[Library.ModifiableModel] = new util.ArrayList[Library.ModifiableModel]
    rootModel = addLibrary(libVersion, rootModel, rootManager, libs, libModels, "scala_lib",
      TestUtils.getMockScalaLib(libVersion), TestUtils.getMockScalaSrc(libVersion))

    if (isIncludeReflectLibrary) {
      rootModel = addLibrary(libVersion, rootModel, rootManager, libs, libModels, "scala_reflect",
        TestUtils.getMockScalaReflectLib(libVersion), null)
    }

    if (isIncludeScalazLibrary) {
      rootModel = addLibrary(libVersion, rootModel, rootManager, libs, libModels, "scalaz",
        TestUtils.getMockScalazLib(libVersion), null)
    }

    javaSdk.foreach {
      case sdk: Sdk =>
        if (rootModel == null) rootModel = rootManager.getModifiableModel
        rootModel.setSdk(sdk)
    }

    if (!libModels.isEmpty || rootModel != null) {
      val finalRootModel: ModifiableRootModel = rootModel
      ApplicationManager.getApplication.runWriteAction(new Runnable {
        def run() {
          import scala.collection.JavaConversions._
          for (libModel <- libModels) {
            libModel.commit()
          }
          if (finalRootModel != null) finalRootModel.commit()
          val startupManager: StartupManagerImpl = StartupManager.getInstance(project).asInstanceOf[StartupManagerImpl]
          startupManager.startCacheUpdate()
        }
      })
    }
  }

  def clean() {
    if (contentEntry != null) {
      val rootManager: ModuleRootManager = ModuleRootManager.getInstance(module)
      val rootModel: ModifiableRootModel = rootManager.getModifiableModel
      rootModel.removeContentEntry(contentEntry)
      contentEntry = null
      ApplicationManager.getApplication.runWriteAction(new Runnable {
        def run() {
          rootModel.commit()
        }
      })
    }
    inWriteAction {
      project.scalaSdks.foreach { scalaSdk =>
        module.detach(scalaSdk)
        project.remove(scalaSdk)
      }
    }
  }

  private def addLibrary(libVersion: TestUtils.ScalaSdkVersion,
                         rootModel: ModifiableRootModel,
                         rootManager: ModuleRootManager, libs: OrderEnumerator,
                         libModels: util.ArrayList[Library.ModifiableModel],
                         scalaLibraryName: String, mockLib: String, mockLibSrc: String): ModifiableRootModel = {
    class CustomProcessor extends Processor[Library] {
      def process(library: Library): Boolean = {
        val res: Boolean = library.getName == scalaLibraryName
        if (res) result = false
        result
      }
      var result: Boolean = true
    }
    val processor: CustomProcessor = new CustomProcessor
    libs.forEachLibrary(processor)
    var usedRootModel = rootModel
    if (processor.result) {
      if (usedRootModel == null) {
        usedRootModel = rootManager.getModifiableModel
      }
      val libraryTable: LibraryTable = usedRootModel.getModuleLibraryTable
      val scalaLib: Library = libraryTable.createLibrary(scalaLibraryName)
      val libModel: Library.ModifiableModel = scalaLib.getModifiableModel
      libModels.add(libModel)
      addLibraryRoots(libVersion, libModel, mockLib, mockLibSrc)
    }
    usedRootModel
  }

  private def addLibraryRoots(version: TestUtils.ScalaSdkVersion, libModel: Library.ModifiableModel, mockLib: String, mockLibSrc: String) {
    val libRoot: File = new File(mockLib)
    assert(libRoot.exists)
    libModel.addRoot(VfsUtil.getUrlForLibraryRoot(libRoot), OrderRootType.CLASSES)
    if (mockLibSrc != null) {
      val srcRoot: File = new File(mockLibSrc)
      assert(srcRoot.exists)
      libModel.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES)
    }
    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
  }

  private def refreshDirectory(dir: VirtualFile) {
    if (!dir.isDirectory) return
    dir.getChildren
    LocalFileSystem.getInstance.refresh(false)
    for (child <- dir.getChildren) {
      refreshDirectory(child)
    }
  }


}

object ScalaLibraryLoader {
  def getSdkNone: Option[Sdk] = None
}
