package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.diagnostic.Logger

import java.util.concurrent.TimeUnit
import scala.collection.mutable

/**
 * Records named time instants and computes the elapsed time between them, for diagnostic phase timing in tests.
 *
 * Thread-safe, and the first write per name wins, so [[mark]] can be safely called from event callbacks that
 * may fire more than once and from background threads. `label` is used as a prefix when printing.
 */
private[highlighting] class PhaseTimeline(label: String) {
  private val instants = mutable.Map.empty[String, Long]

  def mark(name: String): Unit = synchronized {
    instants.getOrElseUpdate(name, System.nanoTime())
  }

  /** Milliseconds between two marks, or -1 if either was not recorded. */
  def elapsedMillis(from: String, to: String): Long = synchronized {
    (instants.get(from), instants.get(to)) match {
      case (Some(startNanos), Some(endNanos)) => TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos)
      case _ => -1L
    }
  }

  def print(report: String): Unit =
    PhaseTimeline.Log.info(s"$label: $report")
}

private[highlighting] object PhaseTimeline {
  private val Log: Logger = Logger.getInstance(classOf[PhaseTimeline])
}
