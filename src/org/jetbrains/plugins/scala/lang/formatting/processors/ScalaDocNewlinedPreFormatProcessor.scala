package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiManager, PsiElement}
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocTag, ScDocComment}

/**
  * @author Roman.Shein
  *         Date: 12.11.2015
  */
class ScalaDocNewlinedPreFormatProcessor extends ScalaRecursiveElementVisitor with PreFormatProcessor {
  override def process(element: ASTNode, range: TextRange): TextRange =
    Option(element.getPsi).map { psiElem =>
      val oldRange = psiElem.getTextRange
      psiElem.accept(this)
      psiElem.getTextRange.getEndOffset - oldRange.getEndOffset
    }.map(range.grown).getOrElse(range)

  override def visitDocComment(s: ScDocComment) {
    val scalaSettings = CodeStyleSettingsManager.getSettings(s.getProject).getCustomSettings(classOf[ScalaCodeStyleSettings])
    s.getChildren.foreach {fixNewlines(_, scalaSettings)}
  }

  private def fixNewlines(element: PsiElement, scalaSettings: ScalaCodeStyleSettings): Unit = {
    import ScalaDocNewlinedPreFormatProcessor._
    val prevElement = element.getPrevSibling
    if (prevElement == null) return
    (isTag(prevElement), isTag(element)) match {
      case (true, true) =>
        //process newlines between tags
        val newlinesNew = if (isParamTag(prevElement) && !isParamTag(element)) {
          if (scalaSettings.SD_BLANK_LINE_AFTER_PARAMETERS_COMMENTS) 2 else 1
        } else if (isReturnTag(prevElement) && !isReturnTag(element)) {
          if (scalaSettings.SD_BLANK_LINE_AFTER_RETURN_COMMENTS) 2 else 1
        } else 1
        fixNewlinesBetweenElements(prevElement.getLastChild, newlinesNew)
      case (false, true) =>
        var current = prevElement
        //do not insert newlines when there is no description
        while (current != null && (current.getNode.getElementType == ScalaDocTokenType.DOC_WHITESPACE ||
          current.getNode.getElementType == ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)) {
          current = current.getPrevSibling
        }
        if (current != null && current.getNode.getElementType != ScalaDocTokenType.DOC_COMMENT_START) {
          //process newlines between description and tags
          fixNewlinesBetweenElements(prevElement, if (scalaSettings.SD_BLANK_LINE_BEFORE_TAGS) 2 else 1)
        }
      case _ =>
    }
    if (isNewLine(prevElement) && !Set(ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS, ScalaDocTokenType.DOC_COMMENT_END).contains(element.getNode.getElementType)) {
      prevElement.getParent.
        addAfter(ScalaPsiElementFactory.createLeadingAsterisk(PsiManager.getInstance(element.getProject)), prevElement)
    }
  }

  private def fixNewlinesBetweenElements(wsAnchor: PsiElement, newlinesNew: Int): Unit = {
    linesCountAndLastWsBeforeElement(wsAnchor) match {
      case Some((newlinesOld, lastWs)) =>
        if (newlinesOld > newlinesNew) {
          //remove unnecessary newlines along with leading asterisks
          for (i <- 1 to newlinesOld - newlinesNew) {
            lastWs.getPrevSibling.getNode.getElementType match {
              case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS =>
                //delete newline and leading asterisk
                lastWs.getPrevSibling.delete()
                lastWs.getPrevSibling.delete()
              case _ =>
                //deleted all the newlines, nothing to do here
                lastWs.delete()
                return
            }
          }
        } else if (newlinesOld < newlinesNew) {
          //add more newlines along with leading asterisks
          val parent = lastWs.getParent
          val manager = PsiManager.getInstance(lastWs.getProject)
          val prev = lastWs.getPrevSibling
          for (i <- 1 to newlinesNew - newlinesOld) {
            parent.addBefore(ScalaPsiElementFactory.createLeadingAsterisk(manager), lastWs)
            parent.addAfter(ScalaPsiElementFactory.createDocWhiteSpace(manager), prev)
          }
        }
      case _ =>
    }
  }

  private def linesCountAndLastWsBeforeElement(element: PsiElement): Option[(Int, PsiElement)] = {
    import ScalaDocNewlinedPreFormatProcessor._
    var currentChild = element
    while (currentChild != null && currentChild.getNode.getElementType != ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
      currentChild = currentChild.getPrevSibling
    }
    if (currentChild == null) return None
    //last wthitespace before the asterisk in tag psi element
    val lastWhitespace = currentChild.getPrevSibling
    if (!isWhiteSpace(lastWhitespace)) return None
    //count newlines
    var currentWs = lastWhitespace
    var newlinesCount = 0
    while (currentWs != null && isNewLine(currentWs)) {
      newlinesCount += 1
      currentWs.getPrevSibling.getNode.getElementType match {
        case ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS => currentWs = currentWs.getPrevSibling.getPrevSibling
        case _ => currentWs = null
      }
    }
    Some((newlinesCount, lastWhitespace))
  }
}

object ScalaDocNewlinedPreFormatProcessor {
  def isWhiteSpace(element: PsiElement): Boolean = isWhiteSpace(element.getNode)

  def isWhiteSpace(node: ASTNode): Boolean = node.getElementType == ScalaDocTokenType.DOC_WHITESPACE

  def isNewLine(element: PsiElement): Boolean = isWhiteSpace(element) && element.getText.contains("\n")

  def getTagName(element: ScDocTag): Option[String] =
    Option(element.getNameElement).filter(_.getNode.getElementType == ScalaDocTokenType.DOC_TAG_NAME).
      map(_.getText)

  def isTag(element: PsiElement): Boolean = element.getNode.getElementType == ScalaDocElementTypes.DOC_TAG

  def isNamedTag(element: PsiElement, names: String*): Boolean = element match {
    case tag: ScDocTag => getTagName(tag).exists(names.contains)
    case _ => false
  }

  def isParamTag(element: PsiElement) = isNamedTag(element, "@param", "@tparam")

  def isReturnTag(element: PsiElement) = isNamedTag(element, "@return")
}