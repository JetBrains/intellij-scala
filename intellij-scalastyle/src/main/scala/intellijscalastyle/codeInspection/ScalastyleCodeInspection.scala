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
    val scalaFile = file.asInstanceOf[ScalaFile]
    val result = new ScalastyleChecker().checkFiles(ScalastyleCodeInspection.configuration, Seq(new SourceSpec(file.getName, file.getText)))
    val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)

    def findPsiElements(line: Int, column: Option[Int]): Option[(PsiElement, PsiElement)]= {
      (for {
        element    <- scalaFile.depthFirst
        if element != scalaFile
        psiLine    =  document.getLineNumber(element.getTextOffset()) + 1
        if line    == psiLine
      } yield (element, element)).toList.headOption
    }

    result.flatMap {
      case StyleError(_, _, key, level, args, Some(line), column, customMessage) =>
        findPsiElements(line, column) match {
          case Some((s, e)) =>
            val message = Messages.format(key, args, customMessage)
            Some(manager.createProblemDescriptor(s, message, Array.empty[LocalQuickFix], ProblemHighlightType.GENERIC_ERROR, true, false))
          case None => None
        }

      case _ => None
    }.toArray
  }
}
