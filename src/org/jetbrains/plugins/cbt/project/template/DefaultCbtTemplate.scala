package org.jetbrains.plugins.cbt.project.template

import java.io.File

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.writeToFile
import org.jetbrains.plugins.cbt._

import scala.util.Try

class DefaultCbtTemplate extends CbtTemplate {
  override def generate(project: Project, root: File, settings: CbtTemplateSettings): Try[String] =
    Try {
      (root / "src").mkdirs()
      writeToFile(root / "build" / "build.scala", template(settings))
      "Template Created"
    }

  private def template(settings: CbtTemplateSettings) =
    s"""import cbt._
       |
       |class Build(val context: Context) extends BaseBuild {
       |  override def defaultScalaVersion = "${settings.scalaVersion}"
       |}
    """.stripMargin

  override def name: String = "Default Template"
}
