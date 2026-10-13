package org.jetbrains.plugins.scala.compiler.highlighting.services.core.runner

import com.intellij.openapi.Disposable

trait DisposableRunner extends TaskRunner, Disposable:

  @volatile private var disposed = false

  abstract override def wakeUp(): Unit = if !disposed then super.wakeUp()

  abstract override def dispose(): Unit =
    disposed = true
    super.dispose()
