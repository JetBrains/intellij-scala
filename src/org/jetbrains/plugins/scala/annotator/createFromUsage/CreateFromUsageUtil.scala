package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTupleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{Any => scTypeAny, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
/**
 * Nikolay.Tropin
 * 2014-07-31
 */
object CreateFromUsageUtil {

  def uniqueNames(names: Seq[String]) = {
    names.foldLeft(List[String]()) { (r, h) =>
      (h #:: Stream.from(1).map(h + _)).find(!r.contains(_)).get :: r
    }.reverse
  }
  
  def nameByType(tp: ScType) = NameSuggester.suggestNamesByType(tp).headOption.getOrElse("value")
  
  def nameAndTypeForArg(arg: PsiElement): (String, ScType) = arg match {
    case ref: ScReferenceExpression => (ref.refName, ref.getType().getOrAny)
    case expr: ScExpression =>
      val tp = expr.getType().getOrAny
      (nameByType(tp), tp)
    case bp: ScBindingPattern if !bp.isWildcard => (bp.name, bp.getType(TypingContext.empty).getOrAny)
    case p: ScPattern =>
      val tp: ScType = p.getType(TypingContext.empty).getOrAny
      (nameByType(tp), tp)
    case _ => ("value", scTypeAny)
  }
  
  def paramsText(args: Seq[PsiElement]) = {
    val (names, types) = args.map(nameAndTypeForArg).unzip
    (uniqueNames(names), types).zipped.map((name, tpe) => s"$name: ${tpe.canonicalText}").mkString("(", ", ", ")")
  }

  def parametersText(ref: ScReferenceElement) = {
    ref.getParent match {
      case p: ScPattern =>
        paramsText(patternArgs(p))
      case _ =>
        val fromConstrArguments = PsiTreeUtil.getParentOfType(ref, classOf[ScConstructor]) match {
          case ScConstructor(simple: ScSimpleTypeElement, args) if ref.getParent == simple => args
          case ScConstructor(pt: ScParameterizedTypeElement, args) if ref.getParent == pt.typeElement => args
          case _ => Seq.empty
        }
        fromConstrArguments.map(argList => paramsText(argList.exprs)).mkString
    }
  }

  def patternArgs(pattern: ScPattern): Seq[ScPattern] = {
    pattern match {
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

  def addQmarksToTemplate(elem: PsiElement, builder: TemplateBuilder): Unit = {
    val Q_MARKS = "???"
    elem.depthFirst.filterByType(classOf[ScReferenceExpression]).filter(_.getText == Q_MARKS)
            .foreach { qmarks =>
      builder.replaceElement(qmarks, Q_MARKS)
    }
  }

  def addUnapplyResultTypesToTemplate(fun: ScFunction, builder: TemplateBuilder): Unit = {
    fun.returnTypeElement match {
      case Some(ScParameterizedTypeElement(_, Seq(tuple: ScTupleTypeElement))) => //Option[(A, B)]
        tuple.components.foreach(te => builder.replaceElement(te, te.getText))
      case Some(ScParameterizedTypeElement(_, args)) =>
        args.foreach(te => builder.replaceElement(te, te.getText))
      case _ =>
    }
  }

  def positionCursor(element: PsiElement): Editor = {
    val offset = element.getTextRange.getEndOffset
    val project = element.getProject
    val descriptor = new OpenFileDescriptor(project, element.getContainingFile.getVirtualFile, offset)
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
  }

  def unapplyMethodText(pattern: ScPattern) = {
    val pType = pattern.expectedType.getOrElse(scTypeAny)
    val pName = nameByType(pType)
    s"def unapply($pName: ${pType.canonicalText}): ${unapplyMethodTypeText(pattern)} = ???"
  }

  def unapplyMethodTypeText(pattern: ScPattern) = {
    val types = CreateFromUsageUtil.patternArgs(pattern).map(_.getType(TypingContext.empty).getOrAny)
    val typesText = types.map(_.canonicalText).mkString(", ")
    types.size match {
      case 0 => "Boolean"
      case 1 => s"Option[$typesText]"
      case _ => s"Option[($typesText)]"
    }
  }
}

object InstanceOfClass {
  def unapply(elem: PsiElement): Option[PsiClass] = elem match {
    case ScExpression.Type(TypeAsClass(psiClass)) => Some(psiClass)
    case ResolvesTo(typed: ScTypedDefinition) =>
      typed.getType().toOption match {
        case Some(TypeAsClass(psiClass)) => Some(psiClass)
        case _ => None
      }
    case _ => None
  }
}

object TypeAsClass {
  def unapply(scType: ScType): Option[PsiClass] = scType match {
    case ScType.ExtractClass(aClass) => Some(aClass)
    case t: ScType => ScType.extractDesignatorSingletonType(t).flatMap(ScType.extractClass(_, None))
    case _ => None
  }
}
