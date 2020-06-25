package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import scala.collection.mutable

/**
 * Generates default scaladoc template for some commentOwner after typing `/ * *` + Enter.
 *
 * NOTE: has nothing to do with Indexes Stubs!
 */
object ScalaDocStubGenerator {

  def createScalaDocStub(commentOwner: PsiDocCommentOwner): String = {
    if (!commentOwner.getContainingFile.isInstanceOf[ScalaFile])
      return ""

    val buffer = new java.lang.StringBuilder
    val leadingAsterisks = "* "

    val inheritedParams = mutable.HashMap.apply[String, PsiDocTag]()
    val inheritedTParams = mutable.HashMap.apply[String, PsiDocTag]()

    import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing._

    def registerInheritedParam(allParams: mutable.HashMap[String, PsiDocTag], param: PsiDocTag): Unit =
      if (!allParams.contains(param.getValueElement.getText)) {
        allParams.put(param.getValueElement.getText, param)
      }

    def processProbablyJavaDocCommentWithOwner(owner: PsiDocCommentOwner): Unit = {
      owner.getDocComment match {
        case scalaComment: ScDocComment =>
          for (docTag <- scalaComment.findTagsByName(Set(PARAM_TAG, TYPE_PARAM_TAG).contains _))
            docTag.name match {
              case PARAM_TAG      => registerInheritedParam(inheritedParams, docTag)
              case TYPE_PARAM_TAG => registerInheritedParam(inheritedTParams, docTag)
            }
        case javaComment: PsiDocComment =>
          for (paramTag <- javaComment.findTagsByName("param"))
            if (paramTag.getValueElement.getText.startsWith("<"))
              registerInheritedParam(inheritedTParams, paramTag)
            else
              registerInheritedParam(inheritedParams, paramTag)
        case _ =>
      }
    }

    def appendParamText(buffer: java.lang.StringBuilder, paramText: String): Unit = {
      val start = paramText.indexWhere(!_.isWhitespace) // tag include leading whitespaces
      val end = paramText.lastIndexOf("\n") + 1
      buffer.append(leadingAsterisks).append(paramText, start, end)
    }

    def processParams(owner: ScParameterOwner): Unit =
      for (param <- owner.parameters)
        if (inheritedParams.contains(param.name)) {
          val paramText = inheritedParams(param.name).getText
          appendParamText(buffer, paramText)
        } else {
          buffer.append(leadingAsterisks).append(PARAM_TAG).append(" ").append(param.name).append("\n")
        }

    def processTypeParams(owner: ScTypeParametersOwner): Unit =
      for (tparam <- owner.typeParameters)
        if (inheritedTParams.contains(tparam.name)) {
          val paramText = inheritedTParams(tparam.name).getText
          appendParamText(buffer, paramText)
        } else if (inheritedTParams.contains("<" + tparam + ">")) {
          val paramTag = inheritedTParams("<" + tparam.name + ">")
          val descriptionText =
            paramTag.getText.substring(paramTag.getValueElement.getTextOffset + paramTag.getValueElement.getTextLength)
          val parameterName = paramTag.getValueElement.getText

          buffer
            .append(leadingAsterisks)
            .append("@").append(paramTag.name)
            .append(" ")
            .append(parameterName.substring(1, parameterName.length - 1))
            .append(" ")
            .append(descriptionText.substring(0, descriptionText.lastIndexOf("\n") + 1))
        } else {
          buffer.append(leadingAsterisks).append(TYPE_PARAM_TAG).append(" ").append(tparam.name).append("\n")
        }

    commentOwner match {
      case clazz: ScClass =>
        clazz.getSupers.foreach(processProbablyJavaDocCommentWithOwner)
        processParams(clazz)
        processTypeParams(clazz)
      case function: ScFunction =>
        val parents = function.findSuperMethods()
        var returnTag: String = null
        val needReturnTag = function.getReturnType != null && !function.hasUnitResultType

        for (parent <- parents) {
          processProbablyJavaDocCommentWithOwner(parent)

          if (needReturnTag) {
            var inherRetTag: PsiDocTag = null
            parent.getDocComment match {
              case scComment: ScDocComment => inherRetTag = scComment.findTagByName("@return")
              case comment: PsiDocComment  => inherRetTag = comment.findTagByName("return")
              case _                       =>
            }
            if (inherRetTag != null) {
              returnTag = inherRetTag.getText.substring(0, inherRetTag.getText.lastIndexOf("\n") + 1)
            }
          }
        }

        processParams(function)
        processTypeParams(function)

        for (annotation <- function.annotations if annotation.annotationExpr.getText.startsWith("throws")) {
          buffer.append(leadingAsterisks).append(MyScaladocParsing.THROWS_TAG).append(" ")
          for {
            arg <- annotation.constructorInvocation.args
            headExpr <- arg.exprs.headOption
            headType <- headExpr.`type`()
          } headType match {
            case ParameterizedType(_, args) =>
              for {
                a <- args.headOption
                clazz <- a.extractClass
              } buffer.append(clazz.qualifiedName)
            case _ =>
          }

          buffer.append(" \n")
        }

        if (returnTag != null) {
          buffer.append(leadingAsterisks).append(returnTag)
        } else if (needReturnTag) {
          buffer.append(leadingAsterisks).append(MyScaladocParsing.RETURN_TAG).append(" \n")
        }
      case scType: ScTypeAlias =>
        val parents = ScalaPsiUtil.superTypeMembers(scType)
        for (parent <- parents if parent.isInstanceOf[ScTypeAlias]) {
          processProbablyJavaDocCommentWithOwner(parent.asInstanceOf[ScTypeAlias])
        }
        processTypeParams(scType)
      case traitt: ScTrait =>
        val parents = traitt.getSupers

        for (parent <- parents) {
          processProbablyJavaDocCommentWithOwner(parent)
        }
        processTypeParams(traitt)
      case _ =>
    }

    buffer.toString
  }

}
