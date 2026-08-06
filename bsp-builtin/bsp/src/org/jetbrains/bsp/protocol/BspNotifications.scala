package org.jetbrains.bsp.protocol


/** Notifications adapter for bsp4j */
object BspNotifications {
  import ch.epfl.scala.bsp4j

  sealed abstract class BspNotification
  final case class LogMessage(params: bsp4j.LogMessageParams) extends BspNotification
  final case class ShowMessage(params: bsp4j.ShowMessageParams) extends BspNotification
  final case class PublishDiagnostics(params: bsp4j.PublishDiagnosticsParams) extends BspNotification
  final case class TaskStart(params: bsp4j.TaskStartParams) extends BspNotification
  final case class TaskProgress(params: bsp4j.TaskProgressParams) extends BspNotification
  final case class TaskFinish(params: bsp4j.TaskFinishParams) extends BspNotification
  final case class DidChangeBuildTarget(didChange: bsp4j.DidChangeBuildTarget) extends BspNotification

}