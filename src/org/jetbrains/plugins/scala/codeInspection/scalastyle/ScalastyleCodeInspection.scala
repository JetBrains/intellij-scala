package org.jetbrains.plugins.scala.codeInspection.scalastyle

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
    val possibleConfigFileNames = Seq("scalastyle_config.xml", "scalastyle-config.xml")
    val possibleLocations = Seq(".idea", "project")

    def findConfigFile(dir: VirtualFile) = possibleConfigFileNames.flatMap(name => Option(dir.findChild(name))).headOption

    def findIn(project: Project): Option[VirtualFile] = {
      val root = project.getBaseDir
      if (root == null) return None

      val dirs = possibleLocations.flatMap(name => Option(root.findChild(name))) :+ root
      dirs.flatMap(findConfigFile).headOption
    }
  }

  private def configuration(project: Project): Option[ScalastyleConfiguration] = {

    def latest(scalastyleXml: VirtualFile): Option[ScalastyleConfiguration] = {
      def read(): TimestampedScalastyleConfiguration = {
        val configuration = ScalastyleConfiguration.readFromString(new String(scalastyleXml.contentsToByteArray()))
        (scalastyleXml.getModificationStamp, configuration)
      }

      val currentStamp = scalastyleXml.getModificationStamp
      cache.get(scalastyleXml) match {
        case Some((`currentStamp`, config)) => Option(config)
        case _ =>
          val fromFile = cache.put(scalastyleXml, read())
          fromFile.map(_._2)
      }
    }

    locations.findIn(project).flatMap(latest)
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
      val result = new ScalastyleChecker(None).checkFiles(configuration, Seq(new SourceSpec(file.getName, file.getText)))
      val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)

      def atPosition(e: PsiElement, line: Int, column: Option[Int]): Boolean = {
        val correctLine = if (line > 0) line - 1 else 0
        val sameLine = correctLine == document.getLineNumber(e.getTextOffset)
        column match {
          case Some(col) =>
            val offset = document.getLineStartOffset(correctLine) + col
            sameLine && e.getTextRange.contains(offset)
          case None => sameLine
        }
      }

      def findPsiElement(line: Int, column: Option[Int]): Option[PsiElement] = {
        (for {
          element    <- scalaFile.depthFirst
          if element != scalaFile && atPosition(element, line, column)
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
          findPsiElement(line, column).filter(e => e.isPhysical && !e.getTextRange.isEmpty).map { e =>
            val message = Messages.format(key, args, customMessage)
            manager.createProblemDescriptor(e, message, Array.empty[LocalQuickFix], levelToProblemType(level), true, false)
          }

        case _ => None
      }

    }

  }
}
