package org.jetbrains.plugins.scala
package testingSupport

import com.intellij.execution.process.{ProcessAdapter, ProcessEvent}
import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil

import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala

final class TestRunnerOutputListener(printProcessOutputToConsole: Boolean)
  extends ProcessAdapter
    with TestOutputMarkers {

  private val _output: mutable.Buffer[(String, Key[_])] =
    ContainerUtil.createConcurrentList[(String, Key[_])].asScala

  private val _outputTextFromTests = new StringBuilder

  def outputText: String = _output.map { case (text, typ) => s"[$typ] $text" }.mkString
  def outputTextFromTests: String = _outputTextFromTests.mkString

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
    // remove 1 `#` in order the tests are not displayed in test tree view when debugging process output in tests
    val text = event.getText.replace("##teamcity", "#teamcity")

    if (printProcessOutputToConsole) {
      print(text)
    }

    _output += ((text, outputType))

    val prefixIndex = text.indexOf(TestOutputPrefix)
    val suffixIndex = text.indexOf(TestOutputSuffix)
    if (prefixIndex != -1 && suffixIndex != -1) {
      val str = text.substring(prefixIndex + TestOutputPrefix.length, suffixIndex)
      _outputTextFromTests.append(str)
    }
  }
}
