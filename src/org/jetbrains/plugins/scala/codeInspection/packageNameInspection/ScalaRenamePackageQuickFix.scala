package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection


import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.ScalaUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class ScalaRenamePackageQuickFix(myFile: ScalaFile, name: String)
      extends AbstractFixOnPsiElement(if (name == null || name.isEmpty) "Remove package statement" else s"Rename Package to $name", myFile) {
  def doApplyFix(project: Project): Unit = {
    val file = getElement
    if (!file.isValid) return
    ScalaUtils.runWriteAction(new Runnable {
      def run(): Unit = {
        file.setPackageName(name)
      }
    }, project, "Rename Package QuickFix")
  }

  override def getFamilyName: String = "Rename Package"
}