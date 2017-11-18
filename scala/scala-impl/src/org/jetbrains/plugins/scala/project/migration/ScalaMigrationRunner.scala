package org.jetbrains.plugins.scala.project.migration

import com.intellij.codeInspection.{InspectionManager, ProblemsHolder}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.{PsiDirectory, PsiFile, PsiManager}
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.migration.api._
import org.jetbrains.plugins.scala.project.migration.defaultimpl.MyRecursiveVisitorWrapper

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
  * User: Dmitry.Naydanov
  * Date: 21.09.16.
  */
class ScalaMigrationRunner(project: Project) {
  def runMigrators(migrators: Seq[ScalaLibraryMigrator], projectStructure: MigrationApiService) {
    def catchRun(migrate: MigrationApiService => Option[MigrationReport]) {
      Try(migrate(projectStructure)) match {
        case Success(report) => report.foreach(projectStructure.showReport)
        case Failure(exception) => BundledCodeStoreComponent.showExceptionWarning(projectStructure, exception)
      }
    }

    runWithProgress {
      indicator =>
        def checkForUserCancel(): Unit = if (indicator.isCanceled) throw new MigrationCanceledException
        
        indicator setIndeterminate true

        migrators.foreach {
          migrator =>
            checkForUserCancel() 
            catchRun(migrator.migrateGlobal)
        }

       
        val inspectionManager = InspectionManager.getInstance(project)
        val myIterator = inReadAction(new FilteringFileIterator /*new FileTreeIterator(project)*/)

        while (myIterator.hasNext) inReadAction(myIterator.next()) match {
          case scalaFile: ScalaFile if !scalaFile.isCompiled && !scalaFile.isWorksheetFile =>
            checkForUserCancel()
            
            val holder = new ProblemsHolder(inspectionManager, scalaFile, false)

            val myCompoundAction = migrators.flatMap { migrator =>
                checkForUserCancel()
              migrator.migrateLocal(scalaFile, holder)
            }

            val visitor = new MyRecursiveVisitorWrapper(myCompoundAction)
            inReadAction(scalaFile.acceptChildren(visitor))

            val fixes = holder.getResultsArray
              .flatMap(_.getFixes).collect {
              case fix: AbstractFixOnPsiElement[_] => fix
            }

            fixes.foreach { fix =>
              checkForUserCancel()

              invokeLater {
                startCommand(project, () => {
                  catchRun { _ =>
                    inWriteAction(fix.invoke(project, scalaFile, null, null))
                    None
                  }
                }, "ScalaMigrator")
              }
            }
          case _ =>
        }

    }
  }

  private def runWithProgress(action: ProgressIndicator => Unit) {
    new Task.Modal(project, "Migrating...", true) {
      override def run(indicator: ProgressIndicator): Unit = try {
        action.apply(indicator)
      } catch {
        case _: MigrationCanceledException => 
      }
    }.queue()
  }
  
  
  private class MigrationCanceledException extends Exception("Migration was canceled by user")
  
  //we need our own iterator as there is no way to filter excluded dirs
  private class FilteringFileIterator() {
    val myCurrentFiles: mutable.Queue[PsiFile] = mutable.Queue[PsiFile]()
    val (myCurrentDirs, myExcludedDirs) = {
      val startDirs = mutable.Queue[PsiDirectory]()
      val excludedDirs = mutable.HashSet[PsiDirectory]()
      
      val modules = ModuleManager.getInstance(project).getModules
      val psiManager = PsiManager.getInstance(project)

      modules foreach {
        module =>
          val moduleRootManager = ModuleRootManager getInstance module
          
          moduleRootManager getSourceRoots true foreach {
            root =>
              val dir = psiManager findDirectory root
              if (dir != null) startDirs enqueue dir
          }
          
          moduleRootManager.getExcludeRoots foreach {
            root => 
              val excluded = psiManager findDirectory root
              if (excluded != null) excludedDirs add excluded
          }
      }
      
      (startDirs, excludedDirs)
    }
    
    expandDirectoriesUntilFilesNotEmpty()

    private def expandDirectory(dir: PsiDirectory) {
      myCurrentFiles ++= dir.getFiles
      myCurrentDirs ++= dir.getSubdirectories.filter(d => !myExcludedDirs.contains(d))
    }

    private def expandDirectoriesUntilFilesNotEmpty() {
      while (myCurrentFiles.isEmpty && myCurrentDirs.nonEmpty) {
        val dir = myCurrentDirs.dequeue()
        expandDirectory(dir)
      }
    }

    def next(): PsiFile = {
      if (myCurrentFiles.isEmpty) throw new NoSuchElementException
      
      val current: PsiFile = myCurrentFiles.dequeue()
      expandDirectoriesUntilFilesNotEmpty()
      
      current
    }

    def hasNext: Boolean = myCurrentFiles.nonEmpty
  }
}
