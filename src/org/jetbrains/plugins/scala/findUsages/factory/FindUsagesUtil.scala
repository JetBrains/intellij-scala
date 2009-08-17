package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */

object FindUsagesUtil {
  def createFindUsagesOptions(project: Project): ScalaFindUsagesOptions = {
    new ScalaFindUsagesOptions(project, null)
  }
}