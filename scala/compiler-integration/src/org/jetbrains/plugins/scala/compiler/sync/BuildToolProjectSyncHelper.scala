package org.jetbrains.plugins.scala.compiler.sync

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

import scala.jdk.CollectionConverters._

@ApiStatus.Internal
trait BuildToolProjectSyncHelper {
  def projectSyncHandler(project: Project): Option[ProjectSyncHandler]
}

object BuildToolProjectSyncHelper {
  val EpName: ExtensionPointName[BuildToolProjectSyncHelper] = ExtensionPointName.create("org.intellij.scala.buildToolProjectSyncHelper")

  def projectSyncHandlers(project: Project): Seq[ProjectSyncHandler] =
    EpName.getExtensionList.asScala.toSeq.flatMap(_.projectSyncHandler(project))
}
