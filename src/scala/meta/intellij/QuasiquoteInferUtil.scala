package scala.meta.intellij

import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScInterpolationPatternImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableReferenceElement, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference

import scala.meta.inputs.Input
import scala.meta.internal.parsers.ScalametaParser
import scala.meta.parsers.Parsed._
import scala.meta.parsers.{ParseException, Parsed}

/**
  * @author Mikhail Mutcianko
  * @since 11.09.16
  */
object QuasiquoteInferUtil {

  import scala.{meta => m}

  def isMetaQQ(ref: ResolvableReferenceElement): Boolean = {
    ref.bind() match {
      case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.name == "unapply" || fun.name == "apply" && isMetaQQ(fun) => true
      case _ => false
    }
  }

  def isMetaQQ(fun: ScFunction): Boolean = {
    val fqnO = Option(fun.containingClass).map(_.qualifiedName)
    fqnO.contains("scala.meta.quasiquotes.Api.XtensionQuasiquoteTerm.q")
  }

  def parseQQExpr(prefix: String, text: String, dialect: m.Dialect): m.Tree = {
    val parser = new ScalametaParser(Input.String(text), dialect)
    prefix match {
      case "q" => parser.parseQuasiquoteStat()
      case "t" => parser.parseType()
    }
  }

  def getMetaQQExprType(pat: ScInterpolatedStringLiteral): TypeResult[ScType] = {
    val patternText = escapeQQ(pat)

    val qqdialect = if (pat.isMultiLineString)
      m.Dialect.forName("QuasiquoteTerm(Scala211, Multi)")
    else
      m.Dialect.forName("QuasiquoteTerm(Scala211, Single)")
    val parser = new ScalametaParser(Input.String(patternText), qqdialect)
    try {
      val parsed: m.Stat = parser.parseQuasiquoteStat()
      ScalaPsiElementFactory.createTypeElementFromText(s"scala.meta.${parsed.productPrefix}", PsiManager.getInstance(pat.getProject)).getType()
    } catch {
      case e: ParseException => Failure(e.getMessage, None)
    }
  }

  def escapeQQ(pat: ScInterpolatedStringLiteral): String = {
    val c = pat.getFirstChild
    if (pat.isMultiLineString) {
      pat.getText.replaceAll("^q\"\"\"", "").replaceAll("\"\"\"$", "").trim
    } else {
      pat.getText.replaceAll("^q\"", "").replaceAll("\"$", "").trim
    }
  }

  def getMetaQQPatternTypes(pat: ScInterpolationPatternImpl): Seq[String] = {

    def collectQQParts(t: scala.meta.Tree): Seq[m.internal.ast.Quasi] = {
      t.children.flatMap {
        case qq: m.internal.ast.Quasi => Some(qq)
        case other => collectQQParts(other)
      }
    }

    val patternText = escapeQQ(pat)

    val qqdialect = if (pat.isMultiLineString)
      m.Dialect.forName("QuasiquotePat(Scala211, Multi)")
    else
      m.Dialect.forName("QuasiquotePat(Scala211, Single)")
    val parsed: Parsed[m.Stat] = qqdialect(patternText).parse[m.Stat]
    parsed match {
      case Success(term) =>
        val parts = collectQQParts(term)
        val classes = parts.map(_.pt)
        classes.map(classToScTypeString)
      case Error(pos, message, details) =>
        Seq.empty
    }
  }

  def escapeQQ(pat: ScInterpolationPatternImpl): String = {
    val c = pat.getFirstChild
    if (pat.isMultiLineString) {
      pat.getText.replaceAll("^q\"\"\"", "").replaceAll("\"\"\"$", "").trim
    } else {
      pat.getText.replaceAll("^q\"", "").replaceAll("\"$", "").trim
    }
  }

  private def classToScTypeString(c: Class[_]): String = {
    if (c.isArray) {
      s"scala.collection.immutable.Seq[${classToScTypeString(c.getComponentType)}]"
    } else {
      c.getTypeName.replaceAll("\\$", ".")
    }
  }
}
