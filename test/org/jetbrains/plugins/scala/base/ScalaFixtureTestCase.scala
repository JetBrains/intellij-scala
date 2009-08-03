package org.jetbrains.plugins.scala.base


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import com.intellij.openapi.vfs.{VfsUtil, LocalFileSystem}
import java.io.File
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import lang.psi.impl.toplevel.synthetic.SyntheticClasses
import util.TestUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.08.2009
 */

abstract class ScalaFixtureTestCase extends CodeInsightFixtureTestCase {
  private val JDK_HOME = TestUtils.getMockJdk

  protected def rootPath = TestUtils.getTestDataPath + "/"

  override protected def setUp: Unit = {
    super.setUp
    myFixture.getProject.getComponent(classOf[SyntheticClasses]).registerClasses

    val rootModel = ModuleRootManager.getInstance(myFixture.getModule).getModifiableModel

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