package org.jetbrains.plugins.scala.refactoring.move

import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaDirectoryService, JavaPsiFacade, PsiClass, PsiDirectory, PsiDocumentManager, PsiPackage}
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.refactoring.move.ScalaMoveClassesOrPackagesProcessor
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.WriteCommandActionEx
import com.intellij.refactoring.BaseRefactoringProcessor
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}

import scala.jdk.CollectionConverters._

abstract class ScalaMoveClassTestBase extends ScalaMoveTestBase {

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
      PsiTestUtil.removeSourceRoot(getModule, getRootBefore)
    }
    settings.MOVE_COMPANION = moveCompanionOld

    PostprocessReformattingAspect.getInstance(getProject).doPostponedFormatting()
    PlatformTestUtil.assertDirectoriesEqual(getRootAfter, getRootBefore)
  } catch {
    case ex: Throwable =>
      //print folders to conveniently navigate to them from failed test console
      System.err.println(s"Folder before path: $getRootBefore")
      System.err.println(s"Folder after path: $getRootAfter")
      throw ex
  }

  /** The move is refused: it reports exactly `expectedConflicts`, and changes nothing. */
  protected def doTestConflicts(classNames: Seq[String], newPackageName: String, expectedConflicts: String*): Unit = {
    val conflicts = try {
      performAction(classNames, newPackageName, Kinds.onlyClasses)
      Seq.empty
    } catch {
      case e: BaseRefactoringProcessor.ConflictsInTestsException => e.getMessages.asScala.toSeq
    } finally {
      PsiTestUtil.removeSourceRoot(getModule, getRootBefore)
    }
    assertEquals(expectedConflicts.sorted.mkString("\n"), conflicts.sorted.mkString("\n"))
    PlatformTestUtil.assertDirectoriesEqual(getRootAfter, getRootBefore)
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
