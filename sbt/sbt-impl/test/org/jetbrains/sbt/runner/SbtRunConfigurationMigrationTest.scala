package org.jetbrains.sbt.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.util.stream.Stream

/**
 * Unit tests for the migration logic between the old `tasks` field and the new `commands` field in [[SbtRunConfiguration]].
 *
 * The test suite covers:
 *  - forward migration (`tasks → commands`)
 *  - backward migration (`commands → tasks`)
 */
class SbtRunConfigurationMigrationTest:

  @ParameterizedTest(name = "{2}")
  @MethodSource(Array("migrateTasksToCommandsData"))
  def testMigrateTasksToCommands(inputTasks: String, expectedCommands: String, testName: String): Unit =
    assertEquals(expectedCommands, SbtRunConfiguration.migrateTasksToCommands(inputTasks), testName)

  @ParameterizedTest(name = "{2}")
  @MethodSource(Array("migrateCommandsToTasksData"))
  def testMigrateCommandsToTasks(inputCommands: String, expectedTasks: String, testName: String): Unit =
    assertEquals(expectedTasks, SbtRunConfiguration.migrateCommandsToTasks(inputCommands), testName)


object SbtRunConfigurationMigrationTest:

  /** Wraps `s` in double quotes, so the test data are easier to read. */
  private def quoted(s: String): String = "\"" + s + "\""

  // Input tasks | Expected commands | Test name
  def migrateTasksToCommandsData(): Stream[Array[String]] = Stream.of(
    Array("task1",                    "task1",                 "single task"),
    Array(quoted("task1"),            "task1",                 "quoted single task"),
    Array("task1 task1",              "task1; task1",          "two space-separated tasks"),
    Array(quoted("task1 task1"),      "task1 task1",           "quoted two space-separated tasks"),
    Array("task1;task1",              "task1;task1",           "two tasks with semicolon (no space)"),
    Array(quoted("task1;task1"),      "task1;task1",           "quoted two tasks with semicolon (no space)"),
    Array("task1; task1",             "task1; task1",          "two tasks with semicolon and space"),
    Array(quoted("task1; task1"),     "task1; task1",          "quoted two tasks with semicolon and space"),
    Array("add 1 2",                  "add; 1; 2",             "task with two arguments"),
    Array(quoted("add 1 2"),          "add 1 2",               "quoted task with two arguments"),
    Array("task1; add 1 2",           "task1; add 1 2",        "task semicolon task with arguments"),
    Array(quoted("task1; add 1 2"),   "task1; add 1 2",        "quoted task semicolon task with arguments"),
    Array("",                         "",                      "empty string"),
    Array("  ",                       "  ",                    "white spaces"),
    Array("  task1  ",                "task1",                 "task with whitespaces") // trimming happens in ParametersListUtil.parse in SbtRunConfiguration.migrateTasksToCommands
  )

  // Input commands | Expected tasks | Test name
  def migrateCommandsToTasksData(): Stream[Array[String]] = Stream.of(
    Array("task1",                    "task1",                     "single task"),
    Array(quoted("task1"),            quoted("task1"),             "quoted single task"),
    Array("task1 task1",              quoted("task1 task1"),       "two space-separated tasks"),
    Array(quoted("task1 task1"),      quoted("task1 task1"),       "quoted two space-separated tasks"),
    Array("task1;task1",              "task1;task1",               "two tasks with semicolon (no space)"),
    Array(quoted("task1;task1"),      quoted("task1;task1"),       "quoted two tasks with semicolon (no space)"),
    Array("task1; task1",             quoted("task1; task1"),      "two tasks with semicolon and space"),
    Array(quoted("task1; task1"),     quoted("task1; task1"),      "quoted two tasks with semicolon and space"),
    Array("add 1 2",                  quoted("add 1 2"),           "task with two arguments"),
    Array(quoted("add 1 2"),          quoted("add 1 2"),           "quoted task with two arguments"),
    Array("task1; add 1 2",           quoted("task1; add 1 2"),    "task semicolon task with arguments"),
    Array(quoted("task1; add 1 2"),   quoted("task1; add 1 2"),    "quoted task semicolon task with arguments"),
    Array("",                         "",                          "empty string"),
    Array("  ",                       "  ",                        "white spaces"),
    Array("  task1  ",                quoted("  task1  "),         "task with whitespaces")
  )
