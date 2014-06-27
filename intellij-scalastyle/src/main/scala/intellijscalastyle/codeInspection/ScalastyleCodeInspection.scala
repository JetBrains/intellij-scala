package intellijscalastyle
package codeInspection

import com.intellij.codeInspection._
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiElementVisitor, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.scalastyle._

object ScalastyleCodeInspection {
  val configuration = ScalastyleConfiguration.getDefaultConfiguration()

}

class ScalastyleCodeInspection extends LocalInspectionTool {


  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    if (!file.isInstanceOf[ScalaFile]) return Array.empty
    println("Checking " + file.getText)
    val scalaFile = file.asInstanceOf[ScalaFile]
    val result = new ScalastyleChecker().checkFiles(ScalastyleCodeInspection.configuration, Seq(new SourceSpec(file.getName, file.getText)))
    val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)

    def findPsiElements(line: Int, column: Option[Int]): Option[(PsiElement, PsiElement)]= {
      (for {
        element <- scalaFile.depthFirst
        psiLine =  document.getLineNumber(element.getTextOffset()) + 1
        if line == psiLine
        // document.getLineStartOffset(element.getTextOffset)
      } yield (element, element)).toList.headOption
    }

    result.flatMap {
      case StyleError(_, _, key, level, args, Some(line), column, msg) =>
        val Some((s, e)) = findPsiElements(line, column)
        Some(manager.createProblemDescriptor(s, "Scala codestyle problem", Array.empty[LocalQuickFix], ProblemHighlightType.ERROR, false, false))
      case _ => None
    }.toArray

//    val s = scalaFile.getFirstChild
//    val e = scalaFile.getLastChild
//    val errors = scalaFile.getChildren
//    Array(
//      manager.createProblemDescriptor(s, "Scala codestyle problem", Array.empty[LocalQuickFix], ProblemHighlightType.ERROR, false, false)
//    )

  }
}
