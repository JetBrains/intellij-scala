package org.jetbrains.sbt.process

import org.junit.Assert.assertEquals
import org.junit.Test
import org.jetbrains.sbt.SbtBundle

class SbtProcessEnvironmentTest {
  import SbtProcessEnvironment.*

  private def clearedWarning(value: String) = environmentWarning(
    "sbt.terminal.props.cleared",
    "sbt.terminal.props.cleared.details",
    value
  )
  private def mayBlockCommandsWarning(value: String) = environmentWarning(
    "sbt.terminal.props.may.block.commands",
    "sbt.terminal.props.may.block.commands.details",
    value
  )

  private def environmentWarning(messageKey: String, detailsKey: String, value: String) = {
    val message = SbtBundle.message(messageKey)
    val explanation = SbtBundle.message(detailsKey)
    val details = s"$explanation\n\n${SbtBundle.message("sbt.terminal.props.detected.value", value)}"
    EnvironmentWarning(message, details)
  }

  @Test
  def inheritedTerminalPropsAreMaskedWhenSanitizationIsEnabled(): Unit = {
    val prepared = prepare(
      userSetEnvironment = Map("CUSTOM" -> "value"),
      passParentEnvironment = true,
      sanitizationEnabled = true,
      parentEnvironment = Map(SbtTerminalProps -> "0,0,false,false,false")
    )

    assertEquals(Seq(clearedWarning("0,0,false,false,false")), prepared.warnings)
    assertEquals(Map("CUSTOM" -> "value", SbtTerminalProps -> ""), prepared.variables)
  }

  @Test
  def explicitEnvironmentOverridesInheritedTerminalProps(): Unit = {
    val prepared = prepare(
      userSetEnvironment = Map(SbtTerminalProps -> ""),
      passParentEnvironment = true,
      sanitizationEnabled = true,
      parentEnvironment = Map(SbtTerminalProps -> "0,0,false,false,false")
    )

    assertEquals(Seq.empty, prepared.warnings)
    assertEquals(Map(SbtTerminalProps -> ""), prepared.variables)
  }

  @Test
  def explicitTerminalPropsAreMaskedWhenSanitizationIsEnabled(): Unit = {
    val prepared = prepare(
      userSetEnvironment = Map(SbtTerminalProps -> "0,0,false,false,false", "CUSTOM" -> "value"),
      passParentEnvironment = false,
      sanitizationEnabled = true,
      parentEnvironment = Map.empty
    )

    assertEquals(Seq(clearedWarning("0,0,false,false,false")), prepared.warnings)
    assertEquals(Map(SbtTerminalProps -> "", "CUSTOM" -> "value"), prepared.variables)
  }

  @Test
  def parentTerminalPropsAreIgnoredWhenParentEnvironmentIsDisabled(): Unit = {
    val prepared = prepare(
      userSetEnvironment = Map("CUSTOM" -> "value"),
      passParentEnvironment = false,
      sanitizationEnabled = true,
      parentEnvironment = Map(SbtTerminalProps -> "0,0,false,false,false")
    )

    assertEquals(Seq.empty, prepared.warnings)
    assertEquals(Map("CUSTOM" -> "value"), prepared.variables)
  }

  @Test
  def disabledSanitizationPreservesTerminalProps(): Unit = {
    val environment = Map(SbtTerminalProps -> "0,0,false,false,false", "CUSTOM" -> "value")
    val prepared = prepare(
      userSetEnvironment = environment,
      passParentEnvironment = false,
      sanitizationEnabled = false,
      parentEnvironment = Map.empty
    )

    assertEquals(Seq(mayBlockCommandsWarning("0,0,false,false,false")), prepared.warnings)
    assertEquals(environment, prepared.variables)
  }

  @Test
  def emptyTerminalPropsDoNotRequireSanitization(): Unit = {
    val environment = Map(SbtTerminalProps -> "", "CUSTOM" -> "value")
    val prepared = prepare(
      userSetEnvironment = environment,
      passParentEnvironment = false,
      sanitizationEnabled = true,
      parentEnvironment = Map.empty
    )

    assertEquals(Seq.empty, prepared.warnings)
    assertEquals(environment, prepared.variables)
  }

  @Test
  def terminalPropsNameIsCaseInsensitiveOnWindows(): Unit = {
    val prepared = prepare(
      userSetEnvironment = Map("sbt_terminal_props" -> "0,0,false,false,false", "CUSTOM" -> "value"),
      passParentEnvironment = false,
      sanitizationEnabled = true,
      parentEnvironment = Map.empty,
      isWindows = true
    )

    assertEquals(Seq(clearedWarning("0,0,false,false,false")), prepared.warnings)
    assertEquals(Map(SbtTerminalProps -> "", "CUSTOM" -> "value"), prepared.variables)
  }

  @Test
  def explicitWindowsEnvironmentOverridesInheritedTerminalPropsRegardlessOfKeyCasing(): Unit = {
    val prepared = prepare(
      userSetEnvironment = Map("sbt_terminal_props" -> ""),
      passParentEnvironment = true,
      sanitizationEnabled = true,
      parentEnvironment = Map(SbtTerminalProps -> "0,0,false,false,false"),
      isWindows = true
    )

    assertEquals(Seq.empty, prepared.warnings)
    assertEquals(Map("sbt_terminal_props" -> ""), prepared.variables)
  }

  @Test
  def malformedTerminalPropsDoNotRequireSanitization(): Unit = {
    val environment = Map(SbtTerminalProps -> "invalid", "CUSTOM" -> "value")
    val prepared = prepare(
      userSetEnvironment = environment,
      passParentEnvironment = false,
      sanitizationEnabled = true,
      parentEnvironment = Map.empty
    )

    assertEquals(Seq.empty, prepared.warnings)
    assertEquals(environment, prepared.variables)
  }
}
