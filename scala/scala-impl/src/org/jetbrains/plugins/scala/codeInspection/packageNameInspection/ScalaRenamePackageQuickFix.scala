package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.startCommand
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */
class ScalaRenamePackageQuickFix(myFile: ScalaFile, name: String)
      extends AbstractFixOnPsiElement(if (name == null || name.isEmpty) "Remove package statement" else s"Rename Package to $name", myFile) {

  override protected def doApplyFix(file: ScalaFile)
                                   (implicit project: Project): Unit = {
    startCommand(project, () => {
      file.setPackageName(name)
    }, "Rename Package QuickFix")
  }

  override def getFamilyName: String = "Rename Package"
}