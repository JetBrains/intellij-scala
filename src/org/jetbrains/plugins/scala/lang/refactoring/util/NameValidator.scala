package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */

trait NameValidator {
  def validateName(name: String, increaseNumber: Boolean): String

  def getProject(): Project
}
