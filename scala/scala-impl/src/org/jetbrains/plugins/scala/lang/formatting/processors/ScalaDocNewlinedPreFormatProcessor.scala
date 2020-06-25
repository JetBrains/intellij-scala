package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiErrorElement}
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.{ElementType, IteratorExt, PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.formatting.FormatterUtil.isDocWhiteSpace
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createDocWhiteSpaceWithNewLine, createLeadingAsterisk}
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocList, ScDocParagraph, ScDocTag}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * @author Roman.Shein
 *         Date: 12.11.2015
 */
final class ScalaDocNewlinedPreFormatProcessor extends PreFormatProcessor with ScalaIntellijFormatterLike {

  private class ScalaDocNewlinedPreFormatVisitor(settings: ScalaCodeStyleSettings) extends ScalaElementVisitor {

    override def visitDocComment(s: ScDocComment): Unit =
      s.children.foreach(fixNewlines(_, settings))

    override def visitTag(s: ScDocTag): Unit =
      fixNewlines(s, settings)
  }

  override def process(element: ASTNode, range: TextRange): TextRange = {
    val psiElement = element.getPsi
    val scalaSettings = ScalaCodeStyleSettings.getInstance(psiElement.getProject)

    if (needToProcess(psiElement, range, scalaSettings)) {
      Option(psiElement)
        .filter(_.isValid)
        .filter(_.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
        .map(processScalaElement(_, range, scalaSettings))
        .map(range.grown)
        .getOrElse(range)
    } else {
      range
    }
  }

  private def processScalaElement(psiElement: PsiElement, range: TextRange, scalaSettings: ScalaCodeStyleSettings): Int = {
    val oldRange = psiElement.getTextRange
    val visitor = new ScalaDocNewlinedPreFormatVisitor(scalaSettings)

    for {
      elem <- elementsToProcess(psiElement, range)
      if elem.isValid
    } elem.accept(visitor)

    val diff = psiElement.getTextRange.getEndOffset - oldRange.getEndOffset
    //range can be overshrinked only for small elements that can't be formatted on their own, so it's ok to return whole range
    if (range.getLength + diff <= 0) 0 else diff
  }

  private def elementsToProcess(psiElement: PsiElement, range: TextRange): Seq[PsiElement] = {
    psiElement.depthFirst().filter(_.getTextRange.intersects(range)).toVector.collect {
      case comment: ScDocComment => comment
      case tag: ScDocTag => tag
    }.reverse
  }

  private def fixNewlines(element: PsiElement, scalaSettings: ScalaCodeStyleSettings): Unit = {
    import ScalaDocNewlinedPreFormatProcessor._
    val prevElement = element.getPrevSibling
    if (prevElement == null) return

    if (scalaSettings.ENABLE_SCALADOC_FORMATTING)
      (isTag(prevElement), isTag(element)) match {
        case (true, true) =>
          //process newlines between tags
          val needBlankLineBetweenTags =
            isParamTag(prevElement) && !isParamTag(element) && scalaSettings.SD_BLANK_LINE_AFTER_PARAMETERS_COMMENTS ||
              isReturnTag(prevElement) && !isReturnTag(element) && scalaSettings.SD_BLANK_LINE_AFTER_RETURN_COMMENTS ||
              isParamTag(prevElement) && isParamTag(element) && scalaSettings.SD_BLANK_LINE_BETWEEN_PARAMETERS ||
              !isParamTag(prevElement) && isParamTag(element) && scalaSettings.SD_BLANK_LINE_BEFORE_PARAMETERS

          val newlinesNew = if (needBlankLineBetweenTags) 2 else 1
          fixNewlinesBetweenElements(prevElement.getLastChild, newlinesNew, scalaSettings)
        case (false, true) =>
          val prevElementFixed = prevElement match {
            case paragraph: ScDocParagraph => Option(PsiTreeUtil.getDeepestLast(paragraph))
            case list: ScDocList           => Option(PsiTreeUtil.getDeepestLast(list))
            case _                         => None
          }
          prevElementFixed match {
            case Some(asterisks@ElementType(ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)) =>
              //process newlines between description and tags
              val newLines = if (scalaSettings.SD_BLANK_LINE_BEFORE_TAGS) 2 else 1
              fixNewlinesBetweenElements(asterisks, newLines, scalaSettings)
            case _ =>
          }
        case _ =>
      }

    fixAsterisk(element)
  }

  private def fixAsterisk(element: PsiElement): Unit = {
    implicit val ctx: ProjectContext = element

    val nextElement = {
      val nextLeaves = Iterator.iterate(PsiTreeUtil.nextLeaf(element))(PsiTreeUtil.nextLeaf)
      // dropping error elements e.g. for unclosed wiki-syntax in the end of the comment
      nextLeaves
        .dropWhile(_.isInstanceOf[PsiErrorElement])
        .headOption.orNull
    }
    val parent      = element.getParent

    //add asterisks inside multi-line newLines e.g.
    // "\n\n\n" "*"  -> "\n*\n*\n" "*"
    // "\n\n\n" "abc"  -> "\n*\n*\n" "*" "abc"
    if (nextElement != null && ScalaDocNewlinedPreFormatProcessor.isNewLine(element)) {
      val newLinesCount = element.getText.count(_ == '\n')
      for (_ <- 2 to newLinesCount) {
        parent.addBefore(createDocWhiteSpaceWithNewLine, element)
        parent.addBefore(createLeadingAsterisk, element)
      }

      val newElement: PsiElement =
        if (newLinesCount > 1)
          element.replace(createDocWhiteSpaceWithNewLine)
        else
          element

      nextElement.elementType match {
        case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS | ScalaDocTokenType.DOC_COMMENT_END =>
        case  _ =>
          parent.addAfter(createLeadingAsterisk, newElement)
      }
    } else {
      //since siblings can be replaced, first we should materialize the list
      // of children and only then process them
      val children = element.children.toList
      children.foreach(fixAsterisk)
    }
  }

  private def fixNewlinesBetweenElements(wsAnchor: PsiElement, newlinesNew: Int, settings: ScalaCodeStyleSettings): Unit =
    linesCountAndLastWsBeforeElement(wsAnchor) match {
      case Some((newlinesOld, lastWs)) =>
        fixNewlinesBetweenElementsInner(lastWs, newlinesOld, newlinesNew, settings)
      case _ =>
    }

  private def fixNewlinesBetweenElementsInner(
    lastWs: PsiElement,
    newlinesOld: Int, newlinesNew: Int,
    settings: ScalaCodeStyleSettings
  ): Unit = {
    if (newlinesOld > newlinesNew && !settings.SD_KEEP_BLANK_LINES_BETWEEN_TAGS) {
      //remove unnecessary newlines along with leading asterisks
      for (_ <- 1 to newlinesOld - newlinesNew) {
        lastWs.getPrevSibling.getNode.getElementType match {
          case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS =>
            lastWs.getPrevSibling.delete() //delete newline
            lastWs.getPrevSibling.delete() //delete leading asterisk
          case _                                               =>
            //deleted all the newlines, nothing to do here
            lastWs.delete()
        }
      }
    } else if (newlinesOld < newlinesNew) {
      implicit val ctx: ProjectContext = lastWs
      //add more newlines along with leading asterisks
      val parent = lastWs.getParent
      val prev   = lastWs.getPrevSibling

      inWriteAction {
        for (_ <- 1 to newlinesNew - newlinesOld) {
          parent.addBefore(createLeadingAsterisk, lastWs)
          parent.addAfter(createDocWhiteSpaceWithNewLine, prev)
        }
      }
    }
  }

  private def linesCountAndLastWsBeforeElement(element: PsiElement): Option[(Int, PsiElement)] = {
    import ScalaDocNewlinedPreFormatProcessor._
    val asterisksOpt = element.withPrevSiblings
      .dropWhile(_.elementType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)
      .headOption
    val asterisks = asterisksOpt match {
      case Some(el) => el
      case _        => return None
    }

    //last whitespace before the asterisk in tag psi element
    val lastWhitespace = asterisks.getPrevSibling
    if (!isDocWhiteSpace(lastWhitespace)) return None

    //count newlines
    var currentWs = lastWhitespace
    var newlinesCount = 0
    while (currentWs != null && isNewLine(currentWs)) {
      newlinesCount += 1
      PsiTreeUtil.prevLeaf(currentWs).getNode.getElementType match {
        case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS => currentWs = currentWs.getPrevSibling.getPrevSibling
        case _ => currentWs = null
      }
    }

    Some((newlinesCount, lastWhitespace))
  }
}

object ScalaDocNewlinedPreFormatProcessor {

  private def isNewLine(element: PsiElement): Boolean =
    isDocWhiteSpace(element) && element.textContains('\n')

  // TODO: mote to PSI
  private def getTagName(element: ScDocTag): Option[String] =
    Option(element.getNameElement).filter(isTagName).map(_.getText)

  private def isTagName(element: PsiElement): Boolean =
    element.getNode.getElementType == ScalaDocTokenType.DOC_TAG_NAME

  private def isTag(element: PsiElement): Boolean =
    element.getNode.getElementType == ScalaDocElementTypes.DOC_TAG

  private def isNamedTag(element: PsiElement, @NonNls names: String*): Boolean = element match {
    case tag: ScDocTag => getTagName(tag).exists(names.contains)
    case _ => false
  }

  private def isParamTag(element: PsiElement): Boolean = isNamedTag(element, "@param", "@tparam")

  private def isReturnTag(element: PsiElement): Boolean = isNamedTag(element, "@return")
}