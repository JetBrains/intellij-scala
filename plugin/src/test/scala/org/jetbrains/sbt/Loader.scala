package org.jetbrains.sbt

import java.io.File
import scala.io.Source

/**
 * @author Pavel Fatin
 */
object Loader {
  def load(project: File): String = {
    val tempFile = File.createTempFile("sbt-structure", "xml")
    tempFile.deleteOnExit()

    val sbt = if (System.getProperty("os.name").toLowerCase.contains("windows")) "sbt.bat" else "sbt"

    val command = sbt + """ "; set artifactPath := new File(\"""" + canonicalPath(tempFile) +
      """\") ; apply -cp """ + canonicalPath("""target\scala-2.9.2\sbt-0.12\classes\""") +
      """ org.jetbrains.sbt.Plugin""""

    Runtime.getRuntime.exec(command, null, project).waitFor()

    assert(tempFile.exists, "File must be created: " + tempFile.getPath)

    Source.fromFile(tempFile).getLines().mkString("\n")
  }

  private def canonicalPath(path: String): String = canonicalPath(new File(path))

  private def canonicalPath(file: File): String = file.getAbsolutePath.replace('\\', '/')
}
