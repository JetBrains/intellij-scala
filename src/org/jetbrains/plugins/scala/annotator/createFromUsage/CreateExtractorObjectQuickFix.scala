package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.template.TemplateBuilder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTupleTypeElement, ScParameterizedTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * Nikolay.Tropin
 * 2014-07-31
 */
class CreateExtractorObjectQuickFix(ref: ScReferenceElement)
        extends CreateTypeDefinitionQuickFix(ref, "extractor object", Object) {

  override protected def afterCreationWork(clazz: ScTypeDefinition) = {
    addUnapplyMethod(clazz)
    super.afterCreationWork(clazz)
  }

  override protected def addMoreElementsToTemplate(builder: TemplateBuilder, clazz: ScTypeDefinition): Unit = {
    val method = clazz.members match {
      case Seq(fun: ScFunctionDefinition) => fun
      case _ => return
    }

    val Q_MARKS = "???"
    method.depthFirst.filterByType(classOf[ScReferenceExpression]).filter(_.getText == Q_MARKS)
            .foreach { qmarks =>
      builder.replaceElement(qmarks, Q_MARKS)
    }

    CreateFromUsageUtil.addParametersToTemplate(method, builder)

    method.returnTypeElement match {
      case Some(ScParameterizedTypeElement(_, Seq(tuple: ScTupleTypeElement))) => //Option[(A, B)]
        tuple.components.foreach(te => builder.replaceElement(te, te.getText))
      case Some(ScParameterizedTypeElement(_, args)) =>
        args.foreach(te => builder.replaceElement(te, te.getText))
      case _ =>
    }
  }

  private def addUnapplyMethod(clazz: ScTypeDefinition): Unit = {
    val types = CreateFromUsageUtil.patternArgs(ref).map(_.getType(TypingContext.empty).getOrAny)
    val typesText = types.map(_.canonicalText).mkString(", ")
    val resultTypeText = types.size match {
      case 0 => "Boolean"
      case 1 => s"Option[$typesText]"
      case _ => s"Option[($typesText)]"
    }
    val methodText = s"def unapply(x: Any): $resultTypeText = ???"
    val method = ScalaPsiElementFactory.createMethodFromText(methodText, clazz.getManager)
    clazz.addMember(method, None)
  }
}
