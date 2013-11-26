package org.jetbrains.sbt
package project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * @author Pavel Fatin
 */
abstract class SbtOptionsProvider {
  def vmOptionsFor(project: Project, path: String): Seq[String]
}

object SbtOptionsProvider { //todo: why we need it?
  val ExtensioPoint: ExtensionPointName[SbtOptionsProvider] =
    ExtensionPointName.create("org.intellij.sbt.sbtOptionsProvider")

  def vmOptionsFor(project: Project, path: String): Seq[String] =
    ExtensioPoint.getExtensions.flatMap(_.vmOptionsFor(project, path))
}
