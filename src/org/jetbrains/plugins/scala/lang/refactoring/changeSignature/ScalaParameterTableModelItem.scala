package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.mutable.ListBuffer


/**
 * Nikolay.Tropin
 * 2014-08-29
 */
class ScalaParameterTableModelItem(parameter: ScalaParameterInfo,
                                   typeCodeFragment: ScalaCodeFragment,
                                   defaultValue: ScalaCodeFragment,
                                   var startsNewClause: Boolean = false)
        extends ParameterTableModelItemBase[ScalaParameterInfo](parameter, typeCodeFragment, defaultValue) {

  var typeText: String = Option(typeCodeFragment).map(_.getText).getOrElse("")

  def keywordsAndAnnotations = parameter.keywordsAndAnnotations

  override def isEllipsisType: Boolean = parameter.isRepeatedParameter

  def updateType(problems: ListBuffer[String] = ListBuffer()): Unit = {
    var trimmed = typeText.trim
    if (trimmed.endsWith("*")) {
      parameter.isRepeatedParameter = true
      trimmed = trimmed.dropRight(1).trim
    } else {
      parameter.isRepeatedParameter = false
    }

    if (typeText.isEmpty) {
      problems += RefactoringBundle.message("changeSignature.no.type.for.parameter", parameter.getName)
      return
    }

    val funArrow = ScalaPsiUtil.functionArrow(typeCodeFragment.getProject)
    val arrow = if (trimmed.startsWith("=>")) "=>" else if (trimmed.startsWith(funArrow)) funArrow else ""
    if (arrow != "") {
      parameter.isByName = true
      trimmed = trimmed.drop(arrow.length).trim
    } else {
      parameter.isByName = false
    }
    val typeElem = ScalaPsiElementFactory.createTypeElementFromText(trimmed, typeCodeFragment, typeCodeFragment.getLastChild)
    if (typeElem == null || typeElem.getType().isEmpty) {
      problems += s"Could not understand type $trimmed"
      parameter.scType = null
    }
    else {
      parameter.scType = typeElem.getType().getOrAny
    }
  }
}