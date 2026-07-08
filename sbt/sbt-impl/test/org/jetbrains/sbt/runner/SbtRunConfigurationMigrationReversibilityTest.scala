package org.jetbrains.sbt.runner

import com.intellij.openapi.command.impl.DummyProject
import org.jdom.Element
import org.junit.Assert.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.util.stream.Stream

/**
 * Tests reversibility of `commands`/`tasks` fields from the [[SbtRunConfiguration]]
 * when switching between an older plugin version (without `commands`) and the current one (with `commands` and `tasks`).
 * The reversibility was tested using the real serialization process to simulate real-world examples.
 *
 * The test suite covers:
 *  - `tasks` reversibility (`tasks → commands → tasks`)
 *  - `commands` reversibility (`commands → tasks → commands`)
 */
class SbtRunConfigurationMigrationReversibilityTest:

  /**
   * Test the reversability of the `commands` field (`commands → tasks → commands`).
   *
   * Scenario:
   * 1. The current IDEA creates a Run Configuration; both `commands` and `tasks` are serialized.
   * 2. The config is opened in an older IDEA. When the user edits the Run Configuration, the older IDEA drops `commands` from the XML.
   * 3. The config is opened again in the current IDEA. Because the `commands` field is not present, it is re-created from the `tasks` field.
   *
   * Non-reversible cases arise because the migration from `tasks` back to `commands` strips outer quotes (e.g. `"task1"` → `task1`).
   */
  @ParameterizedTest(name = "{2}")
  @MethodSource(Array("commandsReversibilityOldIdeDropsCommandsData"))
  def testCommandsReversibilityOldIdeDropsCommands(originalCommands: String, expectedAfterRoundTrip: String, testName: String): Unit = {
    val config = createConfig()
    config.commands = originalCommands

    // Step 1: the current IDEA saves the config; it does commands -> tasks migration.
    val element = new Element("configuration")
    config.writeExternal(element)

    // Step 2: simulate opening the Run Configuration in an older IDEA and serializing it (serialization is triggered when any change is
    // made in the Run Configuration UI). When this happens, the `commands` field is removed because it doesn't exist in the older IDEA.
    removeOptionElement(element, "commands")

    // Step 3: the current IDEA opens the config — `commands` is empty, so it's migrated from `tasks`
    val copy = createConfig()
    copy.readExternal(element)

    assertEquals(testName, expectedAfterRoundTrip, copy.commands)
  }

  /**
   * Test the reversability of the `commands` field.
   * It is similar to [[testCommandsReversibilityOldIdeDropsCommands]], but in this case, when we simulate opening the configuration in an older IDEA version,
   * no serialization happens, so the `commands` field is not removed from the XML file.
   * Serialization of the Run Configuration does not happen when no changes are made, so this is not only a synthetic example but also a possible real-world case.
   *
   * Unlike [[testCommandsReversibilityOldIdeDropsCommands]], the `commands` field stays in the XML, so no re-migration from `tasks` occurs.
   * All cases are reversible.
   */
  @ParameterizedTest(name = "{1}")
  @MethodSource(Array("commandsReversibilityData"))
  def testCommandsReversibility(commands: String, testName: String): Unit = {
    val config = createConfig()
    config.commands = commands

    // Step 1: the current IDEA saves the config; it does commands -> tasks migration.
    val element = new Element("configuration")
    config.writeExternal(element)

    // Step 2: an older IDEA opens the config without modifying it — the XML stays unchanged
    // (no code here: this step is a no-op, just for clarity)

    // Step 3: the current IDEA opens the config again — `commands` is still present, migration does not happen
    val copy = createConfig()
    copy.readExternal(element)

    assertEquals(testName, commands, copy.commands)
  }

  /**
   * Test the reversibility of the `tasks` field.
   *
   * Scenario:
   * 1. An older IDEA creats a configuration with only `tasks` (no `commands`).
   * 2. The current IDEA loads it, migrates `tasks` to `commands`.
   * Important: during serialization, if migrating the current `tasks` field to commands produces the same value as the current `commands` field,
   * then the `tasks` field is not overwritten. Please read the explanation in [[org.jetbrains.sbt.runner.SbtRunConfiguration#writeExternal]].
   * 3. The configuration is opened again in an older IDEA.
   *
   * Because the `tasks` field was not overwritten in step 2 in the current IDEA, it remains the same as before,
   * so all cases in this test are fully reversible.
   */
  @ParameterizedTest(name = "{1}")
  @MethodSource(Array("tasksReversibilityData"))
  def testTasksReversibility(tasks: String, testName: String): Unit = {
    // Step 1: simulate creating config in an older IDEA — only the `tasks` field is present
    val oldIdeElement = new Element("configuration")
    addOptionElement(oldIdeElement, "tasks", tasks)

    // Step 2: the current IDEA loads the old config — migrates `tasks` to `commands`
    val config1 = createConfig()
    val newIdeElement = new Element("configuration")
    config1.readExternal(oldIdeElement)
    config1.writeExternal(newIdeElement)

    // Step 3: an older IDEA opens the config again
    val config2 = createConfig()
    config2.readExternal(newIdeElement)

    assertEquals(testName, tasks, config2.tasks)
  }

  private def removeOptionElement(root: Element, optionName: String): Unit = {
    import scala.jdk.CollectionConverters.*
    val children = root.getChildren("option").asScala.toList
    children.find(_.getAttributeValue("name") == optionName).foreach(root.removeContent)
  }

  private def addOptionElement(root: Element, optionName: String, value: String): Unit = {
    val option = new Element("option")
    option.setAttribute("name", optionName)
    option.setAttribute("value", value)
    root.addContent(option)
  }

  private def createConfig(name: String = "test"): SbtRunConfiguration = {
    val configFactory = new SbtRunConfigurationFactory(new SbtConfigurationType())
    new SbtRunConfiguration(DummyProject.getInstance(), configFactory, name)
  }

