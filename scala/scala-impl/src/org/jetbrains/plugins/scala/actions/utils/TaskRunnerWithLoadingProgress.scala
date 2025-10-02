package org.jetbrains.plugins.scala.actions.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ModalityState, NonBlockingReadAction, ReadAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.{Disposer, NlsContexts}
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.{JBLabel, JBLoadingPanel}
import com.intellij.util.concurrency.{AppExecutorUtil, EdtScheduler}
import com.intellij.util.ui.{AnimatedIcon, AsyncProcessIcon, EmptyIcon, UIUtil}
import org.jetbrains.annotations.{ApiStatus, Nullable}
import org.jetbrains.concurrency.CancellablePromise

import java.awt.{Component, FlowLayout}
import java.util.Objects
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import javax.swing.{JLabel, JPanel}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.chaining.scalaUtilChainingOps

/**
 * This utility class is designed to be able to run a composite action that:
 *  1. Launches a background task in a non blockable read action with some computation
 *  1. Shows a progress popup while the computation is running
 *  1. Present the computation results on the UI thread
 *
 * The trickiest part is the logic around showing the loading placeholder tooltip and handling cancellations.
 * Without this requirement we could simply use the API in [[com.intellij.openapi.application.ReadAction]]
 * and [[com.intellij.openapi.application.NonBlockingReadAction]]
 *
 * Copied from [[com.intellij.codeInsight.hint.ParameterInfoTaskRunnerUtil]] and converted to Scala.
 * No major significant changes were made in the initial commit.
 */
