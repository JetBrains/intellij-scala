package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.incremental.Highlighting._
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Success

class ScalaDfaVisitor(project: Project, file: PsiFile, val run: ScFunctionDefinition => Unit) extends ScalaElementVisitor {
  override def visitFunctionDefinition(function: ScFunctionDefinition): Unit = {
    if (!function.isVisible(project, file)) return

    run(function)
  }
}

object ScalaDfaVisitor {
  class AsyncProvider(project: Project, file: PsiFile) {
    private val mutex = new Object
    private val pendingFutures = ArrayBuffer[(Future[Option[ScalaDfaResult]], ScalaDfaResult => Unit)]()

    def visitor(report: ScalaDfaResult => Unit) = new ScalaDfaVisitor(project, file, processFunctionDef(report))

    def finish(): Unit = mutex.synchronized {
//      println(s"[$this] Finishing ${pendingFutures.size} pending futures")
      for ((fut, report) <- pendingFutures) {
        try Await.result(fut, Duration.Inf).foreach(report)
        catch {
          case e: Throwable =>
//            println(s"[$this] Exception while waiting for future: ${e.getMessage}")
        }
      }
      pendingFutures.clear()
    }

    private def processFunctionDef(report: ScalaDfaResult => Unit)(function: ScFunctionDefinition): Unit = mutex.synchronized {
      val fut = DfaManager.getDfaResultFor(function)

      fut.value match {
        case Some(Success(result)) =>
          result.foreach(report)

        case _ =>
          synchronized {
            pendingFutures += ((fut, report))
          }
      }
    }
  }
}