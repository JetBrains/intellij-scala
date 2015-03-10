package org.jetbrains.plugins.scala.project.converter

import scala.xml.Elem

/**
 * @author Pavel Fatin
 */
case class ScalaCompilerSettingsProfile(name: String, modules: Seq[String], settings: ScalaCompilerSettings) {
  def toXml: Elem = {
    <profile name={name} modules={modules.mkString(", ")}>
      {settings.toXml}
    </profile>
  }
}