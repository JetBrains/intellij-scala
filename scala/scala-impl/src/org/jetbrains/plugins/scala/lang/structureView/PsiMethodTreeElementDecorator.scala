package org.jetbrains.plugins.scala.lang.structureView

import java.util.regex.Pattern

import com.intellij.ide.structureView.impl.java.PsiMethodTreeElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.structureView.PsiMethodTreeElementDecorator.replacePrimitiveTypesIn

/**
  * @author Pavel Fatin
  */
private class PsiMethodTreeElementDecorator(method: PsiMethod, inherited: Boolean) extends PsiMethodTreeElement(method, inherited) {
  override def getPresentableText: String =
    replacePrimitiveTypesIn(super.getPresentableText)
      .replaceAll("(\\w+)\\[\\]", "Array[$1]")
      .replaceAll("\\.\\.\\.", "*")
      .replaceAll("\\<", "[")
      .replaceAll("\\>", "]")
      .replaceAll("\\?", "_")
      .replaceAll("\\bextends\\b", "<:")
      .replaceAll("\\bsuper\\b", ">:")
}

private object PsiMethodTreeElementDecorator {
  private val PrimitiveType = Pattern.compile("\\b(?:void|boolean|byte|char|short|int|long|float|double)\\b")

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
}