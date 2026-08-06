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
    // Valid/invalid in the comments below refer to whether the given command works in the corresponding plugin version
    // (tasks in the old one and commands in the new one).

    // echo "fo; o" (invalid) -> echo; fo; o (invalid)
    Array("echo \"fo; o\"",           "echo; fo; o",           "task with quoted arg containing semicolon"),
    // echo \"fo; o\" (invalid) -> echo "fo; o" (valid)
    Array("echo \\\"fo; o\\\"",       "echo \"fo; o\"",        "task with escaped quoted arg containing semicolon"),
    // "echo \"fo; o\"" (valid) -> echo "fo; o" (valid)
    Array(quoted("echo \\\"fo; o\\\""), "echo \"fo; o\"",      "task with escaped quoted arg containing semicolon (quoted)"),

    // task1;"add 1 2" (valid) -> task1;add 1 2 (valid)
    Array("task1;\"add 1 2\"",        "task1;add 1 2",         "task then quoted task with arguments"),
    // task1;\"add 1 2\" (invalid) -> task1;"add 1 2" (invalid)
    Array("task1;\\\"add 1 2\\\"",    "task1;\"add 1 2\"",     "task then escaped quoted task with arguments"),
    // "task1;\"add 1 2\"" (invalid) -> task1;"add 1 2" (invalid)
    Array(quoted("task1;\\\"add 1 2\\\""), "task1;\"add 1 2\"", "task then escaped quoted task with arguments (quoted)"),

    // task1;echo "fo; o" (invalid) -> task1;echo fo; o (invalid)
    Array("task1;echo \"fo; o\"",     "task1;echo fo; o",      "task then task with quoted arg containing semicolon"),
    // task1;echo \"fo; o\" (invalid) -> task1;echo "fo; o" (valid)
    Array("task1;echo \\\"fo; o\\\"", "task1;echo \"fo; o\"",  "task then task with escaped quoted arg containing semicolon"),
    // "task1;echo \"fo; o\"" (valid) -> task1;echo "fo; o" (valid)
    Array(quoted("task1;echo \\\"fo; o\\\""), "task1;echo \"fo; o\"", "task then task with escaped quoted arg containing semicolon (quoted)"),

    // task1;"task2";task3 (valid) -> task1;task2;task3 (valid)
    Array("task1;\"task2\";task3",    "task1;task2;task3",     "quoted task between plain tasks"),
    // task1;\"task2\";task3 (invalid) -> task1;"task2";task3 (invalid)
    Array("task1;\\\"task2\\\";task3", "task1;\"task2\";task3", "escaped quoted task between plain tasks"),
    // "task1;\"task2\";task3" (invalid) -> task1;"task2";task3 (invalid)
    Array(quoted("task1;\\\"task2\\\";task3"), "task1;\"task2\";task3", "escaped quoted task between plain tasks (quoted)"),

    // When there is no unquoted ;, an explicit ; is used as the separator, just as it was in the past.
    Array("task1 \"echo \\\"a;b\\\"\"",        "task1; echo \"a;b\"",        "quoted semicolon, space separators"),
    Array("task1 \"echo \\\"a;b\\\"\" task2",  "task1; echo \"a;b\"; task2", "quoted semicolon between two tasks"),

    Array(";echo \"a b\"",           ";echo \"a b\"",           "leading semicolon with quoted arg"),
    Array(";task1 ;echo \"a b\"",    ";task1 ;echo \"a b\"",    "leading semicolon, two commands, quoted arg"),
    Array(";echo \\\"a b\\\"",       ";echo \\\"a b\\\"",       "leading semicolon with escaped quotes"),
    Array(";task1 ;task2",           ";task1 ;task2",           "leading semicolon without quotes"),
    Array("  ;task1",                "  ;task1",                "leading whitespace before semicolon"),
    Array(quoted(";task1 ;task2"),   ";task1 ;task2",           "quoted content starting with semicolon"),

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
    // Valid/invalid in the comments below refer to whether the given command works in the corresponding plugin version
    // (tasks in the old one and commands in the new one).

    // echo "fo; o" (valid) -> "echo \"fo; o\"" (valid)
    Array("echo \"fo; o\"",                  quoted("echo \\\"fo; o\\\""),        "task with quoted arg containing semicolon"),
    // echo \"fo; o\" (valid) -> "echo \\"fo; o\\"" (valid)
    Array("echo \\\"fo; o\\\"",              quoted("echo \\\\\"fo; o\\\\\""),    "task with escaped quoted arg containing semicolon"),
    // "echo \"fo; o\"" (invalid) -> unchanged (valid)
    Array(quoted("echo \\\"fo; o\\\""),      quoted("echo \\\"fo; o\\\""),        "task with escaped quoted arg containing semicolon (quoted)"),

    // task1;"add 1 2" (invalid) -> unchanged (valid)
    Array("task1;\"add 1 2\"",               "task1;\"add 1 2\"",                 "task then quoted task with arguments"),
    // task1;\"add 1 2\" (invalid) -> "task1;\\"add 1 2\\"" (invalid)
    Array("task1;\\\"add 1 2\\\"",           quoted("task1;\\\\\"add 1 2\\\\\""), "task then escaped quoted task with arguments"),
    // "task1;\"add 1 2\""(invalid)  -> unchanged (invalid)
    Array(quoted("task1;\\\"add 1 2\\\""),   quoted("task1;\\\"add 1 2\\\""),     "task then escaped quoted task with arguments (quoted)"),

    // task1;echo "fo; o" (valid) -> "task1;echo \"fo; o\"" (valid)
    Array("task1;echo \"fo; o\"",            quoted("task1;echo \\\"fo; o\\\""),  "task then task with quoted arg containing semicolon"),
    // task1;echo \"fo; o\" (Valid or invalid, depending on whether the old or the new shell is used)-> "task1;echo \\"fo; o\\"" (the same as before)
    Array("task1;echo \\\"fo; o\\\"",        quoted("task1;echo \\\\\"fo; o\\\\\""), "task then task with escaped quoted arg containing semicolon"),
    // "task1;echo \"fo; o\"" (invalid) -> unchanged (valid)
    Array(quoted("task1;echo \\\"fo; o\\\""), quoted("task1;echo \\\"fo; o\\\""), "task then task with escaped quoted arg containing semicolon (quoted)"),

    // task1;"task2";task3 (invalid) -> unchanged (valid)
    Array("task1;\"task2\";task3",           "task1;\"task2\";task3",             "quoted task between plain tasks"),
    // task1;\"task2\";task3 (invalid) -> unchanged (invalid)
    Array("task1;\\\"task2\\\";task3",       "task1;\\\"task2\\\";task3",         "escaped quoted task between plain tasks"),
    // "task1;\"task2\";task3" (invalid) -> unchanged (invalid)
    Array(quoted("task1;\\\"task2\\\";task3"), quoted("task1;\\\"task2\\\";task3"), "escaped quoted task between plain tasks (quoted)"),

    Array(";task1 ;task2",            quoted(";task1 ;task2"),     "leading semicolon, two commands"),
    Array(";echo \"a b\"",            quoted(";echo \\\"a b\\\""), "leading semicolon with quoted arg"),
    Array(";task1",                   ";task1",                    "leading semicolon, single command (no spaces, kept as-is)"),

    Array("",                         "",                          "empty string"),
    Array("  ",                       "  ",                        "white spaces"),
    Array("  task1  ",                quoted("  task1  "),         "task with whitespaces")
  )
