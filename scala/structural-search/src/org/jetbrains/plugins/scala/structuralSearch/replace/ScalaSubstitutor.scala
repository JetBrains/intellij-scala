package org.jetbrains.plugins.scala.structuralSearch.replace

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo
import com.intellij.structuralsearch.plugin.replace.impl.ParameterInfo
import com.intellij.structuralsearch.{MatchResult, StructuralSearchProfile, UnsupportedPatternException}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

import java.lang
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object ScalaSubstitutor {

  def handleBlock(sb: StringBuilder, info: ParameterInfo, res: MatchResult, result: lang.StringBuilder, replacementInfo: ReplacementInfo): Unit = {
    val element = info.getElement
    val it = res.getChildren.iterator()
    while ( {
      element.getParent match {
        case _ => sb.append(it.next.getMatchImage)
      }
      it.hasNext
    }) {
      sb.append(element.getPrevSibling.getText)
    }
  }

  def handleParameter(sb: StringBuilder, info: ParameterInfo, res: MatchResult, result: lang.StringBuilder,
                      replaceParameter: ScParameter,
                      paraContext: mutable.Map[String, ParameterInfo], profile: StructuralSearchProfile): Int = {
    val offset = eraseParameter(info, replaceParameter, result, profile)

    // now insert the correct version
    if res.isMultipleMatch then {
      val it = res.getChildren.asScala.iterator
      while ( {
        val mR = it.next()
        appendParameter(sb, mR, replaceParameter, profile)
        it.hasNext
      }) {
        sb.append(", ")
      }
    } else {
      appendParameter(sb, res, replaceParameter, profile)
    }
    offset
  }

  def handleNoSubParameter(info: ParameterInfo, parameter: ScParameter, result: lang.StringBuilder,
                           profile: StructuralSearchProfile): Int = {
    eraseParameter(info, parameter, result, profile)
  }

  private def eraseParameter(info: ParameterInfo, replaceParameter: ScParameter, result: lang.StringBuilder, profile: StructuralSearchProfile): Int = {
    // calculate end of parameter replacement pattern
    var max = info.getStartIndex
    // create template to get length of parameter pattern
    val template = TemplateManager.getInstance(replaceParameter.getProject).createTemplate("", "", replaceParameter.getText)
    val replacement = template.getTemplateText
    for (i <- 0 until template.getSegmentsCount) {
      if (template.getSegmentName(i) == profile.stripReplacementTypedVariableDecorations(replaceParameter.name)) {
        max = info.getStartIndex + (replacement.length - template.getSegmentOffset(i))
      }
    }
    val min = max - replacement.length
    // cut out pattern to get a blank field
    result.delete(min, max)

    min - info.getStartIndex
  }

  private def appendParameter(sb: StringBuilder, mR: MatchResult, replaceParameter: ScParameter, profile: StructuralSearchProfile): Unit = {
    val matchedParameter = mR.getMatch match {
      case para: ScParameter => para
      case _ => throw new UnsupportedPatternException("Parameter result element needs to be parameter")
    }
    val patternParameter = mR.getChildren.asScala.find(_.getName == "__parameter__pattern") match {
      case None => throw new UnsupportedPatternException("No search pattern found")
      case Some(pat) => pat.getMatch.asInstanceOf[ScParameter]
    }

    if (patternParameter.annotations.isEmpty && replaceParameter.annotations.isEmpty) {
      for (elem <- matchedParameter.annotations) {
        sb.append(elem.getText)
        sb.append(" ")
      }
    } else {
      for (replAnno <- replaceParameter.annotations) {
        if (appendAnnotation(sb, mR, replAnno, profile))
          sb.append(" ")
      }
    }

    sb.append(matchedParameter.getNameIdentifier.getText)

    replaceParameter.typeElement match {
      case Some(ty) =>
        if profile.isReplacementTypedVariable(ty.getText) then {
          val tyName = profile.stripReplacementTypedVariableDecorations(ty.getText)
          mR.getChildren.asScala.find(_.getName == tyName) match {
            case None =>
            case Some(subR) =>
              sb.append(": ")
              sb.append(subR.getMatchImage)
          }
        } else {
          sb.append(": ")
          sb.append(ty.getText)
        }
      case None =>
        if (patternParameter.typeElement.isEmpty)
          sb.append(matchedParameter.typeElement.map(": " + _.getText).getOrElse(""))
    }

    replaceParameter.getDefaultExpression match {
      case Some(default) =>
        if profile.isReplacementTypedVariable(default.getText) then {
          val initName = profile.stripReplacementTypedVariableDecorations(default.getText)
          mR.getChildren.asScala.find(_.getName == initName) match {
            case None =>
            case Some(subR) =>
              sb.append(" = ")
              sb.append(subR.getMatchImage)
          }
        } else {
          sb.append(" = ")
          sb.append(default.getText)
        }
      case None =>
        if (patternParameter.getDefaultExpression.isEmpty)
          sb.append(matchedParameter.getDefaultExpression.map(" = " + _.getText).getOrElse(""))
    }
  }

  def appendAnnotation(sb: StringBuilder, mR: MatchResult, replAnno: ScAnnotation, profile: StructuralSearchProfile): Boolean = {
    val annoName = replAnno.constructorInvocation.typeElement.getText
    if profile.isReplacementTypedVariable(annoName) then {
      mR.getChildren.asScala.find(_.getName == profile.stripReplacementTypedVariableDecorations(annoName)) match {
        case None => false
        case Some(subR) =>
          if (subR.isMultipleMatch) {
            val it = subR.getChildren.iterator()
            while ({
              sb.append("@")
              sb.append(it.next().getMatchImage)
              it.hasNext
            }) {
              sb.append(" ")
            }
            // compound match
          } else {
            sb.append("@")
            sb.append(subR.getMatchImage)
          }
          true
      }
    } else {
      sb.append("@")
      sb.append(annoName)
      true
    }
  }
}
