package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
/**
 * Nikolay.Tropin
 * 2014-07-31
 */
object CreateFromUsageUtil {

  def argsTextByTypes(types: Seq[ScType]): String = {
    val names = types.map(NameSuggester.suggestNamesByType(_).headOption.getOrElse("value"))
    val uniqueNames = names.foldLeft(List[String]()) { (r, h) =>
      (h #:: Stream.from(1).map(h + _)).find(!r.contains(_)).get :: r
    }
    (uniqueNames.reverse, types).zipped.map((name, tpe) => s"$name: ${tpe.canonicalText}").mkString("(", ", ", ")")
  }

  def argsText(args: Seq[ScExpression]) = {
    val types = args.map(_.getType().getOrAny)
    argsTextByTypes(types)
  }

  def argumentsText(ref: ScReferenceElement) = {
    if (ref.getParent.isInstanceOf[ScPattern]) {
      val types = patternArgs(ref).map(_.getType(TypingContext.empty).getOrAny)
      argsTextByTypes(types)
    }
    else {
      val fromConstrArguments = PsiTreeUtil.getParentOfType(ref, classOf[ScConstructor]) match {
        case ScConstructor(simple: ScSimpleTypeElement, args) if ref.getParent == simple => args
        case ScConstructor(pt: ScParameterizedTypeElement, args) if ref.getParent == pt.typeElement => args
        case _ => Seq.empty
      }
      fromConstrArguments.map(argList => argsText(argList.exprs)).mkString
    }
  }

  def patternArgs(ref: ScReferenceElement): Seq[ScPattern] = {
    ref.getParent match {
      case cp: ScConstructorPattern => cp.args.patterns
      case inf: ScInfixPattern => inf.leftPattern +: inf.rightPattern.toSeq
      case _ => Seq.empty
    }
  }

  def addParametersToTemplate(elem: PsiElement, builder: TemplateBuilder): Unit = {
    elem.depthFirst.filterByType(classOf[ScParameter]).foreach { parameter =>
      val id = parameter.getNameIdentifier
      builder.replaceElement(id, id.getText)

      parameter.paramType.foreach { it =>
        builder.replaceElement(it, it.getText)
      }
    }
  }

  def addTypeParametersToTemplate(elem: PsiElement, builder: TemplateBuilder): Unit = {
    elem.depthFirst.filterByType(classOf[ScTypeParam]).foreach { tp =>
      builder.replaceElement(tp.nameId, tp.name)
    }
  }
}
