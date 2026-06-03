package org.jetbrains.plugins.scala.worksheet.inspections

import com.intellij.codeInspection._
import com.intellij.openapi.project.DumbAware
import com.intellij.psi._
import org.jetbrains.plugins.scala.worksheet.{WorksheetBundle, WorksheetFile}

final class WorksheetPackageDeclarationInspection extends LocalInspectionTool with DumbAware {
  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] =
    file match {
      case file: WorksheetFile =>
        file.packagingRanges.map { range =>
          manager.createProblemDescriptor(
            file, range, WorksheetBundle.message("package.declarations.are.not.allowed.in.worksheets"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, null
          )
        }.toArray
      case _ => null
    }
}
