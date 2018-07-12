package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionUtil, JavaCompletionUtil, PrefixMatcher}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Computable, Key}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.StringsExt
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.{isIdentifier, isKeyword}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

/**
* User: Alexander Podkhalyuzin
* Date: 21.05.2008.
*/

object ScalaCompletionUtil {

  import ScalaTokenTypes._

  val PREFIX_COMPLETION_KEY: Key[Boolean] = Key.create("prefix.completion.key")

  def completeThis(ref: ScReferenceExpression): Boolean =
    ref.qualifier.isEmpty || (ref.getParent match {
      case e: ScSugarCallExpr => e.operation != ref
      case _ => true
    })

  def shouldRunClassNameCompletion(dummyPosition: PsiElement,
                                   prefixMatcher: PrefixMatcher,
                                   checkInvocationCount: Boolean = true)
                                  (implicit parameters: CompletionParameters): Boolean = {
    if (checkInvocationCount && parameters.getInvocationCount < 2) return false

    if (dummyPosition.getNode.getElementType == tIDENTIFIER) {
      dummyPosition.getParent match {
        case ref: ScReferenceElement if ref.qualifier.isDefined => return false
        case _ =>
      }
    }

    if (checkInvocationCount && parameters.getInvocationCount >= 2) return true

    val prefix = prefixMatcher.getPrefix
    prefix.nonEmpty && prefix.charAt(0).isUpper
  }

  def anonymousFunctionText(types: Seq[ScType], braceArgs: Boolean)
                           (typeText: ScType => String = _.presentableText)
                           (implicit project: Project): String = {
    val buffer = StringBuilder.newBuilder

    if (braceArgs) buffer.append(kCASE).append(" ")

    val suggester = new NameSuggester.UniqueNameSuggester("x")
    val names = types.map(suggester)

    val parametersText = names.zip(types).map {
      case (name, scType) => name + tCOLON + " " + typeText(scType)
    }.commaSeparated(parenthesize = names.size != 1 || !braceArgs)

    buffer.append(parametersText)

    if (project != null) {
      buffer.append(" ")
        .append(ScalaPsiUtil.functionArrow)
    }

    buffer.toString()
  }

  def getLeafByOffset(offset: Int, element: PsiElement): PsiElement = {
    if (offset < 0) {
      return null
    }
    var candidate: PsiElement = element.getContainingFile
    if (candidate == null || candidate.getNode == null) return null
    while (candidate.getNode.getChildren(null).nonEmpty) {
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
      case _: ScReferenceExpression =>
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
      leaf.getPrevSibling.getPrevSibling.getNode.getElementType != kDEF) &&
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
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkElseWith(text: String, manager: PsiManager): Boolean = {
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, "class a {\n" + text + "\n}").asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkDoWith(text: String, manager: PsiManager): Boolean = {
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, "class a {\n" + text + "\n}").asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkTypeWith(typez: ScTypeElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = removeDummy("class a { x:" + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    val value = !checkErrors(dummyFile)
    value
  }

  def checkAnyTypeWith(typez: ScTypeElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = removeDummy("class a { val x:" + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    val value = !checkErrors(dummyFile)
    value
  }

  def checkAnyWith(typez: PsiElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = typez.getText
    val text = removeDummy("class a { " + typeText + " " + additionText + "}")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
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
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    !checkErrors(dummyFile)
  }

  def checkReplace(elem: PsiElement, additionText: String, manager: PsiManager): Boolean = {
    val typeText = elem.getText
    var text = "class a { " + typeText + "}"
    if (text.indexOf(DUMMY_IDENTIFIER) == -1) return false
    text = replaceDummy(text, " "+ additionText+ " ")
    val DUMMY = "dummy."
    val dummyFile = PsiFileFactory.getInstance(manager.getProject).
      createFileFromText(DUMMY + ScalaFileType.INSTANCE.getDefaultExtension,
        ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
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
      case scriptFile: ScalaFile if scriptFile.isScriptFile => (leaf.getParent, true)
      case _: ScalaFile => (leaf, false)
      case _ => (null, false)
    }
  } getOrElse (null, false)


  def getDummyIdentifier(offset: Int, file: PsiFile): String = {
    def isOpChar(c: Char): Boolean = isIdentifier(s"+$c")

    val element = file.findElementAt(offset)
    val ref = file.findReferenceAt(offset)
    if (element != null && ref != null) {
      val text = ref match {
        case ref: PsiElement => ref.getText
        case ref: PsiReference => ref.getElement.getText //this case for anonymous method in ScAccessModifierImpl
      }
      val id = if (isOpChar(text(text.length - 1))) {
        "+++++++++++++++++++++++"
      } else {
        val rest = ref match {
          case ref: PsiElement => text.substring(offset - ref.getTextRange.getStartOffset + 1)
          case ref: PsiReference =>
            val from = offset - ref.getElement.getTextRange.getStartOffset + 1
            if (from < text.length && from >= 0) text.substring(from) else ""
        }
        dummyIdentifier(rest)
      }

      if (ref.getElement != null &&
        ref.getElement.getPrevSibling != null &&
        ref.getElement.getPrevSibling.getNode.getElementType == tSTUB) id + "`" else id
    } else {
      if (element != null && element.getNode.getElementType == tSTUB) {
        CompletionUtil.DUMMY_IDENTIFIER_TRIMMED + "`"
      } else {
        Option(file.findElementAt(offset + 1))
          .map(_.getText)
          .map(dummyIdentifier)
          .getOrElse(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
      }
    }
  }

  private def dummyIdentifier(string: String): String =
    if (isKeyword(string)) CompletionUtil.DUMMY_IDENTIFIER
    else CompletionUtil.DUMMY_IDENTIFIER_TRIMMED

  def isTypeDefiniton(position: PsiElement): Boolean =
    Option(PsiTreeUtil.getParentOfType(position, classOf[ScTypeElement])).isDefined

  def isExcluded(clazz: PsiClass): Boolean = {
    ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
      def compute: Boolean = {
        JavaCompletionUtil.isInExcludedPackage(clazz, false)
      }
    })
  }

}
