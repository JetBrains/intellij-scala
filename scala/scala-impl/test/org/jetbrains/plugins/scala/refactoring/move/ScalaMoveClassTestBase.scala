package org.jetbrains.plugins.scala.refactoring.move

import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaDirectoryService, JavaPsiFacade, PsiClass, PsiDirectory, PsiDocumentManager, PsiPackage}
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassHandler, SingleSourceRootMoveDestination}
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.refactoring.move.ScalaMoveClassesOrPackagesProcessor
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.WriteCommandActionEx
import org.junit.Assert.{assertNotNull, assertTrue}

import java.util

abstract class ScalaMoveClassTestBase extends ScalaMoveTestBase {

  import ScalaMoveClassTestBase.RunActionInMovePreparation

  protected def doTest(
    classNames: Seq[String],
    newPackageName: String,
    mode: Kinds.Value = Kinds.all,
    moveCompanion: Boolean = true
  ): Unit = try {
    doMove(classNames, newPackageName, mode, moveCompanion)

    PostprocessReformattingAspect.getInstance(getProject).doPostponedFormatting()
    PlatformTestUtil.assertDirectoriesEqual(getRootAfter, getRootBefore)
  } catch {
    case ex: Throwable =>
      //print folders to conveniently navigate to them from failed test console
      System.err.println(s"Folder before path: $getRootBefore")
      System.err.println(s"Folder after path: $getRootAfter")
      throw ex
  }

  protected final def doMove(
    classNames: Seq[String],
    newPackageName: String,
    mode: Kinds.Value = Kinds.all,
    moveCompanion: Boolean = true
  ): Unit = withMoveCompanionSetting(moveCompanion) {
    try {
      performAction(classNames, newPackageName, mode)
    } finally {
      PsiTestUtil.removeSourceRoot(getModule, getRootBefore)
    }
  }

  protected final def withMoveCompanionSetting(moveCompanion: Boolean)(action: => Unit): Unit = {
    val settings = ScalaApplicationSettings.getInstance()
    val moveCompanionOld = settings.MOVE_COMPANION
    settings.MOVE_COMPANION = moveCompanion
    try {
      action
    } finally {
      settings.MOVE_COMPANION = moveCompanionOld
    }
  }

  protected final def registerBeforeMoveAction(action: PsiClass => Unit): Unit = {
    // Calling action before doMove would precede association collection; after doMove,
    // the associations have already been restored. A temporary handler reaches the interval
    // where GC can discard saved AST data while the real move processor is running.
    val handler = new RunActionInMovePreparation(action)
    // Scala's prepareMove must save associations first. Run last, before any class moves,
    // and remove this test-only handler when the test disposable is disposed.
    MoveClassHandler.EP_NAME.getPoint.registerExtension(handler, LoadingOrder.LAST, getTestRootDisposable)
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
        val processor = new ScalaMoveClassesOrPackagesProcessor(
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
  }

  object Kinds extends Enumeration {
    type Kinds = Value
    val onlyObjects, onlyClasses, all = Value
  }
}

object ScalaMoveClassTestBase {

  private final class RunActionInMovePreparation(action: PsiClass => Unit) extends NoOpMoveClassHandler {
    override def prepareMove(clazz: PsiClass): Unit = action(clazz)
  }

  /** Test adapter for observing individual move phases without performing a move. */
  private abstract class NoOpMoveClassHandler extends MoveClassHandler {
    override def prepareMove(clazz: PsiClass): Unit = ()
    override def finishMoveClass(clazz: PsiClass): Unit = ()
    override def doMoveClass(clazz: PsiClass, destination: PsiDirectory): PsiClass = null
    override def getName(clazz: PsiClass): String = null
    override def preprocessUsages(usages: util.Collection[UsageInfo]): Unit = ()
  }
}
