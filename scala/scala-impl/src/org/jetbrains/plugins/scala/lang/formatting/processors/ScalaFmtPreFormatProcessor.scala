package org.jetbrains.plugins.scala.lang.formatting.processors

import java.awt.Point
import java.util.concurrent.ConcurrentMap

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.hocon.psi.HoconPsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.project.UserDataHolderExt
import org.jetbrains.sbt.language.SbtFileImpl
import org.scalafmt.Formatted.Success
import org.scalafmt.Scalafmt
import org.scalafmt.config.{Config, ScalafmtConfig, ScalafmtRunner}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

class ScalaFmtPreFormatProcessor extends PreFormatProcessor {
  override def process(element: ASTNode, range: TextRange): TextRange = {
    val psiFile = Option(element.getPsi).map(_.getContainingFile)
    if (!psiFile.exists(CodeStyle.getCustomSettings(_, classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER)) return range
    psiFile match {
      case Some(null) | None => TextRange.EMPTY_RANGE
      case _ if range.isEmpty => TextRange.EMPTY_RANGE
      case Some(file: ScalaFile) =>
        ScalaFmtPreFormatProcessor.formatIfRequired(file, range)
        TextRange.EMPTY_RANGE
      case _ => range
    }
  }
}

object ScalaFmtPreFormatProcessor {

  def configFor(psi: PsiFile): ScalafmtConfig = {
    val settings = CodeStyle.getCustomSettings(psi, classOf[ScalaCodeStyleSettings])
    val project = psi.getProject
    val config = if (settings.SCALAFMT_CONFIG_PATH.nonEmpty) {
      scalaFmtConfigFile(settings, project) match {
        case Some(custom) => storeOrUpdate(externalScalafmtConfigs, custom, project)
        case _ =>
          reportBadConfig(settings.SCALAFMT_CONFIG_PATH, psi)
          ScalafmtConfig.intellij
      }
    } else {
      //auto-detect settings
      val configFileName = ".scalafmt.conf"
      Option(project.getBaseDir.findChild(configFileName)).
        map(ScalaPsiManager.instance(project).getScalafmtProjectConfig).getOrElse(ScalafmtConfig.intellij)
    }
    psi match {
      case _: SbtFileImpl => config.copy(runner = ScalafmtRunner.sbt)
      case _ => config
    }
  }

  def scalaFmtConfigFile(settings: ScalaCodeStyleSettings, project: Project): Option[VirtualFile] =
    Option(StandardFileSystems.local.findFileByPath(absolutePathFromConfigPath(settings.SCALAFMT_CONFIG_PATH, project)))

  def storeOrUpdate(map: ConcurrentMap[VirtualFile, (ScalafmtConfig, Long)], vFile: VirtualFile, project: Project): ScalafmtConfig = {
    Option(map.get(vFile)) match {
      case Some((config, stamp)) if stamp == vFile.getModificationStamp => config
      case _ =>
        val newVal = (loadConfig(vFile, project), vFile.getModificationStamp)
        map.put(vFile, newVal)
        newVal._1
    }
  }

  private def absolutePathFromConfigPath(path: String, project: Project): String = {
    if (path.startsWith(".")) {
      project.getBaseDir.getCanonicalPath + "/" + path
    } else path
  }

  private def formatIfRequired(file: PsiFile, range: TextRange): Unit = {
    val cached = file.getOrUpdateUserData(FORMATTED_RANGES_KEY, (new TextRanges, file.getModificationStamp))

    if (cached._2 == file.getModificationStamp && cached._1.contains(range)) return

    val delta = formatRange(file, range)
    def moveRanges(textRanges: TextRanges): TextRanges = {
      textRanges.ranges.map{ otherRange =>
        if (otherRange.getEndOffset <= range.getStartOffset) otherRange
        else if (otherRange.getStartOffset >= range.getEndOffset) otherRange.shiftRight(delta)
        else TextRange.EMPTY_RANGE
      }.foldLeft(new TextRanges())((acc, aRange) => acc.union(aRange))
    }

    val ranges = if (cached._2 == file.getModificationStamp) moveRanges(cached._1) else new TextRanges

    file.putUserData(FORMATTED_RANGES_KEY, (ranges.union(range.grown(delta)), file.getModificationStamp))
  }

