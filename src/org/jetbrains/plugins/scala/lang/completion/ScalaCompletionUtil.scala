package org.jetbrains.plugins.scala
package lang
package completion

import psi._
import psi.api.base.patterns.ScCaseClause
import psi.api.expr.ScBlock
import psi.api.ScalaFile
import psi.api.toplevel.typedef.ScTypeDefinition
import psi.api.base.types.ScTypeElement
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._

/** 
* User: Alexander Podkhalyuzin
* Date: 21.05.2008.
*/

object ScalaCompletionUtil {
  def getLeafByOffset(offset: Int, element: PsiElement): PsiElement = {
    if (offset < 0) {
      return null
    }
    var candidate: PsiElement = element.getContainingFile()
    while (candidate.getNode().getChildren(null).length > 0) {
      candidate = candidate.findElementAt(offset)
    }
    return candidate
  }

  def getForAll(parent: PsiElement, leaf: PsiElement): (Boolean, Boolean) = {
    parent match {
      case _: ScalaFile => {
        if (leaf.getNextSibling != null && leaf.getNextSibling().getNextSibling().isInstanceOf[ScPackaging] &&
                leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1)
          return (true, false)
      }
      case _ =>
    }
    parent match {
      case _: ScalaFile | _: ScPackaging => {
        var node = leaf.getPrevSibling
        if (node.isInstanceOf[PsiWhiteSpace]) node = node.getPrevSibling
        node match {
          case x: PsiErrorElement => {
            val s = ErrMsg("wrong.top.statment.declaration")
            x.getErrorDescription match {
              case `s` => return (true, true)
              case _ => return (true, false)
            }
          }
          case _ => return (true, true)
        }
      }
      case _: ScReferenceExpression => {
        parent.getParent match {
          case _: ScBlockExpr | _: ScTemplateBody | _: ScBlock | _: ScCaseClause => {
            if (awful(parent, leaf))
              return (true, true)
          }
          case _ =>
        }
      }
      case _ =>
    }

    return (false, true)
  }

  def awful(parent: PsiElement, leaf: PsiElement): Boolean = {
    (leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
            leaf.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaTokenTypes.kDEF) && (parent.getPrevSibling == null ||
            parent.getPrevSibling.getPrevSibling == null ||
            (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementTypes.MATCH_STMT || !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement]))
  }

  val DUMMY_IDENTIFIER = "IntellijIdeaRulezzz"

  def checkClassWith(clazz: ScTypeDefinition, additionText: String, manager: PsiManager): Boolean = {
    val classText: String = clazz.getText
    val text = removeDummy(classText + " " + additionText)
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    return !checkErrors(dummyFile)
  }

  def checkElseWith(text: String, manager: PsiManager): Boolean = {
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY +
        ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), "class a {\n" + text + "\n}").asInstanceOf[ScalaFile]
    return !checkErrors(dummyFile)
  }

  def checkTypeWith(typez: ScTypeElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = removeDummy("class a { x:" + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    val value = !checkErrors(dummyFile)
    return value
  }

  def checkAnyTypeWith(typez: ScTypeElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = removeDummy("class a { val x:" + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    val value = !checkErrors(dummyFile)
    return value
  }

  def checkAnyWith(typez: PsiElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = removeDummy("class a { " + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    return !checkErrors(dummyFile)
  }

  def removeDummy(text: String): String = {
    return replaceDummy(text, "")
  }

  def replaceDummy(text: String, to: String): String = {
    return if (text.indexOf(DUMMY_IDENTIFIER) != -1) {
      val empty = to
      text.replaceAll("\\w*" + DUMMY_IDENTIFIER,to)
      //text.replace(DUMMY_IDENTIFIER.subSequence(0, DUMMY_IDENTIFIER.length), empty.subSequence(0, empty.length))
    } else text
  }

  def checkNewWith(news: ScNewTemplateDefinition, additionText: String, manager: PsiManager): Boolean = {
    val newsText = news.getText
    var text = removeDummy("class a { " + newsText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    return !checkErrors(dummyFile)
  }

  def checkReplace(elem: PsiElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = elem.getText
    var text = "class a { " + typeText + "}"
    if (text.indexOf(DUMMY_IDENTIFIER) == -1) return false
    text = replaceDummy(text, " "+ additionText+ " ")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    return !checkErrors(dummyFile)
  }

  private def checkErrors(elem: PsiElement): Boolean = {
    elem match {
      case _: PsiErrorElement => return true
      case _ =>
    }
    for (child <- elem.getChildren if checkErrors(child)) return true
    return false
  }
}