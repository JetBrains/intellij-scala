package org.jetbrains.plugins.scala.refactoring.move

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaDirectoryService, JavaPsiFacade, PsiClass, PsiDirectory, PsiDocumentManager, PsiPackage}
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassesOrPackagesProcessor, SingleSourceRootMoveDestination}
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.WriteCommandActionEx
import org.junit.Assert.{assertNotNull, assertTrue}

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
