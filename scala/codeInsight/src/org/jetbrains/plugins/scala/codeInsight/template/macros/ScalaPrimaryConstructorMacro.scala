package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ScalaFeatures

sealed abstract class ScalaPrimaryConstructorMacro extends ScalaMacro {
  protected def parametersText(parameters: ScParameters): Option[String]

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    findParameters(context)
      .flatMap(parametersText)
      .map(new TextResult(_))
      .orNull
  }

   private def findParameters(context: ExpressionContext): Option[ScParameters] =
    for {
      clazz       <- ScalaCompanionClassMacro.companionClass(context.getPsiElementAtStartOffset)
      constructor <- clazz.constructor
    } yield constructor.parameterList
}


object ScalaPrimaryConstructorMacro {
  final class Params extends ScalaPrimaryConstructorMacro {

    override def getNameShort: String = "primaryConstructorParams"

    override def getPresentableName: String = ScalaCodeInsightBundle.message("macro.primaryConstructor.param.instances")

    override protected def parametersText(parameters: ScParameters): Option[String] = {
      val clauses: Seq[ScParameterClause] = parameters.clauses
      if (clauses.nonEmpty) {
        val clausesStrings = clauses.map(clauseText)
        Some(clausesStrings.mkString(")("))
      } else {
        None
      }
    }

    private def clauseText(clause: ScParameterClause): String = {
      val params: Seq[String] = clause.parameters.map { (param: ScParameter) =>
        val name = param.name
        val typ = param.paramType.map(_.getText).getOrElse("Any")
        s"$name: $typ"
      }

      val paramsStr = params.commaSeparated()
      // we do not private/final/val/var etc... but we would like to preserve implicits in apply definition
      if (clause.isImplicit) s"implicit $paramsStr"
      else if (clause.isUsing) s"using $paramsStr"
      else paramsStr
    }
  }

  final class ParamNames extends ScalaPrimaryConstructorMacro {

    override def getNameShort: String = "primaryConstructorParamNames"

    override def getPresentableName: String = ScalaCodeInsightBundle.message("macro.primaryConstructor.param.names")

    override def calculateResult(expressions: Array[Expression], context: ExpressionContext): Result = {
      val argsVarResult: Option[TextResult] =
        expressions.headOption
          .map(_.calculateResult(context))
          .filterByType[TextResult]

      argsVarResult
        .flatMap { (args: TextResult) =>
          // ParamNames macro is calculated based on `ARGS` template variable value. User cans change it,
          // so we have to reparse the result in `parseParameterClause` method to extract new parameter names
          createScParametersFromText(s"(${args.toString})", context)
        }
        .flatMap(parametersText)
        .map(new TextResult(_))
        .orNull
    }

    private def createScParametersFromText(paramsText: String, context: ExpressionContext): Option[ScParameters] = {
      implicit def project: Project = context.getProject

      val features =
        context
        .getPsiElementAtStartOffset
        .toOption
        .fold(ScalaFeatures.default: ScalaFeatures)(ScalaFeatures.forPsiOrDefault)

      Option(ScalaPsiElementFactory.createScalaFileFromText(s"def foo$paramsText: Unit = ???", features))
        .flatMap(file => Option(PsiTreeUtil.findChildOfType(file, classOf[ScParameters])))
    }

    override protected def parametersText(parameters: ScParameters): Option[String] = {
      val clauses: Seq[ScParameterClause] = parameters.clauses
      if (clauses.nonEmpty) {
        val clausesStrings = clauses.map(clauseText)
        Some(clausesStrings.mkString(")("))
      } else {
        None
      }
    }

    private def clauseText(clause: ScParameterClause): String = {
      clause.parameters.map(_.getName).commaSeparated()
    }
  }

  final class ParamTypes extends ScalaPrimaryConstructorMacro {

    override def getNameShort: String = "primaryConstructorParamTypes"

    override def getPresentableName: String = ScalaCodeInsightBundle.message("macro.primaryConstructor.param.types")

    override protected def parametersText(parameters: ScParameters): Option[String] = {
      val firstParamClause = parameters.clauses.headOption.map(_.parameters)
      firstParamClause.map { params: Seq[ScParameter] =>
        val types: Seq[String] = params.map(_.paramType.map(_.getText).getOrElse(""))
        types.commaSeparated().parenthesize(types.size > 1)
      }
    }
  }
}