package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.compiler.ProcessWatcher.partitionPickedUpJvmOptionsLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.util.stream.Stream
import scala.annotation.static

class ProcessWatcherTest:

  @ParameterizedTest(name = "{0}")
  @MethodSource(Array("pickedUpJvmOptionsLines"))
  def pickedUpJvmOptionsLineIsNotAnError(line: String): Unit =
    assertEquals((List(line), Nil), partitionPickedUpJvmOptionsLines(line + System.lineSeparator()))

  @ParameterizedTest(name = "{0}")
  @MethodSource(Array("errorLines"))
  def otherLineIsAnError(line: String): Unit =
    assertEquals((Nil, List(line)), partitionPickedUpJvmOptionsLines(line + System.lineSeparator()))

  @Test
  def chunkWithSeveralLinesIsSplitByLine(): Unit =
    val text =
      """Picked up JAVA_TOOL_OPTIONS: -Dhttps.proxyHost=proxy.example
        |Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
        |	at com.facebook.nailgun.NGServer.run(NGServer.java:519)
        |""".stripMargin
    val expected = (
      List("Picked up JAVA_TOOL_OPTIONS: -Dhttps.proxyHost=proxy.example"),
      List(
        "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space",
        "\tat com.facebook.nailgun.NGServer.run(NGServer.java:519)",
      ),
    )
    assertEquals(expected, partitionPickedUpJvmOptionsLines(text))

  @Test
  def windowsLineSeparatorsAreSupported(): Unit =
    val text = "Picked up JAVA_TOOL_OPTIONS: -Dfoo=bar\r\nError: Could not find or load main class Foo\r\n"
    val expected = (List("Picked up JAVA_TOOL_OPTIONS: -Dfoo=bar"), List("Error: Could not find or load main class Foo"))
    assertEquals(expected, partitionPickedUpJvmOptionsLines(text))

  @Test
  def emptyChunkHasNoLines(): Unit =
    assertEquals((Nil, Nil), partitionPickedUpJvmOptionsLines(""))

private object ProcessWatcherTest:
  @static
  def pickedUpJvmOptionsLines(): Stream[String] = Stream.of(
    "Picked up JAVA_TOOL_OPTIONS: -Dhttps.proxyHost=proxy.example -Dhttps.proxyPort=3128",
    "Picked up _JAVA_OPTIONS: -Dfoo=bar",
    "NOTE: Picked up JDK_JAVA_OPTIONS: -Dfoo=bar",
    // The variable is set to an empty string
    "Picked up JAVA_TOOL_OPTIONS: ",
  )

  @static
  def errorLines(): Stream[String] = Stream.of(
    "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space",
    "Error occurred during initialization of VM",
    "WARNING: A restricted method in java.lang.System has been called",
    "\tat com.facebook.nailgun.NGServer.run(NGServer.java:519)",
    // Only the three JVM options variables are known to be reported like this
    "Picked up FOO: bar",
    // Must be at the start of the line
    "Something mentioned Picked up JAVA_TOOL_OPTIONS: in the middle",
  )
