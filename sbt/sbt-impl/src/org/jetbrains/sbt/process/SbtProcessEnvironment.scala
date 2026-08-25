package org.jetbrains.sbt.process

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.EnvironmentUtil
import org.jetbrains.sbt.SbtBundle

import scala.jdk.CollectionConverters.*
import scala.util.Try

/**
 * Prepares the environment of an sbt process started directly by the Scala plugin.
 *
 * `SBT_TERMINAL_PROPS` is set by an sbt client for the server subprocess it launches. A valid value makes sbt skip
 * its local console command channel. A process started by IDEA is controlled through stdin or a PTY instead, so it
 * must not inherit the variable.
 */
object SbtProcessEnvironment {
  private[process] final val SbtTerminalProps = "SBT_TERMINAL_PROPS"
  private val TerminalPropsSanitization = Registry.get("sbt.terminal.props.sanitization")

  /**
   * Environment and diagnostics prepared for an sbt process started by the Scala plugin.
   *
   * @param variables variables explicitly set for the child process after sanitization
   * @param warnings diagnostics emitted while preparing the child process environment
   */
  private[sbt] final case class PreparedEnvironment(
    variables: Map[String, String],
    warnings: Seq[EnvironmentWarning]
  )

  /**
   * A diagnostic emitted while preparing an sbt process environment.
   *
   * @param message short warning title
   * @param details details shown in the Build tool window, notifications, and support logs; consumers that render
   *                rich-text notifications must convert newline characters to line breaks
   */
  private[sbt] final case class EnvironmentWarning(
    message: String,
    details: String
  )

  private[sbt] def isTerminalPropsSanitizationEnabled: Boolean = TerminalPropsSanitization.asBoolean()

  private[sbt] def prepare(
    userSetEnvironment: Map[String, String],
    passParentEnvironment: Boolean,
    sanitizationEnabled: Boolean
  ): PreparedEnvironment =
    prepare(userSetEnvironment, passParentEnvironment, sanitizationEnabled, EnvironmentUtil.getEnvironmentMap.asScala.toMap)

  private[process] def prepare(
    userSetEnvironment: Map[String, String],
    passParentEnvironment: Boolean,
    sanitizationEnabled: Boolean,
    parentEnvironment: Map[String, String]
  ): PreparedEnvironment =
    prepare(userSetEnvironment, passParentEnvironment, sanitizationEnabled, parentEnvironment, SystemInfo.isWindows)

  private[process] def prepare(
    userSetEnvironment: Map[String, String],
    passParentEnvironment: Boolean,
    sanitizationEnabled: Boolean,
    parentEnvironment: Map[String, String],
    isWindows: Boolean
  ): PreparedEnvironment = {
    // Environment names are case-insensitive on Windows, so an explicit setting must override an inherited value
    // even if their key casing differs.
    val terminalPropsValue =
      findTerminalPropsValue(userSetEnvironment, isWindows).orElse {
        if (passParentEnvironment)
          findTerminalPropsValue(parentEnvironment, isWindows)
        else
          None
      }
    val terminalProps = terminalPropsValue.filter(isValidTerminalProps)
    val terminalPropsDetected = terminalProps.isDefined

    val variables =
      if (terminalPropsDetected && sanitizationEnabled)
        userSetEnvironment.filterNot { case (name, _) => isTerminalPropsName(name, isWindows) } + (SbtTerminalProps -> "")
      else
        userSetEnvironment

    val warnings = terminalProps.toSeq.map(createTerminalPropsWarning(_, sanitizationEnabled))

    PreparedEnvironment(variables, warnings)
  }

  private def createTerminalPropsWarning(value: String, sanitizationEnabled: Boolean): EnvironmentWarning = {
    val (message, explanation) =
      if (sanitizationEnabled)
        (
          SbtBundle.message("sbt.terminal.props.cleared"),
          SbtBundle.message("sbt.terminal.props.cleared.details")
        )
      else
        (
          SbtBundle.message("sbt.terminal.props.may.block.commands"),
          SbtBundle.message("sbt.terminal.props.may.block.commands.details")
        )
    val details = s"$explanation\n\n${SbtBundle.message("sbt.terminal.props.detected.value", value)}"
    EnvironmentWarning(message, details)
  }

  private def isTerminalPropsName(name: String, isWindows: Boolean): Boolean =
    if (isWindows)
      name.equalsIgnoreCase(SbtTerminalProps)
    else
      name == SbtTerminalProps

  private def findTerminalPropsValue(environment: Map[String, String], isWindows: Boolean): Option[String] =
    environment.collectFirst { case (name, value) if isTerminalPropsName(name, isWindows) => value }

  private def isValidTerminalProps(value: String): Boolean =
    value.split(",") match {
      case Array(width, height, ansi, color, supershell) =>
        Try {
          width.toInt
          height.toInt
          ansi.toBoolean
          color.toBoolean
          supershell.toBoolean
        }.isSuccess
      case _ => false
    }
}
