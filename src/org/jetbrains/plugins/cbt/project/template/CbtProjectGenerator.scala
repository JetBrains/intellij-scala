package org.jetbrains.plugins.cbt.project.template

import java.io.File
import com.intellij.openapi.util.io.FileUtil._
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.RichFile

object CbtProjectGenerator {
  def apply(root: File, scalaVersion: Version): Unit = {
    (root / "src").mkdirs()
    writeToFile(root / "build" / "build.scala", BuildFileTemplate(scalaVersion))
  }
}

object BuildFileTemplate {
  def apply(scalaVersion: Version): String =
    """
      |import cbt._
      |
      |class Build(val  context: Context) extends BaseBuild {
      |}
    """.stripMargin
}

