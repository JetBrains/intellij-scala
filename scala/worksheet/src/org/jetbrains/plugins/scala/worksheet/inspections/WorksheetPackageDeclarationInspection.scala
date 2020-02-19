package org.jetbrains.plugins.scala.worksheet.inspections

import com.intellij.codeInspection._
import com.intellij.psi._
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.WorksheetParserDefinition.WorksheetScalaFile

class WorksheetPackageDeclarationInspection extends LocalInspectionTool {

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] =
    file match {
      case file: WorksheetScalaFile =>
        file.packagingRanges.map { range =>
          manager.createProblemDescriptor(
            file, range, WorksheetBundle.message("package.declarations.are.not.allowed.in.worksheets"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, null
          )
        }.toArray
      case _  => null
    }
}