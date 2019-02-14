package org.jetbrains.plugins.scala
package conversion

import com.intellij.openapi.project.Project

package object copy {

  def shownDialog(reason: String)
                 (implicit project: Project,
                  projectSettings: settings.ScalaProjectSettings): Boolean =
    projectSettings.isDontShowConversionDialog ||
      new ScalaPasteFromJavaDialog(
        project,
        projectSettings,
        ScalaConversionBundle.message("scala.copy.from", reason)
      ).showAndGet()

}
