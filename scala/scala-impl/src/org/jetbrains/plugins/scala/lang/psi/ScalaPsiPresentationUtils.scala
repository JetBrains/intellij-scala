package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi._
import com.intellij.psi.util.{PsiFormatUtil, PsiFormatUtilBase}
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScAnnotation, ScAnnotationsHolder, ScConstructorInvocation}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

// TODO 1: unify methods styles
// TODO 2: unify with org.jetbrains.plugins.scala.lang.psi.PresentationUtil
// TODO 3: unify with org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
// TODO 4: unify with org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
// TODO 5: unify with com.intellij.psi.util.PsiFormatUtil
// TODO 6: after implementation is stable,
//  investigate performance and check whether passing same buffer everywhere helps
object ScalaPsiPresentationUtils {

  trait TypeRenderer {
    def render(typ: ScType): String
    final def apply(typ: ScType): String = render(typ)
  }

  def renderParameters(elem: ScParameterOwner, spaces: Int)
                      (implicit typeToString: TypeRenderer): String =
    elem.allClauses.map(renderParameterClause(_, spaces)).mkString("\n")

  private def renderParameterClause(elem: ScParameterClause, spaces: Int)
                                   (implicit typeToString: TypeRenderer): String = {
    val buffer: StringBuilder = new StringBuilder(" ")
    buffer.append(" " * spaces)

    val strings = elem.parameters.map(renderParameter(_, memberModifiers = false))
    val prefix = if (elem.isImplicit) "(implicit " else "("
    val separator = if (spaces < 0) ", " else ",\n" + buffer
    val suffix = ")"
    strings.mkString(prefix, separator, suffix)
  }

  def renderParameter(param: ScParameter, escape: Boolean = true, memberModifiers: Boolean = true)
                     (implicit typeToString: TypeRenderer): String = {
    val member = param match {
      case c: ScClassParameter => c.isClassMember
      case _ => false
    }
    val buffer: StringBuilder = new StringBuilder
    // When parameter is val, var, or case class val, annotations are related to member, not to parameter
    if (!member || memberModifiers) {
      buffer.append(renderAnnotations(param, ' ', escape))
    }
    if (memberModifiers) {
      param match {
        case cl: ScClassParameter => buffer.append(renderModifiers(cl))
        case _ =>
      }
      buffer.append(param match {
        case c: ScClassParameter if c.isVal => "val "
        case c: ScClassParameter if c.isVar => "var "
        case _ => ""
      })
    }
    buffer.append(if (escape) escapeHtml(param.name) else param.name)

    buffer.append(typeAnnotationText(param))

    buffer.toString()
  }

  def renderAnnotations(elem: ScAnnotationsHolder, sep: Char = '\n', escape: Boolean = true)
                       (implicit typeToString: TypeRenderer): String = {
    val buffer: StringBuilder = new StringBuilder
    for (ann <- elem.annotations) {
      buffer.append(renderAnnotation(ann) + sep)
    }
    buffer.toString()
  }

  private def renderAnnotation(elem: ScAnnotation)
                              (implicit typeToString: TypeRenderer): String = {
    val res = new StringBuilder("@")
    val constrInvocation: ScConstructorInvocation = elem.constructorInvocation
    res.append(typeToString(constrInvocation.typeElement.`type`().getOrAny))

    val attrs = elem.annotationExpr.getAnnotationParameters
    if (attrs.nonEmpty) res append attrs.map(_.getText).mkString("(", ", ", ")")

    res.toString()
  }

  def renderModifiers(elem: ScModifierListOwner): String = {
    val buffer: StringBuilder = new StringBuilder

    for {
      modifier <- elem.getModifierList.accessModifier

      prefix = if (modifier.isPrivate) PsiModifier.PRIVATE
      else PsiModifier.PROTECTED

      suffix = if (modifier.isThis) "[this]"
      else accessQualifier(modifier)
    } buffer.append(prefix)
      .append(" ")
      .append(suffix)

    val modifiers = Array("abstract", "final", "sealed", "implicit", "lazy", "override")
    for (modifier <- modifiers if elem.hasModifierPropertyScala(modifier)) buffer.append(modifier + " ")
    buffer.toString()
  }

  private def accessQualifier(x: ScAccessModifier): String = x.getReference match {
    case null => ""
    case ref => ref.resolve match {
      case clazz: PsiClass => "[<a href=\"psi_element://" +
        escapeHtml(clazz.qualifiedName) + "\"><code>" +
        (x.idText match {
          case Some(text) => text
          case None => ""
        }) + "</code></a>]"
      case pack: PsiPackage => "[" + escapeHtml(pack.getQualifiedName) + "]"
      case _ => x.idText match {
        case Some(text) => "[" + text + "]"
        case None => ""
      }
    }
  }

