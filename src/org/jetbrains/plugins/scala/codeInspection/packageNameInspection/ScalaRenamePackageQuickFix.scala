package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection


import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import com.intellij.openapi.project.Project
import java.lang.String
import lang.psi.api.ScalaFile
import util.ScalaUtils

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