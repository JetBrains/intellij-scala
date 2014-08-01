package org.jetbrains.plugins.scala
package lang
package completion

import psi._
import api.base.ScReferenceElement
import psi.api.base.patterns.ScCaseClause
import psi.api.ScalaFile
import psi.api.toplevel.typedef.ScTypeDefinition
import psi.api.base.types.ScTypeElement





import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._
import com.intellij.codeInsight.completion.{PrefixMatcher, CompletionParameters}
import refactoring.namesSuggester.NameSuggester
import types.ScType
import collection.mutable.ArrayBuffer
import com.intellij.openapi.util.Key

/**
* User: Alexander Podkhalyuzin
* Date: 21.05.2008.
*/

object ScalaCompletionUtil {
  val PREFIX_COMPLETION_KEY: Key[Boolean] = Key.create("prefix.completion.key")

  def completeThis(ref: ScReferenceExpression): Boolean = {
    ref.qualifier match {
      case Some(_) => false
      case None =>
        ref.getParent match {
          case inf: ScInfixExpr if inf.operation == ref => false
          case postf: ScPostfixExpr if postf.operation == ref => false
          case pref: ScPrefixExpr if pref.operation == ref => false
          case _ => true
        }
    }
  }

  def shouldRunClassNameCompletion(parameters: CompletionParameters, prefixMatcher: PrefixMatcher,
                                   checkInvocationCount: Boolean = true, lookingForAnnotations: Boolean = false): Boolean = {
    val element = parameters.getPosition
    if (checkInvocationCount && parameters.getInvocationCount < 2) return false
    if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
      element.getParent match {
        case ref: ScReferenceElement if ref.qualifier != None => return false
        case _ =>
      }
    }
    if (checkInvocationCount && parameters.getInvocationCount >= 2) return true
    val prefix = prefixMatcher.getPrefix
    val capitalized = prefix.length() > 0 && prefix.substring(0, 1).capitalize == prefix.substring(0, 1)
    capitalized || lookingForAnnotations
  }

  def generateAnonymousFunctionText(braceArgs: Boolean, params: scala.Seq[ScType], canonical: Boolean,
                                    withoutEnd: Boolean = false, arrowText: String = "=>"): String = {
    val text = new StringBuilder()
    if (braceArgs) text.append("case ")
    val paramNamesWithTypes = new ArrayBuffer[(String, ScType)]
    def contains(name: String): Boolean = {
      paramNamesWithTypes.exists{
        case (s, _) => s == name
      }
    }
    for (param <- params) {
      val names = NameSuggester.suggestNamesByType(param)
      var name = if (names.length == 0) "x" else names(0)
      if (contains(name)) {
        var count = 0
        var newName = name + count
        while (contains(newName)) {
          count += 1
          newName = name + count
        }
        name = newName
      }
      paramNamesWithTypes.+=(name -> param)
    }
    val iter = paramNamesWithTypes.map {
      case (s, tp) => s + ": " + (if (canonical) {
        ScType.canonicalText(tp)
      } else ScType.presentableText(tp))
    }
    val paramsString =
      if (paramNamesWithTypes.size != 1 || !braceArgs) iter.mkString("(", ", ", ")")
      else iter.head
    text.append(paramsString)
    if (!withoutEnd) text.append(" ").append(arrowText)
    text.toString()
  }

  def getLeafByOffset(offset: Int, element: PsiElement): PsiElement = {
    if (offset < 0) {
      return null
    }
    var candidate: PsiElement = element.getContainingFile
    if (candidate == null || candidate.getNode == null) return null
    while (candidate.getNode.getChildren(null).length > 0) {
      candidate = candidate.findElementAt(offset)
      if (candidate == null || candidate.getNode == null) return null
    }
    candidate
  }

  /**
   * first return value mean to stop here.
   * Second return value in case if first is true return second value
   */
  def getForAll(parent: PsiElement, leaf: PsiElement): (Boolean, Boolean) = {
    parent match {
      case _: ScalaFile =>
        if (leaf.getNextSibling != null && leaf.getNextSibling.getNextSibling.isInstanceOf[ScPackaging] &&
                leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1)
          return (true, false)
      case _ =>
    }
    parent match {
      case _: ScalaFile | _: ScPackaging =>
        var node = leaf.getPrevSibling
        if (node.isInstanceOf[PsiWhiteSpace]) node = node.getPrevSibling
        node match {
          case x: PsiErrorElement =>
            val s = ErrMsg("wrong.top.statment.declaration")
            x.getErrorDescription match {
              case `s` => return (true, true)
              case _ => return (true, false)
            }
          case _ => return (true, true)
        }
      case expr: ScReferenceExpression =>
        parent.getParent match {
          case _: ScBlockExpr | _: ScTemplateBody | _: ScBlock | _: ScCaseClause =>
            if (awful(parent, leaf))
              return (true, true)
          case _ =>
        }
      case _ =>
    }

    (false, true)
  }

  def awful(parent: PsiElement, leaf: PsiElement): Boolean = {
    (leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
      leaf.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaTokenTypes.kDEF) &&
      (parent.getPrevSibling == null || parent.getPrevSibling.getPrevSibling == null ||
        (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementTypes.MATCH_STMT ||
          !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement]))
  }

  val DUMMY_IDENTIFIER = "IntellijIdeaRulezzz"

  def checkClassWith(clazz: ScTypeDefinition, additionText: String, manager: PsiManager): Boolean = {
    val classText: String = clazz.getText
    val text = removeDummy(classText + " " + additionText)
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkElseWith(text: String, manager: PsiManager): Boolean = {
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, "class a {\n" + text + "\n}").asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkDoWith(text: String, manager: PsiManager): Boolean = {
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, "class a {\n" + text + "\n}").asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkTypeWith(typez: ScTypeElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = removeDummy("class a { x:" + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val value = !checkErrors(dummyFile)
    value
  }

  def checkAnyTypeWith(typez: ScTypeElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = removeDummy("class a { val x:" + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    val value = !checkErrors(dummyFile)
    value
  }

  def checkAnyWith(typez: PsiElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = removeDummy("class a { " + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def removeDummy(text: String): String = {
    replaceDummy(text, "")
  }

  def replaceDummy(text: String, to: String): String = {
    if (text.indexOf(DUMMY_IDENTIFIER) != -1) {
      text.replaceAll("\\w*" + DUMMY_IDENTIFIER,to)
    } else text
  }

  def checkNewWith(news: ScNewTemplateDefinition, additionText: String, manager: PsiManager): Boolean = {
    val newsText = news.getText
    val text = removeDummy("class a { " + newsText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkReplace(elem: PsiElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = elem.getText
    var text = "class a { " + typeText + "}"
    if (text.indexOf(DUMMY_IDENTIFIER) == -1) return false
    text = replaceDummy(text, " "+ additionText+ " ")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension,
      ScalaFileType.SCALA_FILE_TYPE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  private def checkErrors(elem: PsiElement): Boolean = {
    elem match {
      case _: PsiErrorElement => return true
      case _ =>
    }
    val iterator = elem.getChildren.iterator
    while (iterator.hasNext) {
      val child = iterator.next()
      if (checkErrors(child)) return true
    }
    false
  }

  /**
   * @param leaf Start PsiElement
   * @return (End PsiElement, ContainingFile.isScriptFile) 
   */
  def processPsiLeafForFilter(leaf: PsiElement): (PsiElement, Boolean) = Option(leaf) map {
    l => l.getContainingFile match {
      case scriptFile: ScalaFile if scriptFile.isScriptFile() => (leaf.getParent, true)
      case scalaFile: ScalaFile => (leaf, false)
      case _ => (null, false)
    }
  } getOrElse (null, false)
}