  private def formatRange(file: PsiFile, range: TextRange): Int = {
    val start = System.currentTimeMillis()
    val project = file.getProject
    val manager = PsiDocumentManager.getInstance(project)
    val document = manager.getDocument(file)
    if (document == null) return 0
    implicit val fileText: String = file.getText
    val config = configFor(file)
    val wholeFileFormatResult = if (range == file.getTextRange) Scalafmt.format(fileText, config).toEither.toOption else None
    val (elements, formatted, wrapped) = wholeFileFormatResult match {
      case Some(formattedCode) =>
        (Seq(file), formattedCode, false)
      case _ =>
        val wrapElements = elementsInRangeWrapped(file, range)
        if (wrapElements.isEmpty) return 0
        val elementsText = wrapElements.map(getText).mkString("")
        Scalafmt.format(wrap(elementsText), config) match {
          case Success(formattedCode) => (wrapElements, formattedCode, true)
          case _ => return 0
        }
    }
    val formattedTime = System.currentTimeMillis()
    val wrapFile = PsiFileFactory.getInstance(project).createFileFromText("ScalaFmtFormatWrapper", ScalaFileType.INSTANCE, formatted)
    val textRangeDelta = replaceWithFormatted(wrapFile, elements, range, wrapped)
    val replacedTime = System.currentTimeMillis()
    manager.commitDocument(document)
    val doneTime = System.currentTimeMillis()
    println("formatting range " + range +  "took " + (formattedTime - start) + "ms, replacing took " + (replacedTime - formattedTime) + "ms, committing took " + (doneTime - replacedTime) + "ms")
    textRangeDelta
  }

  private val wrapPrefix = "class ScalaFmtFormatWrapper {\n"
  private val wrapSuffix = "\n}"

  //Use this since calls to 'getText' for inner elements of big files are somewhat expensive
  private def getText(element: PsiElement)(implicit fileText: String) =
    fileText.substring(element.getTextRange.getStartOffset, element.getTextRange.getEndOffset)

  private def wrap(elementText: String): String =
    wrapPrefix + elementText + wrapSuffix

  private def unwrap(wrapFile: PsiFile): Seq[PsiElement] = {
    val templateBody = PsiTreeUtil.findChildOfType(wrapFile, classOf[ScTemplateBody])
    if (templateBody == null) Seq.empty
    else {
      //get rid of braces and whitespaces near them
      templateBody.children.toList
        .drop(2).dropRight(2)
    }
  }

  private def isWhitespace(element: PsiElement) = element.isInstanceOf[PsiWhiteSpace]

  private def isProperUpperLevelPsi(element: PsiElement): Boolean = element match {
    case _: ScBlockStatement | _: ScMember | _: PsiWhiteSpace => true
    case l: LeafPsiElement => l.getElementType == ScalaTokenTypes.tIDENTIFIER
    case _ => false
  }

  private def elementsInRangeWrapped(file: PsiFile, range: TextRange)(implicit fileText: String): Seq[PsiElement] = {
    val startElement = file.findElementAt(range.getStartOffset)
    val endElement = file.findElementAt(range.getEndOffset - 1)
    if (startElement == null || endElement == null) return Seq.empty
    Option(PsiTreeUtil.findCommonParent(startElement, endElement)) match {
      case Some(_: PsiWhiteSpace) => Seq.empty
      case Some(parent: LeafPsiElement) if isProperUpperLevelPsi(parent) => Seq(parent)
      case Some(parent) =>
        val rawChildren = parent.children.toArray
        var children = rawChildren.filter(_.getTextRange.intersects(range))

        //drop unnecessary whitespaces
        while (children.headOption.exists(isWhitespace)) children = children.tail
        while (children.lastOption.exists(isWhitespace)) children = children.dropRight(1)

        if (children.isEmpty) Seq.empty
        else if (children.forall(isProperUpperLevelPsi)) {
          //for uniformity use the upper-most of embedded elements with same contents
          if (children.length == rawChildren.length && isProperUpperLevelPsi(parent)) Seq(parent)
          else children
        }
        else if (isProperUpperLevelPsi(parent)) Seq(parent)
        else ScalaPsiUtil.getParentWithProperty(parent, strict = false, isProperUpperLevelPsi) match {
          case Some(properParent) if properParent != file => Seq(properParent)
          case _ => Seq.empty
        }
      case Some(parent) if isProperUpperLevelPsi(parent) => Seq(parent)
      case _ => Seq.empty
    }
  }

