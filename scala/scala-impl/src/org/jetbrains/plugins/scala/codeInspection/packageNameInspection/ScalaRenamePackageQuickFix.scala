package org.jetbrains.plugins.scala.codeInspection.packageNameInspection

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.startCommand
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaRenamePackageQuickFix(myFile: ScalaFile, name: String)
  extends AbstractFixOnPsiElement(if (name == null || name.isEmpty) ScalaInspectionBundle.message("remove.package.statement") else ScalaInspectionBundle.message("rename.package.to", name), myFile) {

  override protected def doApplyFix(file: ScalaFile)
                                   (implicit project: Project): Unit =
    if (IntentionPreviewUtils.isIntentionPreviewActive) file.setPackageName(name)
    else startCommand(ScalaInspectionBundle.message("rename.package.quickfix.command.name")) {
      file.setPackageName(name)
    }

  override def getFamilyName: String = ScalaInspectionBundle.message("family.name.rename.package")
}
