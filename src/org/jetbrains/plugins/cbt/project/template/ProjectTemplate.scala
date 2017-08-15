package org.jetbrains.plugins.cbt.project.template

import java.io.File

import scala.util.Try

trait CbtTemplate {
  def generate(root: File, settings: CbtTemplateSettings): Try[String]

  def name: String
}

case class CbtTemplateSettings(scalaVersion: String)