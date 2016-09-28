package org.jetbrains.plugins.scala.project.migration

import com.intellij.codeInsight.actions.FileTreeIterator
import com.intellij.codeInspection.{InspectionManager, ProblemsHolder}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.migration.api.{MigrationApiService, MigrationReport}
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationLocalFixHolderImpl
import org.jetbrains.plugins.scala.project.migration.defaultimpl.MyRecursiveVisitorWrapper

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
        case Failure(exception) =>
          val failureReport = MigrationReport.createSingleMessageReport(MigrationReport.Error, exception.getMessage +
            "\n" + exception.getStackTrace.take(ImportedLibrariesService.STACKTRACE_FROM_REPORT_CUT_SIZE).mkString("\n"))

          projectStructure showReport failureReport
      }
    }

    runWithProgress {
      indicator =>
        indicator setIndeterminate true

        migrators.foreach {
          migrator =>
            catchRun(migrator.migrateGlobal)
          //            indicatorHandler.tic()
        }

       
        val inspectionManager = InspectionManager.getInstance(project)
        val myIterator = projectStructure.inReadAction(new FileTreeIterator(project))

        while (myIterator.hasNext) projectStructure.inReadAction(myIterator.next()) match {
          case scalaFile: ScalaFile if !scalaFile.isCompiled && !scalaFile.isWorksheetFile =>
            val holder = new ProblemsHolder(inspectionManager, scalaFile, false)
            val fixHolder = new MigrationLocalFixHolderImpl(holder)

            val myCompoundAction = migrators.flatMap {
              migrator => migrator.migrateLocal(scalaFile, fixHolder)
            }

            val visitor = new MyRecursiveVisitorWrapper(myCompoundAction)
            projectStructure.inReadAction(scalaFile.acceptChildren(visitor))

            fixHolder.getFixes.foreach {
              case fix: AbstractFixOnPsiElement[_] =>
                ApplicationManager.getApplication.invokeLater(new Runnable {
                  override def run(): Unit = CommandProcessor.getInstance().executeCommand(project, new Runnable {
                    override def run(): Unit = {
                      catchRun {
                        service =>
                          projectStructure.inWriteAction(fix doApplyFix project)
                          None
                      }
                    }
                  }, "ScalaMigrator", null)
                })
              case _ =>
            }

          //                  indicatorHandler.tic()
          case _ =>
        }

    }
  }

  private def runWithProgress(action: ProgressIndicator => Unit) {
    new Task.Modal(project, "Migrating...", false) {
      override def run(indicator: ProgressIndicator): Unit = action.apply(indicator)
    }.queue()
  }
}
