package intellijscalastyle
package codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalastyleCodeInspection extends LocalInspectionTool {

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    if (!file.isInstanceOf[ScalaFile]) return Array.empty
    println("Checking " + file.getText)
    val scalaFile = file.asInstanceOf[ScalaFile]

    val s = scalaFile.getFirstChild
    val e = scalaFile.getLastChild
    val errors = scalaFile.getChildren
    Array(
      //manager.createProblemDescriptor(file, "Scala codestyle problems", false, Array.empty[LocalQuickFix], ProblemHighlightType.ERROR),
      manager.createProblemDescriptor(s, "Scala codestyle problem", Array.empty[LocalQuickFix], ProblemHighlightType.ERROR, false, false)
      // manager.createProblemDescriptor(s, e, "Generic Scala codestyle problem", ProblemHighlightType.ERROR, false)
    )

  }
}
