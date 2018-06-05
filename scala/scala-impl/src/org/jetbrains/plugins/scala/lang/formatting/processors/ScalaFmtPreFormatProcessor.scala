package org.jetbrains.plugins.scala.lang.formatting.processors

import java.util.concurrent.ConcurrentMap

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.TextRanges
import org.jetbrains.plugins.hocon.psi.HoconPsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.scalafmt.Formatted.Success
import org.scalafmt.Scalafmt
import org.scalafmt.config.{Config, ScalafmtConfig}

import scala.collection.mutable.ListBuffer

class ScalaFmtPreFormatProcessor extends PreFormatProcessor {
  override def process(element: ASTNode, range: TextRange): TextRange = {
    val psiFile = Option(element.getPsi).map(_.getContainingFile)
    if (!psiFile.exists(CodeStyle.getCustomSettings(_, classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER)) return range
    psiFile match {
      case Some(null) | None => TextRange.EMPTY_RANGE
      case Some(file) => ScalaFmtPreFormatProcessor.formatIfRequired(file, range)
    }
  }
}

object ScalaFmtPreFormatProcessor {

  def configFor(psi: PsiFile): ScalafmtConfig = {
    val settings = CodeStyle.getCustomSettings(psi, classOf[ScalaCodeStyleSettings])
    val project = psi.getProject
    if (settings.USE_CUSTOM_SCALAFMT_CONFIG_PATH) {
      Option(StandardFileSystems.local.findFileByPath(settings.SCALAFMT_CONFIG_PATH)) match {
        case Some(custom) => storeOrUpdate(externalScalafmtConfigs, custom, project)
        case _ =>
          Option(PsiDocumentManager.getInstance(project).getDocument(psi)).map(EditorFactory.getInstance().getEditors).
            flatMap(_.headOption).foreach(reportBadConfig(settings.SCALAFMT_CONFIG_PATH, _))
          settings.USE_CUSTOM_SCALAFMT_CONFIG_PATH = false
          ScalafmtConfig.intellij
      }
    } else {
      //auto-detect settings
      val configFileName = ".scalafmt.conf"
      Option(project.getBaseDir.findChild(configFileName)).
        map(ScalaPsiManager.instance(project).getScalafmtProjectConfig).getOrElse(ScalafmtConfig.intellij)
    }
  }

  def storeOrUpdate(map: ConcurrentMap[VirtualFile, (ScalafmtConfig, Long)], vFile: VirtualFile, project: Project): ScalafmtConfig = {
    Option(map.get(vFile)) match {
      case Some((config, stamp)) if stamp == vFile.getModificationStamp => config
      case _ =>
        val newVal = (loadConfig(vFile, project), vFile.getModificationStamp)
        map.put(vFile, newVal)
        newVal._1
    }
  }

  private def formatIfRequired(file: PsiFile, range: TextRange): TextRange = {
    val cache = ScalaPsiManager.instance(file.getProject).scalafmtFormattedFiles
    val cached = cache.getOrDefault(file, (new TextRanges, file.getModificationStamp))
    import collection.JavaConverters._
    if (cached._2 == file.getModificationStamp && cached._1.iterator().asScala.exists(_.contains(range))) return TextRange.EMPTY_RANGE
    val delta = formatRange(file, range)
    def moveRanges(ranges: TextRanges): TextRanges = {
      val res = new TextRanges
      ranges.asScala.map{ otherRange =>
        if (otherRange.getEndOffset <= range.getStartOffset) otherRange
        else if (otherRange.getStartOffset >= range.getEndOffset) otherRange.shiftRight(delta)
        else TextRange.EMPTY_RANGE
      }.foreach(res.union)
      res
    }
    val ranges = if (cached._2 == file.getModificationStamp) moveRanges(cached._1) else new TextRanges
    cache.put(file, (ranges.union(range.grown(delta)), file.getModificationStamp))
    TextRange.EMPTY_RANGE
  }

  private def formatRange(file: PsiFile, range: TextRange): Int = {
    val start = System.currentTimeMillis()
    val project = file.getProject
    val manager = PsiDocumentManager.getInstance(project)
    val document = manager.getDocument(file)
    if (document == null) return 0
    //TODO for some reason, scalaFmt does not properly change whitespaces with custom parsers
    val (config, element) = (configFor(file), file) //actualConfigAndElement(file, range, loadConfig(file))
    val formatted = Scalafmt.format(element.getText, config,
      Set(Range(document.getLineNumber(range.getStartOffset), document.getLineNumber(range.getEndOffset) + 1)))
    val formattedTime = System.currentTimeMillis()
    formatted match {
      case Success(formattedText) =>
        val textRangeDelta = replaceWithFormatted(formattedText, file, range)
        val replacedTime = System.currentTimeMillis()
        if (document != null) manager.commitDocument(document)
        val doneTime = System.currentTimeMillis()
        println("formatting range " + range +  "took " + (formattedTime - start) + "ms, replacing took " + (replacedTime - formattedTime) + "ms, committing took " + (doneTime - replacedTime) + "ms")
        textRangeDelta
      case _ =>
        println("Error on scalaFMT processing")
        0
    }
  }