object SbtRunConfigurationMigrationReversibilityTest:

  /** Wraps `s` in double quotes, so the test data are easier to read. */
  private def quoted(s: String): String = "\"" + s + "\""

  // Original commands | Expected after round-trip (commands -> tasks -> commands) | Test name
  def commandsReversibilityOldIdeDropsCommandsData(): Stream[Array[String]] = Stream.of(
    // fully reversible cases (originalCommands == expectedAfterRoundTrip)
    Array("task1",                    "task1",                 "single task"),
    Array("task1 task1",              "task1 task1",           "two space-separated tasks"),
    Array("task1;task1",              "task1;task1",           "two tasks with semicolon (no space)"),
    Array("task1; task1",             "task1; task1",          "two tasks with semicolon and space"),
    Array("add 1 2",                  "add 1 2",               "task with two arguments"),
    Array("task1; add 1 2",           "task1; add 1 2",        "task semicolon task with arguments"),
    Array("",                         "",                      "empty string"),
    Array("  ",                       "  ",                    "white spaces"),
    Array("  task1  ",                "  task1  ",             "task with whitespaces"),
    // non-reversible (originalCommands != expectedAfterRoundTrip)
    Array(quoted("task1"),            "task1",                 "quoted single task"),
    Array(quoted("task1 task1"),      "task1 task1",           "quoted two space-separated tasks"),
    Array(quoted("task1;task1"),      "task1;task1",           "quoted two tasks with semicolon (no space)"),
    Array(quoted("task1; task1"),     "task1; task1",          "quoted two tasks with semicolon and space"),
    Array(quoted("add 1 2"),          "add 1 2",               "quoted task with two arguments"),
    Array(quoted("task1; add 1 2"),   "task1; add 1 2",        "quoted task semicolon task with arguments")
  )

  // Original commands | Test name
  def commandsReversibilityData(): Stream[Array[String]] = Stream.of(
    Array("task1",                    "single task"),
    Array("task1 task1",              "two space-separated tasks"),
    Array("task1;task1",              "two tasks with semicolon (no space)"),
    Array("task1; task1",             "two tasks with semicolon and space"),
    Array("add 1 2",                  "task with two arguments"),
    Array("task1; add 1 2",           "task semicolon task with arguments"),
    Array("",                         "empty string"),
    Array("  ",                       "white spaces"),
    Array("  task1  ",                "task with whitespaces"),
    Array(quoted("task1"),            "quoted single task"),
    Array(quoted("task1 task1"),      "quoted two space-separated tasks"),
    Array(quoted("task1;task1"),      "quoted two tasks with semicolon (no space)"),
    Array(quoted("task1; task1"),     "quoted two tasks with semicolon and space"),
    Array(quoted("add 1 2"),          "quoted task with two arguments"),
    Array(quoted("task1; add 1 2"),   "quoted task semicolon task with arguments")
  )

  // Original & expected after round-trip commands | Test name
  def tasksReversibilityData(): Stream[Array[String]] = Stream.of(
    Array("task1",                    "single task"),
    Array("task1 task1",              "two space-separated tasks"),
    Array("task1;task1",              "two tasks with semicolon (no space)"),
    Array("task1; task1",             "two tasks with semicolon and space"),
    Array("add 1 2",                  "task with two arguments"),
    Array("task1; add 1 2",           "task semicolon task with arguments"),
    Array("",                         "empty string"),
    Array("  ",                       "white spaces"),
    Array("  task1  ",                "task with whitespaces"),
    Array(quoted("task1"),            "quoted single task"),
    Array(quoted("task1 task1"),      "quoted two space-separated tasks"),
    Array(quoted("task1;task1"),      "quoted two tasks with semicolon (no space)"),
    Array(quoted("task1; task1"),     "quoted two tasks with semicolon and space"),
    Array(quoted("add 1 2"),          "quoted task with two arguments"),
    Array(quoted("task1; add 1 2"),   "quoted task semicolon task with arguments")
  )