  @tailrec
  private def getElementIndent(element: PsiElement)(implicit fileText: String): Int = {
    val prevLeaf = PsiTreeUtil.prevLeaf(element, true)
    if (prevLeaf == null) return 0
    element match {
      case ws: PsiWhiteSpace =>
        val wsText = ws.getText
        if (wsText.contains("\n"))
          wsText.substring(wsText.lastIndexOf("\n") + 1).length
        else getElementIndent(prevLeaf)
      case _ => getElementIndent(prevLeaf)
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

  private def replaceWithFormatted(wrapFile: PsiFile, elements: Seq[PsiElement], range: TextRange, isWrapped: Boolean)(implicit fileText: String): Int = {
    val replaceElements: Seq[PsiElement] = if (isWrapped) unwrap(wrapFile) else Seq(wrapFile)
    val project = elements.head.getProject
    val elementsToTraverse: ListBuffer[(PsiElement, PsiElement)] = ListBuffer(replaceElements zip elements:_*)
    var delta = 0
    val additionalIndent = if (isWrapped) getElementIndent(elements.head) - 2 else 0
    def widenWs(ws: PsiElement): PsiElement = {
      if (additionalIndent == 0) ws
      else {
        val wsText = ws.getText
        if (!wsText.contains("\n")) ws
        else if (additionalIndent >= 0) ScalaPsiElementFactory.createWhitespace(wsText + (" " * additionalIndent))(project)
        else {
          val newlineIndex = wsText.lastIndexOf("\n") + 1
          val trailingWhitespaces = wsText.substring(newlineIndex).length
          ScalaPsiElementFactory.createWhitespace(wsText.substring(0, newlineIndex + Math.max(0, trailingWhitespaces + additionalIndent)))(project)
        }
      }
    }
    def traverseSettingWs(formatted: PsiElement, original: PsiElement): Unit = {
      var formattedIndex = 0
      var originalIndex = 0
      if (isWhitespace(formatted) && isWhitespace(original)) original.replace(widenWs(formatted))

      val formattedChildren = formatted.children.toArray
      val originalChildren = original.children.toArray

      while (formattedIndex < formattedChildren.length && originalIndex < originalChildren.length) {
        val originalElement = originalChildren(originalIndex)
        val formattedElement = formattedChildren(formattedIndex)
        val isInRange = originalElement.getTextRange.intersects(range.grown(delta))
        def replace(originalElement: PsiElement, formattedElement: PsiElement): Unit = {
          //here getText/getTextLength is fine since we only replace leaf elements and they actually store the text
          if (originalElement.getText != formattedElement.getText && isInRange) {
            originalElement.replace(formattedElement)
            delta += formattedElement.getTextLength - originalElement.getTextLength
          }
          formattedIndex += 1
          originalIndex += 1
        }
        (originalElement, formattedElement) match {
          case (originalWs: PsiWhiteSpace, formattedWs: PsiWhiteSpace) => //replace whitespace
            replace(originalWs, widenWs(formattedWs))
          case (_, formattedWs: PsiWhiteSpace) => //a whitespace has been added
            val parent = originalElement.getParent
            if (parent != null && isInRange) {
              val widenedWs = widenWs(formattedWs)
              parent.addBefore(widenedWs, originalElement)
              delta += widenedWs.getTextLength
            }
            formattedIndex += 1
          case (originalWs: PsiWhiteSpace, _) => //a whitespace has been removed
            if (isInRange) {
              originalWs.delete()
              delta -= originalWs.getTextLength
            }
            originalIndex += 1
          case (originalComment: PsiComment, formattedComment: PsiComment) => //replace comments
            replace(originalComment, formattedComment)
          case _ =>
            if (isInRange) {
              elementsToTraverse += ((formattedElement, originalElement))
            }
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

  private def reportError(errorText: String, psiFile: PsiFile): Unit = {
    val project = psiFile.getProject
    val popupFactory = JBPopupFactory.getInstance
    val frame = WindowManagerEx.getInstanceEx.getFrame(project)
    val balloon = popupFactory.createHtmlTextBalloonBuilder(
      errorText,
      MessageType.WARNING,
      null).createBalloon
    balloon.show(new RelativePoint(frame, new Point(frame.getWidth - 20, 20)), Balloon.Position.above)
  }

  private def reportInvalidCodeFailure(psiFile: PsiFile): Unit =
    reportError("Failed to find correct surrounding code to pass for scalafmt, no formatting will be performed", psiFile)

  private def reportBadConfig(path: String, psiFile: PsiFile): Unit =
    reportError("Failed to load scalafmt config " + path + ", using default configuration instead", psiFile)

  //TODO get rid of this once com.intellij.util.text.TextRanges does not have an error on unifying (x, x+1) V (x+1, y)
  class TextRanges(val ranges: Seq[TextRange]) {
    def this() = this(Seq.empty)

    def contains(range: TextRange): Boolean = ranges.exists(_.contains(range))

    def union(range: TextRange): TextRanges = if (contains(range)) this else {
      val intersections = ranges.filter(_.intersects(range))
      val newRanges = if (intersections.isEmpty) {
        ranges :+ range
      } else {
        ranges.filter(!_.intersects(range)) :+ intersections.foldLeft(range)((acc, nextRange) => acc.union(nextRange))
      }
      new TextRanges(newRanges)
    }
  }

  private val FORMATTED_RANGES_KEY: Key[(TextRanges, Long)] = Key.create("scala.fmt.formatted.ranges")
}