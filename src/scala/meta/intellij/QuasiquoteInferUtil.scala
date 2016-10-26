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
import scala.util.Try

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
    fqnO.exists(_.startsWith("scala.meta.quasiquotes.Api.XtensionQuasiquote"))
  }

  def parseQQExpr(prefix: String, text: String, dialect: m.Dialect): m.Tree = {
    val parser = new ScalametaParser(Input.String(text), dialect)
    prefix match {
      case "q"      => Try(parser.parseQuasiquoteCtor()).getOrElse(parser.parseQuasiquoteStat())
      case "t"      => parser.parseType()
      case "arg"    => parser.parseTermArg()
      case "param"  => parser.parseTermParam()
      case "targ"   => parser.parseTypeArg()
      case "tparam" => parser.parseTypeParam()
      case "p"      => Try(parser.parseCase()).getOrElse(parser.parseQuasiquotePat())
      case "parg"   => parser.parseQuasiquotePatArg()
      case "pt"     => parser.parseQuasiquotePatType()
      case "ctor"   => parser.parseQuasiquoteCtor()
      case "mod"    => parser.parseQuasiquoteMod()
      case "template"   => parser.parseQuasiquoteTemplate()
      case "enumerator" => parser.parseEnumerator()
      case "importer"   => parser.parseImporter()
      case "importee"   => parser.parseImportee()
      case "source"     => parser.parseSource()
      case _ => throw ParseException(null, s"Unexpected QQ prefix - $prefix")
    }
  }

  def getMetaQQExprType(pat: ScInterpolatedStringLiteral): TypeResult[ScType] = {
    val patternText = escapeQQ(pat)
    val qqdialect = if (pat.isMultiLineString)
      m.Dialect.forName("QuasiquoteTerm(Scala211, Multi)")
    else
      m.Dialect.forName("QuasiquoteTerm(Scala211, Single)")
    try {
      val prefix = pat.reference.map(_.refName).getOrElse(throw new ParseException(null, s"Failed to get QQ ref in ${pat.getText}"))
      val parsed = parseQQExpr(prefix, patternText, qqdialect)
      ScalaPsiElementFactory.createTypeElementFromText(s"scala.meta.${parsed.productPrefix}")(PsiManager.getInstance(pat.getProject)).getType()
    } catch {
      case e: ParseException => Failure(e.getMessage, Some(pat))
      case e: Exception => Failure(e.getMessage, Some(pat))
    }
  }

  def escapeQQ(pat: ScInterpolatedStringLiteral): String = {
    if (pat.isMultiLineString) {
      pat.getText.replaceAll("^[a-z]+\"\"\"", "").replaceAll("\"\"\"$", "").trim
    } else {
      pat.getText.replaceAll("^[a-z]+\"", "").replaceAll("\"$", "").trim
    }
  }

  def getMetaQQPatternTypes(pat: ScInterpolationPatternImpl): Seq[String] = {

    def collectQQParts(t: scala.meta.Tree): Seq[m.internal.ast.Quasi] = {
      t.children.flatMap {
        case qq: m.internal.ast.Quasi => Some(qq)
        case other => collectQQParts(other)
      }
    }

    try {
      val prefix = pat.ref.refName
      val patternText = escapeQQ(pat)
      val qqDialect = if (pat.isMultiLineString)
        m.Dialect.forName("QuasiquotePat(Scala211, Multi)")
      else
        m.Dialect.forName("QuasiquotePat(Scala211, Single)")
      val parsed = parseQQExpr(prefix, patternText, qqDialect)
      val parts = collectQQParts(parsed)
      val classes = parts.map(_.pt)
      classes.map(classToScTypeString)
    } catch {
      case ParseException(pos, message) =>
        Seq.empty  // TODO: report more meaningful error on parse failure
      case _: Exception =>
        Seq.empty
    }
  }

  def escapeQQ(pat: ScInterpolationPatternImpl): String = {
    val c = pat.getFirstChild
    if (pat.isMultiLineString) {
      pat.getText.replaceAll("^[a-z]+\"\"\"", "").replaceAll("\"\"\"$", "").trim
    } else {
      pat.getText.replaceAll("^[a-z]+\"", "").replaceAll("\"$", "").trim
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
