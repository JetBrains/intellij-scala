package intellijscalastyle
package codeInspection

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.scalastyle._

import scala.collection.mutable

object ScalastyleCodeInspection {
  private type TimestampedScalastyleConfiguration = (Long, ScalastyleConfiguration)
  private val cache = new mutable.HashMap[VirtualFile, TimestampedScalastyleConfiguration]()

  private object locations {
    def `scalastyle-config.xml`(root: VirtualFile): Option[VirtualFile] = Option(root.findChild("scalastyle-config.xml"))
    def `project/`(f: VirtualFile => Option[VirtualFile])(root: VirtualFile): Option[VirtualFile] = Option(root.findChild("project")).flatMap(f)

    def typicalLocation(project: Project): Option[VirtualFile] = {
      val root = project.getBaseDir
      `project/`(`scalastyle-config.xml`)(root).fold(`scalastyle-config.xml`(root))(Some(_))
    }
  }

  private def configuration(project: Project): Option[ScalastyleConfiguration] = {
    def latest(scalastyleXml: VirtualFile): TimestampedScalastyleConfiguration = {
      val configuration = ScalastyleConfiguration.readFromString(new String(scalastyleXml.contentsToByteArray()))
      (scalastyleXml.getModificationStamp, configuration)
    }

    locations.typicalLocation(project).map { scalastyleXml =>
      val (ts, configuration) = cache.getOrElse(scalastyleXml, latest(scalastyleXml))
      if (ts != scalastyleXml.getModificationStamp) {
        val Some((_, latestConfiguration)) = cache.put(scalastyleXml, latest(scalastyleXml))
        latestConfiguration
      } else {
        configuration
      }
    }
  }

}

class ScalastyleCodeInspection extends LocalInspectionTool {

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    def withConfiguration(f: ScalastyleConfiguration => Iterable[ProblemDescriptor]): Array[ProblemDescriptor] = {
      ScalastyleCodeInspection.configuration(file.getProject).map(c => f(c).toArray).getOrElse(Array.empty)
    }

    if (!file.isInstanceOf[ScalaFile]) Array.empty
    else withConfiguration { configuration =>
      val scalaFile = file.asInstanceOf[ScalaFile]
      val result = new ScalastyleChecker().checkFiles(configuration, Seq(new SourceSpec(file.getName, file.getText)))
      val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)

      def findPsiElement(line: Int, column: Option[Int]): Option[PsiElement] = {
        (for {
          element    <- scalaFile.depthFirst
          if element != scalaFile
          psiLine    =  document.getLineNumber(element.getTextOffset) + 1
          if line    == psiLine
        } yield element).toList.headOption
      }

      def levelToProblemType(level: Level): ProblemHighlightType = level.name match {
        case Level.Info => ProblemHighlightType.INFORMATION
        case Level.Error => ProblemHighlightType.GENERIC_ERROR
        case Level.Warning => ProblemHighlightType.WEAK_WARNING
        case _ => ProblemHighlightType.GENERIC_ERROR
      }

      result.flatMap {
        case StyleError(_, _, key, level, args, Some(line), column, customMessage) =>
          findPsiElement(line, column).map { e =>
            val message = Messages.format(key, args, customMessage)
            manager.createProblemDescriptor(e, message, Array.empty[LocalQuickFix], levelToProblemType(level), true, false)
          }

        case _ => None
      }

    }

  }
}
