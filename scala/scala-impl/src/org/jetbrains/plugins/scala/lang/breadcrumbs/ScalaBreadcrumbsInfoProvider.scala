package org.jetbrains.plugins.scala.lang.breadcrumbs

import com.intellij.lang.Language
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
  * User: Dmitry.Naydanov
  * Date: 23.06.16.
  */
class ScalaBreadcrumbsInfoProvider extends BreadcrumbsProvider {
  override def getElementTooltip(e: PsiElement): String = {
    import ScalaBreadcrumbsInfoProvider.MyTextRepresentationUtil._
    
    e match {
      case templ: ScTemplateDefinition => getTemplateDefTooltip(templ)
      case fun: ScFunction => getFunctionTooltip(fun)
      case funExpr: ScFunctionExpr => getFunctionTooltip(funExpr)
      case member: ScMember => getMemberTooltip(member)
      case caseClause: ScCaseClause => getCaseClauseTooltip(caseClause)
      case _ => "?"
    }
  }

  override def getElementInfo(e: PsiElement): String = {
    import ScalaBreadcrumbsInfoProvider.MyTextRepresentationUtil._
    
    e match {
      case newDef: ScNewTemplateDefinition => describeNewTemplate(newDef)
      case clazz: ScTemplateDefinition => clazz.name
      case funExpr: ScFunctionExpr => describeFunction(funExpr)
      case fun: ScFunction => describeFunction(fun)
      case member: ScMember => describeMember(member)
      case caseClause: ScCaseClause => describeCaseClause(caseClause)
      case expr: ScExpression => describeExpression(expr)
      case named: ScNamedElement => named.name
      case other =>
        println("Error: " + other.getClass + "   " + other.getText)
        ""
    }
  }

  override def acceptElement(e: PsiElement): Boolean = {
    val settings = ScalaProjectSettings.getInstance(e.getProject)
    
    e match {
      case _: ScTemplateDefinition => settings.isBreadcrumbsClassEnabled
      case _: ScFunctionExpr => settings.isBreadcrumbsLambdaEnabled
      case _: ScFunction => settings.isBreadcrumbsFunctionEnabled
      case _: ScCaseClause | _: ScMatch => settings.isBreadcrumbsMatchEnabled
      case _: ScMember => settings.isBreadcrumbsValDefEnabled
      case _: ScIf | _: ScWhile | _: ScDo => settings.isBreadcrumbsIfDoWhileEnabled
      case _ => false
    }
  }

  override def getLanguages: Array[Language] = ScalaBreadcrumbsInfoProvider.SCALA_LANG
}

object ScalaBreadcrumbsInfoProvider {
  private val PAR_HOLDER = "(...)"
  
  val SCALA_LANG: Array[Language] = Array[Language](ScalaLanguage.INSTANCE)
  
  val MAX_TEXT_LENGTH = 150
  val MAX_STRING_LENGTH = 25
  
  object MyTextRepresentationUtil {
    private def limitString(s: String, stub: String = PAR_HOLDER) = if (s == null) "" else if (s.length < MAX_STRING_LENGTH) s else stub
    private def limitText(txt: String, stub: String = "...", limit: Int = MAX_TEXT_LENGTH) = {
      if (txt == null) "" else if (txt.length < MAX_TEXT_LENGTH) txt else txt.substring(0, MAX_TEXT_LENGTH - 1 - stub.length) + stub
    }
    
    def getSignature(el: Option[ScNamedElement], parameters: Iterable[ScParameter], tpe: Option[ScType], needTpe: Boolean = false)(implicit tpc: TypePresentationContext): String =
      el.map(_.name).getOrElse("") + 
        limitString(parameters.map(p => p.name + ": " +  p.typeElement.map(_.getText).getOrElse("Any")).mkString("(", ", ", ")")) + 
        (if (needTpe && el.exists(e => !DumbService.isDumb(e.getProject))) ": " + tpe.map(_.presentableText).getOrElse("") else "")

    def getSignature(fun: ScFunction): String = getSignature(Option(fun), fun.parameters, None)(fun)

    def getSignature(fun: ScFunctionExpr): String = getSignature(None, fun.parameters, None)(fun)
    
    def getConstructorSignature(constr: ScFunction): String = 
      if (!constr.isConstructor) "" else "this" + limitString(getSignature(None, constr.parameters, None)(constr))

    def getPrimaryConstructorSignature(constr: ScPrimaryConstructor): String = 
      "this" + limitString(getSignature(None, constr.parameters, None)(constr))
    
    def describeFunction(fun: ScFunction): String = if (fun.isConstructor) getConstructorSignature(fun) else getSignature(fun)
    
    def describeFunction(fun: ScFunctionExpr): String = "λ" + getSignature(fun)

    def describeNewTemplate(newDef: ScNewTemplateDefinition): String = {
      val constructor = newDef.constructorInvocation match {
        case None =>
          "Object"
        case Some(c) if c.arguments.nonEmpty =>
          s"${c.typeElement.getText}$PAR_HOLDER"
        case Some(c) =>
          s"${c.typeElement.getText}"
      }
      val hasOtherTypes = newDef.extendsBlock.templateParents.exists(_.typeElementsWithoutConstructor.nonEmpty)
      val otherTypesHolder = if (hasOtherTypes) " with ..." else ""
      val text = s"new $constructor$otherTypesHolder"
      limitText(text, limit = MAX_STRING_LENGTH*2)
    }
    
    def describeMember(mb: ScMember): String = mb match {
      case pattern: ScPatternDefinition =>
        val vs = pattern.bindings.map(b => b.name)
        "val " + limitString(if (vs.length == 1) vs.head else vs.mkString("(", ", ", ""))
      case other => other.getName
    }
    
    def describeExpression(expr: ScExpression): String = expr match {
      case ifSt: ScIf => s"if (${limitString(ifSt.condition.map(_.getText).getOrElse(""))}) {...}"
      case whileSt: ScWhile => s"while(${limitString(whileSt.condition.map(_.getText).getOrElse(""), "...")})"
      case doWhileSt: ScDo => s"do ... while(${limitString(doWhileSt.condition.map(_.getText).getOrElse(""), "...")})"
      case matchSt: ScMatch => limitString(matchSt.expression.map(_.getText).getOrElse(PAR_HOLDER)) + " match {...}"
      case _ => "Expr"
    }
    
    def describeCaseClause(clause: ScCaseClause): String = s"case ${limitString(clause.pattern.map(_.getText).getOrElse(""))} =>"
    
    def getTemplateDefTooltip(td: ScTemplateDefinition): String = {
      val name = td.name
      td match {
        case _: ScClass                      => s"class $name"
        case _: ScObject                     => s"object $name"
        case _: ScTrait                      => s"trait $name"
        case newDef: ScNewTemplateDefinition => describeNewTemplate(newDef)
        case _                               => name
      }
    }
    
    def getFunctionTooltip(fun: ScFunction): String = describeFunction(fun) + (if (fun.getBody != null) limitText(fun.getBody.getText) else "")
    
    def getFunctionTooltip(funExpr: ScFunctionExpr): String = limitText(funExpr.getText)
    
    def getMemberTooltip(member: ScMember): String = limitText(member.getText)
    
    def getCaseClauseTooltip(clause: ScCaseClause): String = limitText(clause.getText)
  }
}
