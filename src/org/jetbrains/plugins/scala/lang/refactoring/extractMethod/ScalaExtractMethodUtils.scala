package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */

object ScalaExtractMethodUtils {
  def createMethodFromSettings(settings: ScalaExtractMethodSettings): ScFunction = {
    var builder: StringBuilder = new StringBuilder
    builder.append(settings.visibility)
    builder.append("def ").append(settings.methodName)
    if (settings.paramNames.length != 0) {
      builder.append(settings.paramNames.zip(settings.paramTypes.map(ScType.presentableText(_))).map(p =>
        p._1 + ": " + p._2).mkString("(", ", ", ")"))
    }
    builder.append(": ")
    if (settings.returnTypes.length == 1) builder.append(ScType.presentableText(settings.returnTypes.apply(0)))
    else if (settings.returnTypes.length == 0) builder.append("Unit")
    else builder.append(settings.returnTypes.map(ScType.presentableText(_)).mkString("(", ", ", ")"))
    builder.append(" = {\n")
    for (element <- settings.elements) {
      builder.append(element.getText)
    }
    builder.append("\n}")
    ScalaPsiElementFactory.createMethodFromText(builder.toString, settings.elements.apply(0).getManager)
  }
}