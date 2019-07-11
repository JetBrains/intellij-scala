package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.extensions.{Model, PsiModifierListOwnerExt, StringsExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.util.CommonQualifiedNames._

class CaseClassAndCompanionMembersInjector extends SyntheticMembersInjector {

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      case ObjectWithCaseClassCompanion(_, cls: ScClass) =>
        val className            = cls.name
        val typeArgs             = typeArgsFromTypeParams(cls)
        val typeParamsDefinition = typeParamsString(cls.typeParameters)

        val unapply: Option[String] =
          if (cls.tooBigForUnapply) None
          else cls.constructor match {
            case Some(x: ScPrimaryConstructor) =>
              val clauses = x.parameterList.clauses
              val params = clauses.headOption.map(_.parameters).getOrElse(Seq.empty)
              val returnTypeText = if (params.isEmpty) BooleanFqn
              else {
                val params = clauses.head.parameters
                if (params.isEmpty) BooleanFqn
                else {
                  val caseClassParamTypes = params.map(p => paramTypeText(p, defaultTypeText = AnyFqn))
                  val optionTypeArg = caseClassParamTypes match {
                    case Seq(text) => text
                    case seq       => seq.commaSeparated(Model.Parentheses)
                  }
                  s"$OptionFqn[$optionTypeArg]"
                }
              }
              val unapplyName = if (params.lastOption.exists(_.isRepeatedParameter)) UnapplySeq else Unapply

              Some(s"def $unapplyName$typeParamsDefinition(x$$0: $className$typeArgs): $returnTypeText = throw new Error()")
            case None => None
          }

        val apply: Option[String] = if (cls.hasAbstractModifier) None else {
          cls.constructor match {
            case Some(x: ScPrimaryConstructor) =>

              val paramString = asFunctionParameters(x.effectiveParameterClauses, defaultExpressionString)

              Some(s"def $Apply$typeParamsDefinition$paramString: $className$typeArgs = throw new Error()")
            case None => None
          }
        }

        apply.toList ::: unapply.toList

      case cls: ScClass if shouldGenerateCopyMethod(cls) => copyMethodText(cls).toList
      case _                                             => Seq.empty
    }
  }

  private[this] def shouldGenerateCopyMethod(cls: ScClass): Boolean =
    cls.isCase && !cls.hasAbstractModifier && cls.parameters.nonEmpty && (cls.constructor match {
      case Some(cons: ScPrimaryConstructor) =>
        val hasRepeatedParam = cons.parameterList.clauses.exists(cl => cl.hasRepeatedParam)

        // That may not look entirely reasonable, but that's how it's done in scalac
        lazy val hasUserDefinedCopyMethod =
          cls.functions.exists(_.name == CommonNames.Copy) ||
            cls.supers.exists(_.findMethodsByName(CommonNames.Copy).nonEmpty)
        !hasRepeatedParam && !hasUserDefinedCopyMethod
      case _ => false
    })

  override def injectSupers(source: ScTypeDefinition): Seq[String] = source match {
    case ObjectWithCaseClassCompanion(obj, cc) if obj.isSyntheticObject =>
      val effectiveParamClauses = cc.constructor.map(_.effectiveParameterClauses).getOrElse(Seq.empty)
      val extendsFunction = cc.typeParameters.isEmpty && effectiveParamClauses.size == 1
      if (extendsFunction) {
        val paramTypes =
          for {
            clause <- effectiveParamClauses
            param  <- clause.parameters
          } yield paramTypeText(param, defaultTypeText = NothingFqn)

        val functionClassName = s"_root_.scala.Function${paramTypes.length}"
        val typeParameters = (paramTypes :+ cc.name).commaSeparated(Model.SquareBrackets)
        val functionTypeText = functionClassName + typeParameters

        Seq(functionTypeText)
      }
      else Seq.empty
    case _ =>
      Seq.empty
  }

  private def paramTypeText(param: ScParameter, defaultTypeText: String) = {
    val typeText = param.typeElement.fold(defaultTypeText)(_.getText)
    if (param.isRepeatedParameter) s"$SeqFqn[$typeText]"
    else typeText
  }

  //strips keywords and modifiers from class parameters
  private def asFunctionParameters(effectiveClauses: Seq[ScParameterClause], defaultParamString: ScParameter => String): String = {

    def paramText(p: ScParameter) = {
      val paramType = p.typeElement.fold("Any")(_.getText)
      val defaultExpr = defaultParamString(p)
      val repeatedSuffix = if (p.isRepeatedParameter) "*" else ""
      s"${p.name} : $paramType$repeatedSuffix$defaultExpr"
    }

    def clauseText(clause: ScParameterClause) = {
      val paramsText = clause.parameters.map(paramText).commaSeparated()

      if (clause.isImplicit) s"(implicit $paramsText)" else s"($paramsText)"
    }

    effectiveClauses.map(clauseText).mkString("")
  }

  private def defaultExpressionString(p: ScParameter): String =
    if (p.isDefaultParam) " = " + p.getDefaultExpression.fold("{}")(_.getText) else ""

  private[this] def typeParamsString(tparams: Seq[ScTypeParam]): String =
    if (tparams.isEmpty) ""
    else
      tparams
        .map(ScalaPsiUtil.typeParamString(_, withContextBounds = false))
        .mkString("[", ",", "]")

  private def copyMethodText(caseClass: ScClass): Option[String] = {
    caseClass.constructor
      .map(_.effectiveParameterClauses)
      .map { clauses =>
        val className = caseClass.name

        val (clauseWithDefault, restClauses) =
          if (clauses.isEmpty) (Seq.empty, Seq.empty)
          else                 clauses.splitAt(1)

        val paramString =
          asFunctionParameters(clauseWithDefault, defaultParamString = p => s" = $className.this.${p.name}") +
            asFunctionParameters(restClauses, defaultExpressionString)

        val typeParamsDefinition = typeParamsString(caseClass.typeParameters)
        val returnType = className + typeArgsFromTypeParams(caseClass)
        "def copy" + typeParamsDefinition + paramString + " : " + returnType + " = throw new Error(\"\")"
    }
  }

  private def typeArgsFromTypeParams(caseClass: ScClass): String =
    if (caseClass.typeParameters.isEmpty) ""
    else caseClass.typeParameters.map(_.name).commaSeparated(model = Model.SquareBrackets)
}
