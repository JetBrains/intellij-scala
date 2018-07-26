package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util.EventListener

import com.intellij.util.messages.Topic
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SbtCompilationListener.ProjectIdentifier.ProjectBase

trait SbtCompilationListener extends EventListener {
  import SbtCompilationListener._

  def beforeCompilationStart(project: ProjectBase): Unit                                                     = ()
  def connectionFailure(project: ProjectIdentifier): Unit                                                    = ()
  def compilationFinished(project: ProjectBase, success: Boolean, compilationInfoFile: Option[String]): Unit = ()
}

object SbtCompilationListener {
  trait ProjectIdentifier
  object ProjectIdentifier {
    final case class ProjectBase(path: String) extends ProjectIdentifier
    final object Unidentified                  extends ProjectIdentifier
  }

  val topic: Topic[SbtCompilationListener] =
    Topic.create[SbtCompilationListener]("sbt compilation status", classOf[SbtCompilationListener])
}
