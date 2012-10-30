package org.jetbrains.plugins.scala
package refactoring.move

import org.jetbrains.plugins.scala.util.TestUtils
import com.intellij.testFramework.{CodeInsightTestCase, PlatformTestUtil, PsiTestUtil}
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import java.util
import java.io.File
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import collection.mutable.ArrayBuffer
import lang.psi.impl.ScalaPsiManager
import com.intellij.refactoring.move.moveClassesOrPackages.{SingleSourceRootMoveDestination, MoveClassesOrPackagesProcessor}
import com.intellij.refactoring.PackageWrapper
import com.intellij.openapi.fileEditor.FileDocumentManager

/**
 * @author Alefas
 * @since 30.10.12
 */
class ScalaMoveClassTest extends CodeInsightTestCase {
  def testSimple() {
    doTest("simple", Array("com.A"), "org")
  }

  def testSCL2625() {
    doTest("scl2625", Array("somepackage.Dummy", "somepackage.MoreBusiness", "somepackage.Business", "somepackage.AnotherEnum"), "dest")
  }

  def testSCL4623() {
    doTest("scl4623", Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def doTest(testName: String, classNames: Array[String], newPackageName: String) {
    val root: String = TestUtils.getTestDataPath + "/move/" + testName
    val rootBefore: String = root + "/before"
    PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17)
    val rootDir: VirtualFile = PsiTestUtil.createTestProjectStructure(getProject, myModule, rootBefore, new util.HashSet[File]())
    performAction(classNames, newPackageName)
    val rootAfter: String = root + "/after"
    val rootDir2: VirtualFile = LocalFileSystem.getInstance.findFileByPath(rootAfter.replace(File.separatorChar, '/'))
    getProject.getComponent(classOf[PostprocessReformattingAspect]).doPostponedFormatting()
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir, PlatformTestUtil.CVS_FILE_FILTER)
  }

  private def performAction(classNames: Array[String], newPackageName: String) {
    val classes = new ArrayBuffer[PsiClass]()
    for (name <- classNames) {
      classes ++= ScalaPsiManager.instance(getProject).getCachedClasses(GlobalSearchScope.allScope(getProject), name)
    }
    val aPackage: PsiPackage = JavaPsiFacade.getInstance(getProject).findPackage(newPackageName)
    val dirs: Array[PsiDirectory] = aPackage.getDirectories
    assert(dirs.length == 1)
    new MoveClassesOrPackagesProcessor(getProject, classes.toArray,
      new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance.getPackage(dirs(0))), dirs(0)), true, true, null).run()
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    FileDocumentManager.getInstance.saveAllDocuments()
  }
}
