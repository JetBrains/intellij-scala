package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.refactoring._

final case class InnerClassSettings(needClass: Boolean, className: String, outputs: Array[ExtractMethodOutput], isCase: Boolean) {
  def classText(canonTextForTypes: Boolean): String = {
    def paramText(output: ExtractMethodOutput) = {
      val tp = output.returnType
      implicit val ctx: TypePresentationContext = output.fromElement
      val typeText = if (canonTextForTypes) tp.canonicalCodeText(ctx) else tp.codeText
      val typed = ScalaExtractMethodUtils.typedName(output.paramName, typeText)(output.fromElement.getProject)
      if (isCase) typed else s"val $typed"
    }
    val paramsText = outputs.map(paramText).mkString("(", ", ", ")")
    s"${if (isCase) "case " else ""}class $className$paramsText"
  }
}
