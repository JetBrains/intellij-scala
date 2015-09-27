package org.jetbrains.sbt
package lang.completion

import java.io.File

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{ModuleRootManager, OrderRootType}
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs._
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.testFramework.{LightVirtualFile, UsefulTestCase}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.lang.completion
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.mutable.ArrayBuffer

/**
 * @author Nikolay Obedin
 * @since 7/17/14.
 */

abstract class CompletionTestBase extends completion.CompletionTestBase {

  override def folderPath  = super.folderPath + "Sbt/"
  override def testFileExt = ".sbt"


  /**
   * @inheritdoc
   * Instead of using original file copy its contents into
   * mock file prepending implicit SBT imports
   */
  override def loadFile = {
    val fileName = getTestName(false) + testFileExt
    val filePath = folderPath + fileName
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText =
      Sbt.DefaultImplicitImports.map("import " + _).mkString("\n") + "\n" +
      StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    val mockFile = new LightVirtualFile(fileName, fileText)
    assert(mockFile != null, "Mock file can not be created")
    (fileName, mockFile)
  }

  override def loadAndSetFileText(filePath: String, file: VirtualFile) = {
    val fileText = new String(file.contentsToByteArray())
    configureFromFileTextAdapter (filePath, fileText)
    fileText
  }

  override def chechResult(got: Array[String], _expected: String) {
    import scala.collection.JavaConversions._
    val expected = _expected.split("\n")
    UsefulTestCase.assertContainsElements[String](got.toSet.toSeq, expected.toSeq)
  }

  override def setUp() {
    super.setUpWithoutScalaLib()
    loadSbt(getProjectAdapter, getModuleAdapter)
  }

  private def loadSbt(project: Project, module: Module) {
    val rootManager = ModuleRootManager.getInstance(module)
    val rootModel = rootManager.getModifiableModel
    val libs = rootManager.orderEntries().librariesOnly()
    val models = new ArrayBuffer[Library.ModifiableModel]

    def addLibrary(libName: String, libJarPath: String) {
      val libTable = rootModel.getModuleLibraryTable
      val libModel = libTable.createLibrary(libName).getModifiableModel
      val libRoot = new File(libJarPath)
      assert(libRoot.exists)
      libModel.addRoot(VfsUtil.getUrlForLibraryRoot(libRoot), OrderRootType.CLASSES)
      VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
      models += libModel
    }
    def isLibraryLoaded(libName: String): Boolean = {
      var isLoaded = false
      libs.forEachLibrary(new Processor[Library] {
        def process(lib: Library): Boolean = {
          isLoaded = lib.getName == libName
          !isLoaded
        }
      })
      isLoaded
    }

    val sbtLibrariesRoot = TestUtils.getTestDataPath + "/mockSbt0135/"
    val sbtLibraries = new File(sbtLibrariesRoot).listFiles().filter(f => f.isFile && f.getName.endsWith(".jar"))
    sbtLibraries foreach (lib => addLibrary(lib.getName, lib.getAbsolutePath))

    ApplicationManager.getApplication.runWriteAction(new Runnable {
      def run() {
        models foreach (_.commit())
        rootModel.commit()
        val startupManager = StartupManager.getInstance(project).asInstanceOf[StartupManagerImpl]
        startupManager.startCacheUpdate()
      }
    })
  }
}

