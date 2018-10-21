package org.jetbrains.bsp.protocol


/** Notifications adapter for bsp4j */
object Bsp4jNotifications {
  import ch.epfl.scala.bsp4j

  sealed abstract class Bsp4jNotification
  final case class LogMessage(params: bsp4j.LogMessageParams) extends Bsp4jNotification
  final case class ShowMessage(params: bsp4j.ShowMessageParams) extends Bsp4jNotification
  final case class PublishDiagnostics(params: bsp4j.PublishDiagnosticsParams) extends Bsp4jNotification
  final case class CompileReport(params: bsp4j.CompileReport) extends Bsp4jNotification
  final case class TestReport(params: bsp4j.TestReport) extends Bsp4jNotification

}