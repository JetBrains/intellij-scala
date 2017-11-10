package org.jetbrains.plugins.scala.lang.macros.evaluator.impl
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType

object ShapelessMaterializeLabelledGeneric extends ScalaMacroTypeable with ShapelessUtils {

  private def mkParamTp(p: ScClassParameter): String = {
    val tp = p.`type`().getOrAny.presentableText
    val paramName = p.name
    s"$tp with $fqKeyTag[Symbol with $fqTagged[{type $paramName}], $tp]"
  }

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    if (context.expectedType.isEmpty) return None
    val targetClass = extractTargetClass(context)
    val fields      = targetClass.flatMap(_.constructor.map(_.parameters)).getOrElse(Seq.empty)
    val reprTpStr   = fields.foldRight(fqHNil)((p, suffix) => s"$fqColonColon[${mkParamTp(p)}, $suffix]")
    val genericStr  = s"$fqDefSymLab[${targetClass.map(_.name).getOrElse("Any")}]"
    ScalaPsiElementFactory.createTypeFromText(s"$genericStr{type Out = $reprTpStr}", context.place, null)
  }
}
