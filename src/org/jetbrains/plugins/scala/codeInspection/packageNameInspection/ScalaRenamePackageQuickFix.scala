package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection


import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.ScalaUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class ScalaRenamePackageQuickFix(file: ScalaFile, name: String) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    ScalaUtils.runWriteAction(new Runnable {
      def run: Unit = {
        file.setPackageName(name)
      }
    }, project, "Rename Package QuickFix")
  }

  def getName: String = "Rename Package to " + name

  def getFamilyName: String = "Rename Package"
}