  private def actualConfigAndElement(file: PsiFile, range: TextRange, config: ScalafmtConfig): (ScalafmtConfig, PsiElement) = {
    if (range == file.getTextRange) return (config, file)
    val startElement = file.findElementAt(range.getStartOffset)
    val endElement = file.findElementAt(range.getEndOffset)
    if (startElement == null || endElement == null) return (config, file)
    Option(PsiTreeUtil.findCommonParent(startElement, endElement)).map(PsiTreeUtil.getParentOfType(_, classOf[ScBlockStatement])) match {
      case Some(parent) =>
        (config.copy(runner = config.runner.copy(parser = scala.meta.parsers.Parse.parseStat)), parent)
      case _ => (config, file)
    }
  }

  private def loadConfig(configFile: VirtualFile, project: Project): ScalafmtConfig = {
    PsiManager.getInstance(project).findFile(configFile) match {
      case hoconFile: HoconPsiFile =>
        val res = Config.fromHoconString(hoconFile.getText)
        res.getOrElse(ScalafmtConfig.intellij)
      case _ => ScalafmtConfig.intellij
    }
  }

  private def replaceWithFormatted(formattedText: String, file: PsiFile, range: TextRange): Int = {
    val project = file.getProject
    val formattedFile = PsiFileFactory.getInstance(project).createFileFromText("scalaFmtFormattedFile", ScalaFileType.INSTANCE, formattedText)
    val elementsToTraverse: ListBuffer[(PsiElement, PsiElement)] = ListBuffer((formattedFile, file))
    var delta = 0
    def traverseSettingWs(formatted: PsiElement, original: PsiElement): Unit = {
      var formattedIndex = 0
      var originalIndex = 0
      val formattedChildren = PsiTreeUtil.getChildrenOfType(formatted, classOf[PsiElement])
      val originalChildren = PsiTreeUtil.getChildrenOfType(original, classOf[PsiElement])
      if (formattedChildren == null || originalChildren == null) return
      def replace(originalElement: PsiElement, formattedElement: PsiElement): Unit = {
        if (originalElement.getText != formattedElement.getText) {
          originalElement.replace(formattedElement)
          delta += formattedElement.getTextLength - originalElement.getTextLength
        }
        formattedIndex += 1
        originalIndex += 1

      }
      while (formattedIndex < formattedChildren.size && originalIndex < originalChildren.size) {
        val originalElement = originalChildren(originalIndex)
        val formattedElement = formattedChildren(formattedIndex)
        (originalElement, formattedElement) match {
          case _ if originalElement.getTextRange.intersection(range.grown(delta)) == null =>
            elementsToTraverse += ((formattedElement, originalElement))
            formattedIndex += 1
            originalIndex += 1
          case (originalWs: PsiWhiteSpace, formattedWs: PsiWhiteSpace) => //replace whitespace
            replace(originalWs, formattedWs)
          case (_, formattedWs: PsiWhiteSpace) => //a whitespace has been added
            val parent = originalElement.getParent
            if (parent != null) {
              parent.addBefore(formattedWs, originalElement)
              delta += formattedWs.getTextLength
            }
            formattedIndex += 1
          case (originalWs: PsiWhiteSpace, _) => //a whitespace has been removed
            originalWs.delete()
            delta -= originalWs.getTextLength
            originalIndex += 1
          case (originalComment: PsiComment, formattedComment: PsiComment) => //replace comments
            replace(originalComment, formattedComment)
          case _ =>
            elementsToTraverse += ((formattedElement, originalElement))
            originalIndex += 1
            formattedIndex += 1
        }
      }
    }
    while(elementsToTraverse.nonEmpty) {
      val head = elementsToTraverse.head
      elementsToTraverse.remove(0)
      traverseSettingWs(head._1, head._2)
    }
    delta
  }

  private val externalScalafmtConfigs: ConcurrentMap[VirtualFile, (ScalafmtConfig, Long)] = ContainerUtil.createConcurrentWeakMap()

  private def reportBadConfig(path: String, component: Editor): Unit = {
    val popupFactory = JBPopupFactory.getInstance
    val bestLocation = popupFactory.guessBestPopupLocation(component)
    popupFactory.createHtmlTextBalloonBuilder(
      "Failed to load scalafmt config " + path + ", using default configuration instead",
      MessageType.WARNING,
      null).createBalloon.show(new RelativePoint(bestLocation.getPoint()), Balloon.Position.above)
  }
}