@ApiStatus.Experimental
private[actions] object TaskRunnerWithLoadingProgress {
  private val DefaultProgressPopupDelay: Duration = 1000.millis

  private def getFocusOwner(project: Project): Component = IdeFocusManager.getInstance(project).getFocusOwner

  def runSingleInstanceTask[T](
    project: Project,
    backgroundDataSupplier: () => T,
    uiDataConsumer: T => Unit,
    @Nullable @NlsContexts.ProgressTitle
    progressTitle: String,
    editor: Editor,
    cancelOnScrolling: Boolean,
    coalesceObject: AnyRef
  ): Unit = {
    val backgroundAction = ReadAction
      .nonBlocking[T](() => {
        backgroundDataSupplier()
      })
      .coalesceBy(coalesceObject)

    TaskRunnerWithLoadingProgress.runTask(
      project = project,
      backgroundAction = backgroundAction,
      uiDataConsumer = result => uiDataConsumer(result),
      progressTitle = progressTitle,
      editor = editor,
      cancelOnScrolling = cancelOnScrolling
    )
  }

  /**
   * @param progressTitle     null means no loading panel should be shown
   * @param cancelOnScrolling cancel execution on scrolling
   */
  def runTask[T](
    project: Project,
    backgroundAction: NonBlockingReadAction[T],
    uiDataConsumer: Consumer[_ >: T],
    @Nullable @NlsContexts.ProgressTitle
    progressTitle: String,
    editor: Editor,
    cancelOnScrolling: Boolean
  ): Unit = {
    // Unfortunately, the Editor interface is not disposable, so we fall back to the project as disposable.
    // Though the main implementation `EditorImpl` has the disposable.
    val editorOrProjectDisposable = editor match {
      case impl: EditorImpl => impl.getDisposable
      case _ => project
    }

    val cancellablePromiseRef = new AtomicReference[CancellablePromise[_]]()
    val (stopAction, stopActionDisposable) =
      startProgressAndCreateStopAction(project, progressTitle, cancellablePromiseRef, editor, editorOrProjectDisposable)

    val originalFocusOwner = getFocusOwner(project)

    def actionNotCancelledAndFocusIsNotLost: Boolean = {
      val cancellablePromise = cancellablePromiseRef.get()
      val actionSucceeded = cancellablePromise != null && cancellablePromise.isSucceeded

      // NOTE: this check is probably redundant, because if we cancel the action and if the progress tooltip is disposed,
      // it should automatically cancel the ReadAction, which is achieved by NonBlockingReadAction.expireWith method
      // It was originally in the ParameterInfoTaskRunnerUtil, but it doesn't cancel the computation on loading progress disposal
      if (actionSucceeded) {
        val focusOwner = getFocusOwner(project)

        // NOTE: this check is probably redundant, because if we change the focus, it should cancel the loading Action
        // (as per logic in startProgressAndCreateStopAction and JBPopupFactory.createComponentPopupBuilder)
        // And this in its turn cancels the read action (see the above comment)
        Objects.equals(originalFocusOwner, focusOwner)
      }
      else false
    }

    val uiDataConsumerWithCancellationCheck: Consumer[_ >: T] = result => {
      if (actionNotCancelledAndFocusIsNotLost) {
        uiDataConsumer.accept(result)
      }
    }

    val visibleAreaListener = new CancelProgressOnScrolling(cancellablePromiseRef)
    if (cancelOnScrolling) {
      editor.getScrollingModel.addVisibleAreaListener(visibleAreaListener)
    }

    val submittedActionPromise = backgroundAction
      .finishOnUiThread(
        ModalityState.defaultModalityState(),
        uiDataConsumerWithCancellationCheck
      )
      .expireWith(editorOrProjectDisposable)
      // If there is a progress tooltip, expire the task if we cancel it (via Escape key or on focus change)
      .pipe(action => stopActionDisposable.fold(action)(action.expireWith))
      .submit(AppExecutorUtil.getAppExecutorService)
      .onProcessed(_ => {
        stopAction.accept(false)
        if (cancelOnScrolling) {
          editor.getScrollingModel.removeVisibleAreaListener(visibleAreaListener)
        }
      })

    cancellablePromiseRef.set(submittedActionPromise)
  }

  /**
   * @note Again, the implementation is copied from `com.intellij.codeInsight.hint.ParameterInfoTaskRunnerUtil`
   */
  private def startProgressAndCreateStopAction(
    project: Project,
    @Nullable @NlsContexts.ProgressTitle
    progressTitle: String,
    promiseRef: AtomicReference[_ <: CancellablePromise[_]],
    editor: Editor,
    editorOrProjectDisposable: Disposable
  ): (Consumer[Boolean], Option[Disposable]) = {
    val stopActionRef = new AtomicReference[Consumer[Boolean]]

    val originalStopAction: Consumer[Boolean] = (cancel: Boolean) => {
      stopActionRef.set(null)

      if (cancel) {
        val promise = promiseRef.get()
        if (promise != null) {
          promise.cancel()
        }
      }
    }

    if (progressTitle == null) {
      stopActionRef.set(originalStopAction)
      (stopActionRef.get(), None)
    } else {
      val disposable = Disposer.newDisposable()
      Disposer.register(editorOrProjectDisposable, disposable)

      val loadingPanel = new JBLoadingPanel(null, panel =>
        new LoadingDecorator(panel, disposable, 0, false, new AsyncProcessIcon(s"ProgressUtils(title: $progressTitle)")) {
          override protected def customizeLoadingLayer(parent: JPanel, text: JLabel, icon: AnimatedIcon): NonOpaquePanel = {
            parent.setLayout(new FlowLayout(FlowLayout.LEFT))
            val result = new NonOpaquePanel
            result.add(icon)
            parent.add(result)
            result
          }
        })
      loadingPanel.add(new JBLabel(EmptyIcon.ICON_18))
      loadingPanel.add(new JBLabel(progressTitle))

      val popupBuilder = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(loadingPanel, null)
        .setProject(project)
        .setCancelCallback(() => {
          val stopAction = stopActionRef.get()
          if (stopAction != null) {
            stopAction.accept(true)
          }
          true
        })

      val popup = popupBuilder.createPopup()
      Disposer.register(disposable, popup)

      val showPopupFuture: kotlinx.coroutines.Job = EdtScheduler.getInstance.schedule(
        DefaultProgressPopupDelay.toMillis.toInt,
        ModalityState.defaultModalityState(),
        () => {
          val needToShowPopup = !popup.isDisposed && !popup.isVisible && !editor.isDisposed
          if (needToShowPopup) {
            val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
            loadingPanel.startLoading()
            popup.show(popupPosition)
          }
        }
      )

      stopActionRef.set((cancel: Boolean) => {
        try {
          loadingPanel.stopLoading()
          originalStopAction.accept(cancel)
        } finally {
          showPopupFuture.cancel(null)

          UIUtil.invokeLaterIfNeeded(() => {
            if (popup.isVisible) {
              popup.setUiVisible(false)
            }
            Disposer.dispose(disposable)
          })
        }
      })

      (stopActionRef.get(), Some(disposable))
    }
  }
}