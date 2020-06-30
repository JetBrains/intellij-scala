package org.jetbrains.plugins.scala.codeInspection.scalastyle

import com.intellij.codeInspection._
import com.intellij.openapi.project.{Project, ProjectUtil}
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.scalastyle.ScalastyleCodeInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.scalastyle._

import scala.collection.mutable


class ScalastyleCodeInspection extends LocalInspectionTool {
  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val converter = MessageToProblemDescriptorConverter(file, manager)
    configurationFor(file)
      .map(checkWithScalastyle(file, _))
      .map(_.flatMap(converter.toProblemDescriptor).toArray)
      .getOrElse(Array.empty)
  }
}


object ScalastyleCodeInspection {
  private type TimestampedScalastyleConfiguration = (Long, ScalastyleConfiguration)
  private val cache = new mutable.HashMap[VirtualFile, TimestampedScalastyleConfiguration]()

  def configurationFor(file: ScalaFile): Option[ScalastyleConfiguration] = {
    val project = file.getProject
    val isTestSource = TestSourcesFilter.isTestSources(file.getVirtualFile, project)

    import Locations.{possibleSrcConfigFileNames, possibleTestConfigFileNames}
    val possibleFileNames =
      if (isTestSource) possibleTestConfigFileNames ++ possibleSrcConfigFileNames
      else possibleSrcConfigFileNames

    Locations.findIn(project, possibleFileNames).map(latest)
  }

  def configurationFor(file: PsiFile): Option[ScalastyleConfiguration] = {
    file.asOptionOf[ScalaFile].flatMap(configurationFor)
  }

  private object Locations {
    val possibleSrcConfigFileNames: Seq[String]   = Seq("scalastyle_config.xml", "scalastyle-config.xml")
    val possibleTestConfigFileNames: Seq[String]  = Seq("scalastyle_test_config.xml", "scalastyle-test-config.xml")
    val possibleLocations: Seq[String]            = Seq(".idea", "project")

    private def findConfigFile(dir: VirtualFile, possibleConfigFileNames: Seq[String]): Option[VirtualFile] =
      possibleConfigFileNames.flatMap(name => Option(dir.findChild(name))).headOption

    def findIn(project: Project, possibleConfigFileNames: Seq[String]): Option[VirtualFile] = {
      val root = ProjectUtil.guessProjectDir(project)
      if (root == null) return None

      val dirs = possibleLocations.flatMap(name => Option(root.findChild(name))) :+ root
      dirs.flatMap(findConfigFile(_, possibleConfigFileNames)).headOption
    }
  }

  def latest(scalastyleXml: VirtualFile): ScalastyleConfiguration = {
    def readConfig(): TimestampedScalastyleConfiguration = {
      val configuration = ScalastyleConfiguration.readFromString(new String(scalastyleXml.contentsToByteArray()))
      (scalastyleXml.getModificationStamp, configuration)
    }

    val currentStamp = scalastyleXml.getModificationStamp
    cache.get(scalastyleXml) match {
      case Some((`currentStamp`, config)) => config
      case _ =>
        val stampedConfig@(_, config) = readConfig()
        cache += scalastyleXml -> stampedConfig
        config
    }
  }

  private def levelToProblemType(level: Level): ProblemHighlightType = level.name match {
    case Level.Info => ProblemHighlightType.INFORMATION
    case Level.Error => ProblemHighlightType.GENERIC_ERROR
    case Level.Warning => ProblemHighlightType.WEAK_WARNING
    case _ => ProblemHighlightType.GENERIC_ERROR
  }

  private case class MessageToProblemDescriptorConverter(file: PsiFile, manager: InspectionManager) {

    private val maybeDocument = PsiDocumentManager.getInstance(file.getProject).getDocument(file).toOption

    def toProblemDescriptor(msg: Message[SourceSpec]): Option[ProblemDescriptor] = msg match {
      case StyleError(_, _, key, level, args, Some(line), column, customMessage) =>
        findPsiElement(line, column).filter(e => e.isPhysical && !e.getTextRange.isEmpty).map { e =>
          val message = Messages.format(key, args, customMessage)
          manager.createProblemDescriptor(e, message, Array.empty[LocalQuickFix], ScalastyleCodeInspection.levelToProblemType(level), true, false)
        }

      case _ => None
    }

    private def isAtPosition(e: PsiElement, line: Int, column: Option[Int]): Boolean = {
      val correctLine = if (line > 0) line - 1 else 0
      val sameLine = maybeDocument.exists(correctLine == _.getLineNumber(e.getTextOffset))
      (column, maybeDocument) match {
        case (Some(col), Some(document)) =>
          val offset = document.getLineStartOffset(correctLine) + col
          sameLine && e.getTextRange.contains(offset)
        case _ => sameLine
      }
    }

    private def findPsiElement(line: Int, column: Option[Int]): Option[PsiElement] =
      file.depthFirst().filter(e => e != file && isAtPosition(e, line, column)).toStream.headOption
  }

  private def checkWithScalastyle(file: PsiFile, config: ScalastyleConfiguration): List[Message[SourceSpec]] = {
    val checker = new ScalastyleChecker[SourceSpec](None)
    checker.checkFiles(config, Seq(new SourceSpec(file.getName, file.getText)))
  }
}
