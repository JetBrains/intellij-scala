package org.jetbrains.plugins.scala.project

import com.intellij.openapi.application.Experiments
import com.intellij.openapi.util.io
import com.intellij.openapi.util.text.Strings
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions.IterableOnceExt

import java.awt.Container
import java.io._
import javax.swing.JLabel
import scala.util.Using

package object template {

  import io.FileUtil._

  // TODO: SCL-23312
  def usingTempFile[T](prefix: String, suffix: String = null)(block: File => T): T = {
    val file = createTempFile(prefix, suffix, true)
    try {
      block(file)
    } finally {
      file.delete()
    }
  }

  // TODO: SCL-23312
  def usingTempDirectory[T](prefix: String)(block: File => T): T = {
    val directory = createTempDirectory(prefix, null, true)
    try {
      block(directory)
    } finally {
      delete(directory)
    }
  }

  // TODO: SCL-23312
  def writeLinesTo(file: File)
                  (lines: String*): Unit = {
    Using.resource(new PrintWriter(new FileWriter(file))) { writer =>
      lines.foreach(writer.println)
      writer.flush()
    }
  }

  /**
   * Examples: {{{
   *    "Project SDK:"       -> "JDK:"
   *    "Project name:"     -> "Name:"
   *    "Project location:" -> "Location:"
   * }}}
   *
   * TODO: Remove the label patching when the External System will use the concise and proper labels natively
   */
  def patchProjectLabels(parent: Container): Unit = {
    parent.getComponents.toSeq.foreachDefined {
      case label: JLabel if label.getText == "Project SDK:" =>
        label.setText("JDK:")
        label.setDisplayedMnemonic('J')

      case label: JLabel if label.getText.startsWith("Project ") && label.getText.length > 8 =>
        val newText = Strings.capitalize(label.getText.substring(8))
        label.setText(newText)
    }
  }

  def isNewWizardEnabled: Boolean =
    Experiments.getInstance.isFeatureEnabled("new.project.wizard")

  @TestOnly
  private[jetbrains] def setNewWizardEnabled(enabled: Boolean): Unit =
    Experiments.getInstance.setFeatureEnabled("new.project.wizard", enabled)
}
