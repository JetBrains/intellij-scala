package org.jetbrains.bsp.protocol


/**
  * Notifications for bsp4s
  */
object Bsp4sNotifications {
  import ch.epfl.scala.bsp

  sealed abstract class Bsp4sNotification
  final case class LogMessage(params: bsp.LogMessageParams) extends Bsp4sNotification
  final case class ShowMessage(params: bsp.ShowMessageParams) extends Bsp4sNotification
  final case class PublishDiagnostics(params: bsp.PublishDiagnosticsParams) extends Bsp4sNotification
  final case class CompileReport(params: bsp.CompileReport) extends Bsp4sNotification
  final case class TestReport(params: bsp.TestReport) extends Bsp4sNotification

}

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