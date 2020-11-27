package org.jetbrains.plugins.scala.worksheet

import scala.quoted._

import dotty.tools.dotc.ast.tpd.{Tree => InternalTree}
import dotty.tools.dotc.core.Contexts.{Context => InternalContext}
import dotty.tools.dotc.ast.{Trees => InternalTrees}

object MacroPrinter3 {

  inline def showType[T](inline expr: => T): String = ${ showTypeImpl('expr) }
  inline def showMethodDefinition[T](inline expr: T): String = ${ showMethodDefinitionImpl('expr) }

  private def summonInternalContext(implicit quotes: Quotes): InternalContext = {
    quotes.asInstanceOf[scala.quoted.runtime.impl.QuotesImpl].ctx
  }

  private def showTypeImpl[T](expr: quoted.Expr[T])(implicit quotes: Quotes): Expr[String] = {
    import quotes.reflect._

    // need to import TermMethod to use `.tpe` extension methods, but for some reason import quotes.TermMethods doesn't work
    import quotes._

    implicit val ic: InternalContext = summonInternalContext
    val printer = new dotty.tools.dotc.printing.ReplPrinter(ic)

    val quotesImpl = quotes.asInstanceOf[scala.quoted.runtime.impl.QuotesImpl]
    val tpe1 = Term.of(expr).tpe
    val tpe2 = tpe1.asInstanceOf[dotty.tools.dotc.core.Types.Type]
    val tpe3 = tpe2
      .deconst // avoid value types (val x: 42 = 42)
      .widenTermRefExpr // avoid varName.type
    val text = printer.toText(tpe3)
    Expr(text.mkString(80, false)) // TODO: max width, const or parameterize in settings?
  }

  private def showMethodDefinitionImpl[T](expr: Expr[T])(implicit quotes: Quotes): Expr[String] = {
    import quotes.reflect._
    implicit val ic: InternalContext = summonInternalContext

    def showTypeParam(p: TypeDef) =
      p.show.stripPrefix("type ")

    def showTypeParams(params: List[TypeDef]) =
      if(params.isEmpty) ""
      else params.map(showTypeParam).mkString("[", ", ", "]")

    def showParam(p: ValDef) =
      p match {
        case internal: InternalTrees.ValDef[_] => internal.show
        case _                                 => p.show.stripPrefix("val ")
      } /*.stripSuffix(" = _")*/

    def showParams(params: List[ValDef]) =
      params.map(showParam).mkString("(", ", ", ")")

    def showReturnType(typ: TypeTree) =
      typ match {
        case internal: InternalTrees.Ident[_]   => internal.show
        case internal: InternalTrees.TypTree[_] => internal.show
        case _                                  => typ.show
      }

    def showDef(defName: String,  typeParams: List[TypeDef], paramss: List[List[ValDef]], returnTpt: TypeTree): String = {
      val typeParamsText = showTypeParams(typeParams)
      val paramsText = paramss.map(showParams).mkString("")
      val returnTypeText = showReturnType(returnTpt)

      s"def $defName$typeParamsText$paramsText: $returnTypeText"
    }

    def processStatements(statements: List[Statement]) =
      statements.headOption.flatMap {
        case DefDef(name, typeParams, paramss, returnTpt, _) =>
          Some(showDef(name, typeParams, paramss, returnTpt))
        case _ =>
          None
      }

    val xTree: Term = Term.of(expr)
    val result = xTree match {
      case Block(statements, _)                => processStatements(statements)
      case Inlined(_, _, Block(statements, _)) => processStatements(statements)
      case _                                   => None
    }

    Expr(result.getOrElse(""))
  }
}