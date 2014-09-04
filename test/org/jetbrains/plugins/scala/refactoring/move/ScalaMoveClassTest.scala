package org.jetbrains.plugins.scala
package refactoring.move

import java.io.File
import java.util

import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi._
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.refactoring.move.ScalaMoveClassesOrPackagesProcessor
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 30.10.12
 */
class ScalaMoveClassTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
def testPackageObject() {
    doTest("packageObject", Array("com.`package`"), "org")
  }

  def testPackageObject2() {
    doTest("packageObject2", Array("com"), "org")
  }

  def testSimple() {
    doTest("simple", Array("com.A"), "org")
  }

  def testSCL2625() {
    doTest("scl2625", Array("somepackage.Dummy", "somepackage.MoreBusiness", "somepackage.Business", "somepackage.AnotherEnum"), "dest")
  }

  def testSCL4623() {
    doTest("scl4623", Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testSCL4613() {
    doTest("scl4613", Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testSCL4621() {
    doTest("scl4621", Array("moveRefactoring.foo.O"), "moveRefactoring.bar")
  }

  def testSCL4619() {
    doTest("scl4619", Array("foo.B"), "bar")
  }

  def testSCL4875() {
    doTest("scl4875", Array("com.A"), "org")
  }

  def testSCL4878() {
    doTest("scl4878", Array("org.B"), "com")
  }

  def testSCL4894() {
    doTest("scl4894", Array("moveRefactoring.foo.B", "moveRefactoring.foo.BB"), "moveRefactoring.bar")
  }

  def testSCL4972() {
    doTest("scl4972", Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testWithCompanion() {
    doTest("withCompanion", Array("source.A"), "target", Kinds.onlyClasses)
  }

  def testBothJavaAndScala() {
    doTest("bothJavaAndScala", Array("org.A", "org.J"), "com")
  }


//  wait for fix SCL-6316
//  def testWithoutCompanion() {
//    doTest("withoutCompanion", Array("source.A"), "target", Kinds.onlyObjects, moveCompanion = false)
//  }



  def doTest(testName: String, classNames: Array[String], newPackageName: String, mode: Kinds.Value = Kinds.all, moveCompanion: Boolean = true) {
    val root: String = TestUtils.getTestDataPath + "/move/" + testName
    val rootBefore: String = root + "/before"
    val rootDir: VirtualFile = PsiTestUtil.createTestProjectStructure(getProjectAdapter, getModuleAdapter, rootBefore, new util.HashSet[File]())
    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
    val settings = ScalaApplicationSettings.getInstance()
    val moveCompanionOld = settings.MOVE_COMPANION
    settings.MOVE_COMPANION = moveCompanion
    try {
      performAction(classNames, newPackageName, mode)
    } finally {
      PsiTestUtil.removeSourceRoot(getModuleAdapter, rootDir)
    }
    settings.MOVE_COMPANION = moveCompanionOld
    val rootAfter: String = root + "/after"
    val rootDir2: VirtualFile = LocalFileSystem.getInstance.findFileByPath(rootAfter.replace(File.separatorChar, '/'))
    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
    getProjectAdapter.getComponent(classOf[PostprocessReformattingAspect]).doPostponedFormatting()
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir)
  }

  private def performAction(classNames: Array[String], newPackageName: String, mode: Kinds.Value) {
    val classes = new ArrayBuffer[PsiClass]()
    for (name <- classNames) {
      classes ++= ScalaPsiManager.instance(getProjectAdapter).getCachedClasses(GlobalSearchScope.allScope(getProjectAdapter), name).filter {
        case o: ScObject if o.isSyntheticObject => false
        case c: ScClass if mode == Kinds.onlyObjects => false
        case o: ScObject if mode == Kinds.onlyClasses => false
        case _ => true
      }
    }
    val aPackage: PsiPackage = JavaPsiFacade.getInstance(getProjectAdapter).findPackage(newPackageName)
    val dirs: Array[PsiDirectory] = aPackage.getDirectories(GlobalSearchScope.moduleScope(getModuleAdapter))
    assert(dirs.length == 1)
    ScalaFileImpl.performMoveRefactoring {
      new ScalaMoveClassesOrPackagesProcessor(getProjectAdapter, classes.toArray,
        new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance.getPackage(dirs(0))), dirs(0)), true, true, null).run()
    }
    PsiDocumentManager.getInstance(getProjectAdapter).commitAllDocuments()
  }

  object Kinds extends Enumeration {
    type Kinds = Value
    val onlyObjects, onlyClasses, all = Value
  }
}
