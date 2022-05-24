package org.jetbrains.plugins.scala.refactoring.move

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaDirectoryService, JavaPsiFacade, PsiClass, PsiDirectory, PsiDocumentManager, PsiPackage}
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassesOrPackagesProcessor, SingleSourceRootMoveDestination}
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.junit.Assert.assertNotNull

import java.io.File
import java.nio.file.Path
import java.util
import scala.annotation.nowarn
import scala.collection.mutable.ArrayBuffer

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
abstract class ScalaMoveClassTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  protected def testDataRoot: String

  private def root: String = testDataRoot + getTestName(true)

  private def findAndRefreshVFile(path: String) = {
    val vFile = LocalFileSystem.getInstance.findFileByPath(path.replace(File.separatorChar, '/'))
    VfsUtil.markDirtyAndRefresh(/*async = */false, /*recursive =*/ true, /*reloadChildren =*/true, vFile)
    vFile
  }

  private var rootDirBefore: VirtualFile = _
  private var rootDirAfter: VirtualFile = _

  override protected def afterSetUpProject(module: Module): Unit = {
    super.afterSetUpProject(module)
    val rootBefore = root + "/before"
    val rootAfter  = root + "/after"
    findAndRefreshVFile(rootBefore)
    rootDirBefore = PsiTestUtil.createTestProjectStructure(getProjectAdapter, getModuleAdapter, rootBefore, new util.HashSet[Path](), true)
    rootDirAfter = findAndRefreshVFile(rootAfter)
  }

  def doTest(
    classNames: Array[String],
    newPackageName: String,
    mode: Kinds.Value = Kinds.all,
    moveCompanion: Boolean = true
  ): Unit = try {
    val settings = ScalaApplicationSettings.getInstance()
    val moveCompanionOld = settings.MOVE_COMPANION
    settings.MOVE_COMPANION = moveCompanion
    try {
      performAction(classNames, newPackageName, mode)
    } finally {
      PsiTestUtil.removeSourceRoot(getModuleAdapter, rootDirBefore)
    }
    settings.MOVE_COMPANION = moveCompanionOld
    PostprocessReformattingAspect.getInstance(getProjectAdapter).doPostponedFormatting()
    PlatformTestUtil.assertDirectoriesEqual(rootDirAfter, rootDirBefore)
  } catch {
    case ex: Throwable =>
      //print folders to conveniently navigate to them from failed test console
      System.err.println(s"Folder before path: $rootDirBefore")
      System.err.println(s"Folder after path: $rootDirAfter")
      throw ex
  }

  private def performAction(classNames: Array[String], newPackageName: String, mode: Kinds.Value): Unit = {
    val classes = new ArrayBuffer[PsiClass]()
    for (name <- classNames) {
      classes ++= ScalaPsiManager.instance(getProjectAdapter).getCachedClasses(GlobalSearchScope.allScope(getProjectAdapter), name).filter {
        case o: ScObject if o.isSyntheticObject => false
        case _: ScClass if mode == Kinds.onlyObjects => false
        case _: ScObject if mode == Kinds.onlyClasses => false
        case _ => true
      }
    }
    val aPackage: PsiPackage = JavaPsiFacade.getInstance(getProjectAdapter).findPackage(newPackageName)
    assertNotNull(s"Can't find package '$newPackageName'", aPackage)
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
