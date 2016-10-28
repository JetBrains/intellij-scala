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

  def parseQQExpr(prefix: String, text: String, dialect: m.Dialect): Parsed[m.Tree] = {
//    val parser = new ScalametaParser(Input.String(text), dialect)
    val p = dialect(text)
    prefix match {
//      case "q"      => p.parse[m.Ctor].orElse(p.parse[m.Stat])
      case "q"      => p.parse[m.Stat].orElse(p.parse[m.Term.Block])
      case "t"      => p.parse[m.Type]
      case "p"      => p.parse[m.Case].orElse(p.parse[m.Pat])
      case "pt"     => p.parse[m.Pat.Type]
      case "arg"    => p.parse[m.Term.Arg]
      case "mod"    => p.parse[m.Mod]
      case "targ"   => p.parse[m.Type.Arg]
      case "parg"   => p.parse[m.Pat.Arg]
      case "ctor"   => p.parse[m.Ctor.Call]
      case "param"  => p.parse[m.Term.Param]
      case "tparam" => p.parse[m.Type.Param]
      case "source"     => p.parse[m.Source]
      case "template"   => p.parse[m.Template]
      case "importer"   => p.parse[m.Importer]
      case "importee"   => p.parse[m.Importer]
      case "enumerator" => p.parse[m.Enumerator]
      case _ => Parsed.Error(null, s"Unknown Quasiquote kind - $prefix", null)
    }
  }

  def getMetaQQExprType(pat: ScInterpolatedStringLiteral): TypeResult[ScType] = {
    val patternText = escapeQQ(pat)
    val qqdialect = if (pat.isMultiLineString)
      m.Dialect.forName("QuasiquoteTerm(Scala211, Multi)")
    else
      m.Dialect.forName("QuasiquoteTerm(Scala211, Single)")
    val prefix = pat.reference.map(_.refName).getOrElse(throw new ParseException(null, s"Failed to get QQ ref in ${pat.getText}"))
    val parsed = parseQQExpr(prefix, patternText, qqdialect)
    parsed match {
      case Parsed.Success(qq) =>
        ScalaPsiElementFactory
          .createTypeElementFromText(s"scala.meta.${qq.productPrefix}")(PsiManager.getInstance(pat.getProject))
          .getType()
      case err@Parsed.Error(pos, message, exc) =>
        Failure(message, Some(pat))
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

      val prefix = pat.ref.refName
      val patternText = escapeQQ(pat)
      val qqDialect = if (pat.isMultiLineString)
        m.Dialect.forName("QuasiquotePat(Scala211, Multi)")
      else
        m.Dialect.forName("QuasiquotePat(Scala211, Single)")
      parseQQExpr(prefix, patternText, qqDialect) match {
        case Parsed.Success(qqparts)   =>
          val parts = collectQQParts(qqparts)
          val classes = parts.map(_.pt)
          classes.map(classToScTypeString)
        case Parsed.Error(_, cause, exc)  =>
          Seq.empty
      }
  }

  def escapeQQ(pat: ScInterpolationPatternImpl): String = {
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
