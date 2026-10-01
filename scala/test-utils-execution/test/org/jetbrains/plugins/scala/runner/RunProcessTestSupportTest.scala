package org.jetbrains.plugins.scala.runner

import com.intellij.openapi.util.Disposer
import org.junit.Assert.{assertSame, assertThrows}
import org.junit.Test

import java.util.concurrent.{ExecutionException, TimeUnit}

class RunProcessTestSupportTest {

  // TODO: Cover processStarted with an already-terminated handler, a null descriptor,
  // and a descriptor without a handler.
  @Test
  def processNotStartedCompletesStartupWithItsCause(): Unit = {
    val disposable = Disposer.newDisposable()
    try {
      val failure = new IllegalStateException("Process could not start")
      val run = new RunProcessTestSupport(disposable)

      run.processNotStarted(failure)

      val error = assertThrows(classOf[ExecutionException], () => run.startedFuture.get(1, TimeUnit.SECONDS))
      assertSame(failure, error.getCause)
    } finally {
      Disposer.dispose(disposable)
    }
  }
}
