package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection


import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import com.intellij.openapi.project.Project
import java.lang.String
import lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class ScalaRenamePackageQuickFix(file: ScalaFile, name: String) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    file.setPackageName(name)
  }

  def getName: String = "Rename Package to " + name

  def getFamilyName: String = "Rename Package"
}