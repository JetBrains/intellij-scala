package org.jetbrains.plugins.cbt.project.template

import java.io.File

import org.jetbrains.plugins.cbt.process.CbtProcess

import scala.util.Try

class Giter8CbtTemplate(template: String) extends CbtTemplate {
  override def generate(root: File, settings: CbtTemplateSettings): Try[String] =
    CbtProcess.generateGiter8Template(template, root)

  override def name = s"Giter8 template '$name'"
}
