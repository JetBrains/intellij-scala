package org.jetbrains.sbt.shell.event

import java.util.Collections

import com.intellij.build.events._
import com.intellij.build.events.impl.AbstractBuildEvent
import com.intellij.build.{FileNavigatable, FilePosition, events}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellRunner}


// extensions to com.intellij.build.events for convenience use in sbt shell

abstract class SbtBuildEvent(parentId: Any, kind: MessageEvent.Kind, group: String, message: String)
  extends AbstractBuildEvent(new Object, parentId, System.currentTimeMillis(), message) with MessageEvent {

  override def getKind: MessageEvent.Kind = kind
  override def getGroup: String = group

  override def getResult: MessageEventResult =
    new MessageEventResult() {
      override def getKind: MessageEvent.Kind = kind
    }
}

trait SbtShellBuildEvent extends MessageEvent {

  override def getNavigatable(project: Project): Navigatable = {
    val shell = SbtProcessManager.forProject(project).acquireShellRunner
    SbtShellNavigatable(shell) // TODO pass some kind of position info
  }
}

trait SbtFileBuildEvent extends FileMessageEvent { outer =>

  val position: FilePosition

  override def getFilePosition: FilePosition = position
  override def getNavigatable(project: Project): Navigatable = new FileNavigatable(project, position)

  override def getResult: FileMessageEventResult = new FileMessageEventResult() {
    override def getFilePosition: FilePosition = position
    def getKind: MessageEvent.Kind = outer.getKind
  }
}

case class SbtShellBuildWarning(parentId: Any, message: String)
  extends SbtBuildEvent(parentId, MessageEvent.Kind.WARNING, "warnings", message) with SbtShellBuildEvent

case class SbtShellBuildError(parentId: Any, message: String)
  extends SbtBuildEvent(parentId, MessageEvent.Kind.ERROR, "errors", message) with SbtShellBuildEvent

case class SbtFileBuildError(parentId: Any, message: String, position: FilePosition)
  extends SbtBuildEvent(parentId, MessageEvent.Kind.ERROR, "errors", message) with SbtFileBuildEvent

case class SbtShellNavigatable(shell: SbtShellRunner) extends Navigatable {

  override def navigate(requestFocus: Boolean): Unit =
    if (canNavigate) {
      shell.openShell(requestFocus)
    }

  override def canNavigate: Boolean = true
  override def canNavigateToSource: Boolean = true
}


case class SbtBuildFailure(message: String) extends events.Failure {
  override def getMessage: String = message
  override def getDescription: String = null
  override def getError: Throwable = null
  override def getCauses: java.util.List[events.Failure] = Collections.emptyList()
}

case class SbtBuildWarning(message: String) extends Warning {
  override def getMessage: String = message
  override def getDescription: String = null
}
