package org.jetbrains.plugins.cbt.project.template

import java.io.File

import com.intellij.openapi.util.io.FileUtil.writeToFile
import org.jetbrains.plugins.cbt._

class LocalProjectTemplate extends ProjectTemplate {
  override def generate(root: File, settings: TemplateSettings): Unit = {
    (root / "src").mkdirs()
    writeToFile(root / "build" / "build.scala", template(settings))
  }

  private def template(settings: TemplateSettings) =
    s"""import cbt._
       |
       |class Build(val context: Context) extends BaseBuild {
       |  override def defaultScalaVersion = "${settings.scalaVersion}"
       |}
    """.stripMargin
}
