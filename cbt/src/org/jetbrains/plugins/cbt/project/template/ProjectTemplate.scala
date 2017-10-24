package org.jetbrains.plugins.cbt.project.template

import java.io.File

import com.intellij.openapi.project.Project

import scala.util.Try

trait CbtTemplate {
  def generate(project: Project, root: File, settings: CbtTemplateSettings): Try[String]

  def name: String

  override def toString: String = name
}

case class CbtTemplateSettings(scalaVersion: String)