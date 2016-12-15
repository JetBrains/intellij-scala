package org.jetbrains.sbt.shell

import java.io.{OutputStreamWriter, PrintWriter}

import com.intellij.execution.process.{ProcessAdapter, ProcessEvent}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.task.ProjectTaskResult

import scala.concurrent.{Future, Promise}
import scala.util.Success

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by jast on 2016-11-06.
  */
class SbtShellCommunication(project: Project) {

  private lazy val process = SbtProcessManager.forProject(project)

  // TODO ask sbt to provide completions for a line via its parsers
  def completion(line: String): List[String] = List.empty

  /**
    * Execute an sbt task.
    */
  def task(task: String): Future[ProjectTaskResult] = {

    val handler = process.acquireShellProcessHandler
    val listener = new SbtProcessListener
    handler.addProcessListener(listener)

    // TODO more robust way to get the writer? cf createOutputStreamWriter.createOutputStreamWriter
    val shell = new PrintWriter(new OutputStreamWriter(handler.getProcessInput))

    // TODO queue the task instead of direct execution.
    // we want to avoid registering multiple callbacks to the same output and having whatever side effects
    // TODO build selected module?
    shell.println(task)
    shell.flush()

    listener.future.andThen {
      case _ => handler.removeProcessListener(listener)
    }.recover {
      case _ =>
        // TODO some kind of feedback / rethrow
        new ProjectTaskResult(true, 1, 0)
    }
  }
}

class SbtProcessListener extends ProcessAdapter {

  private var success = false
  private var errors = 0
  private var warnings = 0

  private val promise = Promise[ProjectTaskResult]()

  def future: Future[ProjectTaskResult] = promise.future

  override def processTerminated(event: ProcessEvent): Unit = {
    val res = new ProjectTaskResult(true, errors, warnings)
    promise.complete(Success(res))
  }

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
    val text = event.getText
    if (text startsWith "[error]") {
      success = false
      errors += 1
    } else if (text startsWith "[warning]") {
      warnings += 1
    }
    else if (text contains "[success]")
      success = true

    if (!promise.isCompleted && taskCompleted(text)) {
      val res = new ProjectTaskResult(false, errors, warnings)
      promise.complete(Success(res))
    }
  }

  private def taskCompleted(line: String) =
  // TODO smarter conditions? see idea-sbt-plugin: SbtRunner.execute
    line.startsWith("> ")
}