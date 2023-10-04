package org.jetbrains.plugins.scala.refactoring.move

import com.intellij.lang.ASTNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaDirectoryService, JavaPsiFacade, PsiClass, PsiDirectory, PsiDocumentManager, PsiPackage}
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassesOrPackagesProcessor, SingleSourceRootMoveDestination}
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.{TestUtils, WriteCommandActionEx}
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}

import java.io.File
import java.nio.file.Path
import java.util

abstract class ScalaMoveClassTestBase extends ScalaLightCodeInsightFixtureTestCase {

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken.DoNotShare

  protected def testDataRoot = TestUtils.getTestDataPath + "/refactoring/move/"

  private def root: String = testDataRoot + getTestName(true)

  private def findAndRefreshVFile(path: String) = {
    val vFile = LocalFileSystem.getInstance.findFileByPath(path.replace(File.separatorChar, '/'))
    VfsUtil.markDirtyAndRefresh(/*async = */false, /*recursive =*/ true, /*reloadChildren =*/true, vFile)
    vFile
  }

  private var rootDirBefore: VirtualFile = _
  private var rootDirAfter: VirtualFile = _

  override protected def afterSetUpProject(project: Project, module: Module): Unit = {
    super.afterSetUpProject(project, module)
    val rootBefore = root + "/before"
    val rootAfter  = root + "/after"
    findAndRefreshVFile(rootBefore)

    //remove existing content entries (default source folders),
    //otherwise default package (empty one, "") will be detected in this content entry during move refactoring
    inWriteAction {
      ModuleRootModificationUtil.modifyModel(module, model => {
        val contentEntries = model.getContentEntries
        contentEntries.foreach(model.removeContentEntry)
        true
      })
    }

    rootDirBefore = PsiTestUtil.createTestProjectStructure(project, module, rootBefore, new util.HashSet[Path](), true)
    rootDirAfter = findAndRefreshVFile(rootAfter)
  }

  protected def doTest(
    classNames: Seq[String],
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
      PsiTestUtil.removeSourceRoot(getModule, rootDirBefore)
    }
    settings.MOVE_COMPANION = moveCompanionOld

    PostprocessReformattingAspect.getInstance(getProject).doPostponedFormatting()
    PlatformTestUtil.assertDirectoriesEqual(rootDirAfter, rootDirBefore)
  } catch {
    case ex: Throwable =>
      //print folders to conveniently navigate to them from failed test console
      System.err.println(s"Folder before path: $rootDirBefore")
      System.err.println(s"Folder after path: $rootDirAfter")
      throw ex
  }

  private def performAction(classNames: Seq[String], targetPackageName: String, mode: Kinds.Value): Unit = {
    val classesToMove: Seq[PsiClass] =
      for {
        name <- classNames
        clazz <- {
          val projectScope = GlobalSearchScope.allScope(getProject)
          val cachedClasses = ScalaPsiManager.instance(getProject).getCachedClasses(projectScope, name)
          val cachedClassesFiltered = cachedClasses.filter {
            case o: ScObject if o.isSyntheticObject => false
            case _: ScClass if mode == Kinds.onlyObjects => false
            case _: ScObject if mode == Kinds.onlyClasses => false
            case _ => true
          }
          cachedClassesFiltered
        }
      } yield clazz

    //keeping hard refs to AST nodes to avoid flaky tests (as a workaround for SCL-20527 (see solution proposals))
    var myASTHardRefs: Seq[ASTNode] = classesToMove.map(_.getNode)

    val targetPackage: PsiPackage = JavaPsiFacade.getInstance(getProject).findPackage(targetPackageName)
    assertNotNull(s"Can't find package '$targetPackageName'", targetPackage)

    val dirs: Array[PsiDirectory] = targetPackage.getDirectories(GlobalSearchScope.moduleScope(getModule))
    assertTrue(
      s"""Expected only single directory in module, but got ${dirs.length}:
         |${dirs.mkString("\n")}""".stripMargin,
      dirs.length == 1
    )
    val targetDirectory: PsiDirectory = dirs.head

    WriteCommandActionEx.runWriteCommandAction(getProject, () => {
      ScalaFileImpl.performMoveRefactoring {
        val targetPackageWrapper = PackageWrapper.create(JavaDirectoryService.getInstance.getPackage(targetDirectory))
        val destination = new SingleSourceRootMoveDestination(targetPackageWrapper, targetDirectory)
        val processor = new MoveClassesOrPackagesProcessor(
          getProject,
          classesToMove.toArray,
          destination,
          true,
          true,
          null
        )
        processor.run()
      }
    })

    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    myASTHardRefs = null
  }

  object Kinds extends Enumeration {
    type Kinds = Value
    val onlyObjects, onlyClasses, all = Value
  }
}
