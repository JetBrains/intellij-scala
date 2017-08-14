package org.jetbrains.plugins.cbt.project.template

import java.io.File

trait ProjectTemplate {
  def generate(root: File, settings: TemplateSettings): Unit
}

case class TemplateSettings(scalaVersion: String)