  def typeAnnotationText(elem: ScTypedDefinition)
                        (implicit typeToString: TypeRenderer): String = {
    val typ = elem match {
      case fun: ScFunction => fun.returnType.getOrAny
      case _               => elem.`type`().getOrAny
    }
    val typeText = typeToString(typ)
    val typeTextFixed = elem match {
      case param: ScParameter => decoratedParameterType(param, typeText)
      case _                  => typeText
    }
    s": $typeTextFixed"
  }

  private def decoratedParameterType(param: ScParameter, typeText: String): String = {
    val buffer = StringBuilder.newBuilder

    if (param.isCallByNameParameter) {
      val arrow = ScalaPsiUtil.functionArrow(param.getProject)
      buffer.append(s"$arrow ")
    }

    buffer.append(typeText)

    if (param.isRepeatedParameter) buffer.append("*")

    if (param.isDefaultParam) {
      buffer.append(" = ")
      param.getDefaultExpressionInSource match {
        case Some(expr) =>
          val text: String = expr.getText.replace(" /* compiled code */ ", "")
          val cutTo = 20
          buffer.append(text.substring(0, text.length.min(cutTo)))
          if (text.length > cutTo) buffer.append("...")
        case None => buffer.append("...")
      }
    }
    buffer.toString()
  }

  // TODO: unify with renderParameters
  def renderParametersAsString(x: ScParameters, short: Boolean, subst: ScSubstitutor): String = {
    val buffer = StringBuilder.newBuilder
    for (child <- x.clauses) {
      buffer.append("(")
      renderParametersAsString(child, short, subst)(buffer)
      buffer.append(")")
    }
    buffer.result
  }

  private def renderParametersAsString(x: ScParameterClause, short: Boolean, subst: ScSubstitutor)
                                      (buffer: StringBuilder): Unit = {
    var isFirst = true
    for (param <- x.parameters) {
      if (isFirst)
        isFirst = false
      else
        buffer.append(", ")
      if (short) {
        param.paramType match {
          case Some(pt) => buffer.append(pt.getText).append(", ")
          case None => buffer.append("AnyRef")
        }
      }
      else {
        buffer.append(param.name + ": ")
        val typez = subst(param.`type`().getOrNothing)
        buffer.append(typez.presentableText(x) + (if (param.isRepeatedParameter) "*" else ""))
      }
    }
  }

  def getMethodPresentableText(
    method: PsiMethod,
    subst: ScSubstitutor = ScSubstitutor.empty
  ): String =
    method match {
      case method: ScFunction =>
        getMethodPresentableText(method, fast = false, subst)
      case _ =>
        getJavaMethodPresentableText(method, subst)
    }

  def getMethodPresentableText(
    function: ScFunction,
    fast: Boolean,
    subst: ScSubstitutor
  ): String = {
    val buffer: StringBuilder = new StringBuilder

    buffer.append(if (function.isConstructor) "this" else function.name)
    buffer.append(function.typeParametersClause.fold("")(_.getTextByStub)) // (!) text taken from stub
    buffer.append(Option(function.paramClauses).fold("")(renderParametersAsString(_, fast, subst)))
    buffer.append(typeAnnotationText2(function, fast, subst))

    buffer.toString
  }

  // TODO: unify with typeAnnotationText
  private def typeAnnotationText2(function: ScFunction, fast: Boolean, subst: ScSubstitutor): String =
    if (fast) {
      val psiText = function.returnTypeElement.map(_.getText)
      psiText.fold("")(": " + _)
    } else {
      val typText = try {
        subst(function.returnType.getOrAny).presentableText(function)
      } catch {
        case _: IndexNotReadyException => "NoTypeInfo"
      }
      s": $typText"
    }

  private def getJavaMethodPresentableText(
    method: PsiMethod,
    subst: ScSubstitutor
  ): String = {
    val javaSubstitutor = ScalaPsiUtil.getPsiSubstitutor(subst)(method.elementScope)
    import PsiFormatUtilBase._
    val pramOptions = SHOW_NAME | SHOW_TYPE | TYPE_AFTER
    PsiFormatUtil.formatMethod(method, javaSubstitutor, pramOptions | SHOW_PARAMETERS, pramOptions)
  }
}
