package org.jetbrains.bsp.protocol


/** Notifications adapter for bsp4j */
object BspNotifications {
  import ch.epfl.scala.bsp4j

  sealed abstract class BspNotification
  final case class LogMessage(params: bsp4j.LogMessageParams) extends BspNotification
  final case class ShowMessage(params: bsp4j.ShowMessageParams) extends BspNotification
  final case class PublishDiagnostics(params: bsp4j.PublishDiagnosticsParams) extends BspNotification
  final case class CompileReport(params: bsp4j.CompileReport) extends BspNotification
  final case class TestReport(params: bsp4j.TestReport) extends BspNotification

}