package org.jetbrains.plugins.scala

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, StandardProgressIndicator}
import org.jetbrains.plugins.scala.DependencyManagerBase.{DependencyDescription, ResolveFailure, Resolver, RichStr}
import org.junit.jupiter.api.Assertions.{assertThrows, fail}
import org.junit.jupiter.api.{Test, Timeout}

import java.text.ParseException
import java.util.concurrent.TimeUnit

class DependencyManagerTest {
  import DependencyManagerTest.*

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  def resolveSafeReturnsAFailureWhenTheResolutionThreadThrows(): Unit = {
    // The quote makes the generated ivy.xml malformed, so Ivy throws while parsing it on the resolution thread
    val malformedDependency = ("org.scala-lang" % "scala3-repl_3" % "3.8.0\"").transitive()
    OfflineDependencyManager().resolveSafe(malformedDependency) match {
      case Left(ResolveFailure.UnknownException(_: ParseException)) =>
      case other => fail(s"Unexpected result: $other")
    }
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  def resolveSafePropagatesCancellation(): Unit = {
    val indicator = DummyProgressIndicator()
    val manager = new OfflineDependencyManager {
      override protected def progressIndicator: Option[ProgressIndicator] = Some(indicator)
    }
    assertThrows(classOf[ProcessCanceledException], () => {
      manager.resolveSafe(TransitiveDependency)
      ()
    })
  }
}

private object DependencyManagerTest {

  val TransitiveDependency: DependencyDescription = ("org.scala-lang" % "scala3-repl_3" % "3.8.0").transitive()

  /** Only the local file system repositories from the Ivy settings are used, so the tests never access the network. */
  class OfflineDependencyManager extends DependencyManagerBase {
    override protected def resolvers: Seq[Resolver] = Nil
  }

  class DummyProgressIndicator extends StandardProgressIndicator {
    override def start(): Unit = ()
    override def stop(): Unit = ()
    override def isRunning: Boolean = true
    override def cancel(): Unit = ()
    override def isCanceled: Boolean = true
    override def setText(text: String): Unit = ()
    override def getText: String = ""
    override def setText2(text: String): Unit = ()
    override def getText2: String = ""
    override def getFraction: Double = 0
    override def setFraction(fraction: Double): Unit = ()
    override def pushState(): Unit = ()
    override def popState(): Unit = ()
    override def isModal: Boolean = false
    override def getModalityState: ModalityState = null
    override def setModalityProgress(modalityProgress: ProgressIndicator): Unit = ()
    override def isIndeterminate: Boolean = false
    override def setIndeterminate(indeterminate: Boolean): Unit = ()
    override def checkCanceled(): Unit = throw ProcessCanceledException()
    override def isPopupWasShown: Boolean = false
    override def isShowing: Boolean = false
  }
}
