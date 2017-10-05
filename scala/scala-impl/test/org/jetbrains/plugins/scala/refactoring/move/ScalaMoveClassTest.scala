package org.jetbrains.plugins.scala
package refactoring.move

import java.io.File
import java.util

import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.psi._
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassesOrPackagesProcessor, SingleSourceRootMoveDestination}
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 30.10.12
 */
class ScalaMoveClassTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  def testPackageObject() {
    doTest(Array("com.`package`"), "org")
  }

  def testPackageObject2() {
    doTest(Array("com"), "org")
  }

  def testSimple() {
    doTest(Array("com.A"), "org")
  }

  def testScl2625() {
    doTest(Array("somepackage.Dummy", "somepackage.MoreBusiness", "somepackage.Business", "somepackage.AnotherEnum"), "dest")
  }

  def testScl4623() {
    doTest(Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testScl4613() {
    doTest(Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testScl4621() {
    doTest(Array("moveRefactoring.foo.O"), "moveRefactoring.bar")
  }

  def testScl4619() {
    doTest(Array("foo.B"), "bar")
  }

  def testScl4875() {
    doTest(Array("com.A"), "org")
  }

  def testScl4878() {
    doTest(Array("org.B"), "com")
  }

  def testScl4894() {
    doTest(Array("moveRefactoring.foo.B", "moveRefactoring.foo.BB"), "moveRefactoring.bar")
  }

  def testScl4972() {
    doTest(Array("moveRefactoring.foo.B"), "moveRefactoring.bar")
  }

  def testScl5456 () {
    doTest(Array("com.A"), "org", Kinds.onlyClasses)
  }

  def testWithCompanion() {
    doTest(Array("source.A"), "target", Kinds.onlyClasses)
  }


  def testBothJavaAndScala() {
    doTest(Array("org.A", "org.J"), "com")
  }


//  wait for fix Scl-6316
//  def testWithoutCompanion() {
//    doTest("withoutCompanion", Array("source.A"), "target", Kinds.onlyObjects, moveCompanion = false)
//  }

  private def findAndRefreshVFile(path: String) = {
    val vFile = LocalFileSystem.getInstance.findFileByPath(path.replace(File.separatorChar, '/'))
    VfsUtil.markDirtyAndRefresh(/*async = */false, /*recursive =*/ true, /*reloadChildren =*/true, vFile)
    vFile
  }

  private def root = TestUtils.getTestDataPath + "/move/" + getTestName(true)

  private var rootDirBefore: VirtualFile = _
  private var rootDirAfter: VirtualFile = _

  override protected def afterSetUpProject() = {
    super.afterSetUpProject()
    val rootBefore = root + "/before"
    val rootAfter  = root + "/after"
    findAndRefreshVFile(rootBefore)
    rootDirBefore = PsiTestUtil.createTestProjectStructure(getProjectAdapter, getModuleAdapter, rootBefore, new util.HashSet[File]())
    rootDirAfter = findAndRefreshVFile(rootAfter)
  }

  def doTest(classNames: Array[String], newPackageName: String, mode: Kinds.Value = Kinds.all, moveCompanion: Boolean = true) {
    val settings = ScalaApplicationSettings.getInstance()
    val moveCompanionOld = settings.MOVE_COMPANION
    settings.MOVE_COMPANION = moveCompanion
    try {
      performAction(classNames, newPackageName, mode)
    } finally {
      PsiTestUtil.removeSourceRoot(getModuleAdapter, rootDirBefore)
    }
    settings.MOVE_COMPANION = moveCompanionOld
    getProjectAdapter.getComponent(classOf[PostprocessReformattingAspect]).doPostponedFormatting()
    PlatformTestUtil.assertDirectoriesEqual(rootDirAfter, rootDirBefore)
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
      new MoveClassesOrPackagesProcessor(getProjectAdapter, classes.toArray,
        new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance.getPackage(dirs(0))), dirs(0)), true, true, null).run()
    }
    PsiDocumentManager.getInstance(getProjectAdapter).commitAllDocuments()
  }

  object Kinds extends Enumeration {
    type Kinds = Value
    val onlyObjects, onlyClasses, all = Value
  }
}
