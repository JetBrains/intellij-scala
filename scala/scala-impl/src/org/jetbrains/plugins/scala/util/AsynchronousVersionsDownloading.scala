package org.jetbrains.plugins.scala.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{EmptyProgressIndicator, ProcessCanceledException, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.template.SearchingListCellRenderer
import org.jetbrains.sbt.project.template.SComboBox

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.immutable.ListSet
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait AsynchronousVersionsDownloading {

  val Log: Logger = Logger.getInstance(this.getClass)

  protected def downloadVersionsAsynchronously[V](
    isRunning: AtomicBoolean,
    disposable: Disposable,
    downloadVersions: ProgressIndicator => V,
    versionType: String
  )(successCallback: V => Unit): Unit = {
    // note: we mock indicator for com.intellij.openapi.progress.ProgressManager.runProcessWithProgressAsynchronously
    // It will allow to cancel downloading process when user creates the project with default values or closes the new project wizard
    val indicator = new EmptyProgressIndicator
    registerChildDisposable(disposable, indicator)
    val onComplete: Try[V] => Unit = { result =>
      result match {
        case Success(x) => successCallback(x)
        case Failure(exception) if !exception.is[ProcessCanceledException] => Log.debug(s"Exception during downloading of $versionType versions", exception)
        case _ =>
      }
      isRunning.set(false)
    }
    val task = createBackgroundableTask(ScalaBundle.message("title.fetching.available.this.versions", versionType), onComplete) {
      isRunning.set(true)
      downloadVersions(indicator)
    }
    ProgressManager.getInstance.runProcessWithProgressAsynchronously(task, indicator)
  }

  private def registerChildDisposable(disposable: Disposable, indicator: ProgressIndicator): Unit =
    Disposer.register(disposable, () =>
      indicator.cancel()
    )

  protected def createSComboBoxWithSearchingListRenderer[T <: Object : ClassTag](elements: ListSet[T], textCustomizer: Option[T => String], isLoading: AtomicBoolean): SComboBox[T] = {
    val searchingListCellRenderer = new SearchingListCellRenderer[T](isLoading, textCustomizer)
    new SComboBox(elements.toArray, 150, searchingListCellRenderer, true)
  }

  private def createBackgroundableTask[T](@Nls title: String, onComplete: Try[T] => Unit)(operationToExecute: => T): Task.Backgroundable = {
    new Task.Backgroundable(null, title, false) {
      override def run(indicator: ProgressIndicator): Unit = {
        onComplete(Try(operationToExecute))
      }

      override def onCancel(): Unit = {
        onComplete(Failure(new ProcessCanceledException()))
      }
    }
  }

}
