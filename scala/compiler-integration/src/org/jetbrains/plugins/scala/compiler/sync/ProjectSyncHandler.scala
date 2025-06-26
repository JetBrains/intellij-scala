package org.jetbrains.plugins.scala.compiler.sync

sealed trait ProjectSyncHandler

object ProjectSyncHandler {
  case object ExternalSystem extends ProjectSyncHandler
  final case class Custom(syncAction: SyncAction) extends ProjectSyncHandler
}
