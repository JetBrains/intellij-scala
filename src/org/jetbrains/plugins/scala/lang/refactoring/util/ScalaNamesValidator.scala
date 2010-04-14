package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.04.2010
 */

class ScalaNamesValidator extends NamesValidator {
  def isIdentifier(name: String, project: Project): Boolean = {
    ScalaNamesUtil.isIdentifier(name)
  }

  def isKeyword(name: String, project: Project): Boolean = {
    ScalaNamesUtil.isKeyword(name)
  }
}