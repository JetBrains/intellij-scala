package org.jetbrains.plugins.scala.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, ProgressManager, Task}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.template.SearchingListCellRenderer
import org.jetbrains.sbt.project.template.SComboBox

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.immutable.ListSet
import scala.concurrent.Promise
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait AsynchronousVersionsDownloading {

  val Log: Logger = Logger.getInstance(this.getClass)

  protected def downloadVersionsAsynchronously[T](isRunning: AtomicBoolean, indicator: ProgressIndicator, versions: => T, versionType: String)(successCallback: T => Unit): Unit = {
    val promise = Promise[T]()
    val task = createBackgroundableTask(ScalaBundle.message("title.fetching.available.this.versions", versionType), promise) {
      isRunning.set(true)
      versions
    }
    import scala.concurrent.ExecutionContext.Implicits.global
    ProgressManager.getInstance.runProcessWithProgressAsynchronously(task, indicator)
    promise.future.onComplete { result =>
      result match {
        case Success(x) => successCallback(x)
        case Failure(exception) => Log.debug(s"Exception during downloading of $versionType versions", exception)
        case _ =>
      }
      isRunning.set(false)
    }
  }

  protected def createSComboBoxWithSearchingListRenderer[T <: Object : ClassTag](elements: ListSet[T], textCustomizer: Option[T => String], isLoading: AtomicBoolean): SComboBox[T] = {
    val searchingListCellRenderer = new SearchingListCellRenderer[T](isLoading, textCustomizer)
    new SComboBox(elements.toArray, 150, searchingListCellRenderer, true)
  }

  private def createBackgroundableTask[T](@Nls title: String, promise: Promise[T])(task: => T): Task.Backgroundable = {
    new Task.Backgroundable(null, title, false) {
      override def run(indicator: ProgressIndicator): Unit = {
        promise.tryComplete(Try(task))
      }

      override def onCancel(): Unit = {
        promise.tryFailure(new ProcessCanceledException())
      }
    }
  }

}
