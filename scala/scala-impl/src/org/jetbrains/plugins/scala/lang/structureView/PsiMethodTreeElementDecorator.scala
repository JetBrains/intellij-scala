package org.jetbrains.plugins.scala.lang.structureView

import java.util.regex.Pattern

import com.intellij.ide.structureView.impl.java.PsiMethodTreeElement
import com.intellij.psi.PsiMethod

/**
  * @author Pavel Fatin
  */
private class PsiMethodTreeElementDecorator(method: PsiMethod, inherited: Boolean) extends PsiMethodTreeElement(method, inherited) {
  override def getPresentableText: String =
    PsiMethodTreeElementDecorator.asScalaType(super.getPresentableText)
}

private object PsiMethodTreeElementDecorator {
  private val PrimitiveType = Pattern.compile("\\b(?:void|boolean|byte|char|short|int|long|float|double)\\b")

  private val Transformations = Seq(
    "(\\w+)\\[\\]" -> "Array[$1]",
    "\\.\\.\\." -> "*",
    "\\<" -> "[",
    "\\>" -> "]",
    "\\?" -> "_",
    "\\bextends\\b" -> "<:",
    "\\bsuper\\b" -> ">:"
  ).map {
    case (regex, replacement) => (Pattern.compile(regex), replacement)
  }

  private def asScalaType(javaType: String) =
    applyTransformationsTo(replacePrimitiveTypesIn(javaType))

  private def replacePrimitiveTypesIn(text: String) = {
    val matcher = PrimitiveType.matcher(text)

    val buffer = new StringBuffer()

    while (matcher.find()) {
      val replacement = matcher.group() match {
        case "void" => "Unit"
        case s => s.capitalize
      }
      matcher.appendReplacement(buffer, replacement)
    }

    matcher.appendTail(buffer)

    buffer.toString
  }

  private def applyTransformationsTo(javaType: String) = Transformations.foldLeft(javaType) {
    case (acc, (regex, replacement)) => regex.matcher(acc).replaceAll(replacement)
  }
}