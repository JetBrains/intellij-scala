package org.jetbrains.plugins.scala
package base

import _root_.com.intellij.testFramework.PsiTestCase
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.{ModifiableRootModel, ContentEntry, OrderRootType, ModuleRootManager}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{VfsUtil, LocalFileSystem, VirtualFile}
import java.io.{File, IOException}
import lang.superMember.SuperMethodTestUtil
import util.TestUtils
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.projectRoots.JavaSdk

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.02.2009
 */

abstract class ScalaPsiTestCase extends PsiTestCase {
  private val JDK_HOME = TestUtils.getMockJdk

  protected def rootPath = TestUtils.getTestDataPath + "/"

  /**
   * Main test body. All tests should be with same body: def testSmthing = doTest
   */
  protected def doTest: Unit

  override protected def setUp: Unit = {
    super.setUp
    myProject.getComponent(classOf[SyntheticClasses]).registerClasses

    val rootModel = ModuleRootManager.getInstance(getModule).getModifiableModel

    val testDataRoot = LocalFileSystem.getInstance.findFileByPath(rootPath)
    assert(testDataRoot != null)

    val contentEntry = rootModel.addContentEntry(testDataRoot)
    rootModel.setSdk(JavaSdk.getInstance.createJdk("java sdk", JDK_HOME, false))
    contentEntry.addSourceFolder(testDataRoot, false)

    // Add Scala Library
    val libraryTable = rootModel.getModuleLibraryTable
    val scalaLib = libraryTable.createLibrary("scala_lib")
    val libModel = scalaLib.getModifiableModel
    val libRoot = new File(TestUtils.getMockScalaLib)
    assert(libRoot.exists)

    val srcRoot = new File(TestUtils.getMockScalaSrc)
    assert(srcRoot.exists)

    libModel.addRoot(VfsUtil.getUrlForLibraryRoot(libRoot), OrderRootType.CLASSES)
    libModel.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES)

    ApplicationManager.getApplication.runWriteAction(new Runnable {
      def run {
        libModel.commit
        rootModel.commit
      }
    })
  }
}
