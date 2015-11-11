package org.jetbrains.plugins.scala
package base

import java.io.File

import _root_.com.intellij.testFramework.PsiTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.libraries.Library.ModifiableModel
import com.intellij.openapi.roots.{ModifiableRootModel, ModuleRootManager, OrderRootType}
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil}
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.02.2009
 */

abstract class ScalaPsiTestCase extends PsiTestCase {
  private val JDK_HOME = TestUtils.getDefaultJdk

  protected def rootPath = TestUtils.getTestDataPath + "/"

  /**
   * Main test body. All tests should be with same body: def testSmthing = doTest
   */
  protected def doTest()

  override protected def setUp() {
    super.setUp()
    val rootModel: ModifiableRootModel = ModuleRootManager.getInstance(getModule).getModifiableModel

    try {
      val testDataRoot = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootPath)
      assert(testDataRoot != null)

      val contentEntry = rootModel.addContentEntry(testDataRoot)
      rootModel.setSdk(JavaSdk.getInstance.createJdk("java sdk", JDK_HOME, false))
      contentEntry.addSourceFolder(testDataRoot, false)

      // Add Scala Library
      val libraryTable = rootModel.getModuleLibraryTable
      val scalaLib = libraryTable.createLibrary("scala_lib")
      val libModel: ModifiableModel = scalaLib.getModifiableModel
      try {
        val libRoot = new File(TestUtils.getScalaLibraryPath)
        assert(libRoot.exists)

        val srcRoot = new File(TestUtils.getScalaLibrarySrc)
        assert(srcRoot.exists)

        libModel.addRoot(VfsUtil.getUrlForLibraryRoot(libRoot), OrderRootType.CLASSES)
        libModel.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES)

        ApplicationManager.getApplication.runWriteAction(new Runnable {
          def run() {
            libModel.commit()
            rootModel.commit()
          }
        })
      }
      finally {
        if (!Disposer.isDisposed(libModel)) {
          Disposer.dispose(libModel)
        }
      }
    } finally {
      if (!rootModel.isDisposed) {
        rootModel.dispose()
      }
    }
  }
}
