package scala.meta.intellij

import com.intellij.psi.{PsiElementFactory, PsiManager}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScInterpolatedStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScInterpolationPatternImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScMethodCallImpl, ScReferenceExpressionImpl}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableReferenceElement, ScalaResolveResult}

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
    fqnO.exists(_.startsWith("scala.meta.quasiquotes.Api.XtensionQuasiquote"))
  }

  def extractQQFromParts(ref: ScReferenceExpression): String = {

    ref.qualifier match {
      case Some(mc: ScMethodCallImpl) => mc.argumentExpressions.zipWithIndex.foldLeft("") {
        case (a,(expr, i)) => s"$a $$__meta$i${expr.text}"
      }
      case _ => ""
    }
  }


  def parseQQExpr(prefix: String, text: String, dialect: m.Dialect): Parsed[m.Tree] = {
    val p = dialect(text)
    prefix match {
      // FIXME: this seems wrong - reference q parser only parses Stat or Ctor, however this way many qqs couldn't be parsed
      case "q"      => p.parse[m.Stat].orElse(p.parse[m.Source])
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
      case "importee"   => p.parse[m.Importee]
      case "enumerator" => p.parse[m.Enumerator]
      case _ => Parsed.Error(null, s"Unknown Quasiquote kind - $prefix", null)
    }
  }

  def getMetaQQExpectedTypes(stringContextApplicationRef: ScReferenceExpression): Seq[Parameter] = {
    val joined = stringContextApplicationRef.qualifier match {
      case Some(mc: ScMethodCallImpl) => mc.argumentExpressions.zipWithIndex.foldLeft("") {
        case (a, (expr, i)) if i > 0 =>
          s"$a $$__meta$i${expr.text.replaceAll("^\"\"\"", "").replaceAll("\"\"\"$", "")}"
        case (_, (expr, i)) if i == 0 =>
          expr.text.replaceAll("^\"\"\"", "").replaceAll("\"\"\"$", "")
      }
      case _ => ""
    }
    val qqdialect = if (joined.contains("\n"))
      m.Dialect.forName("QuasiquoteTerm(Scala211, Multi)")
    else
      m.Dialect.forName("QuasiquoteTerm(Scala211, Single)")
    val typeStrings = parseQQExpr(stringContextApplicationRef.refName, joined, qqdialect) match {
      case Parsed.Success(qqparts) =>
        val parts = collectQQParts(qqparts)
        val classes = parts.map(_.pt)
        classes.map(classToScTypeString)
      case Parsed.Error(_, cause, exc) =>
        Seq.empty
    }
    val types = typeStrings.map(ScalaPsiElementFactory.createTypeFromText(_, stringContextApplicationRef, null))
    val treeType = ScalaPsiElementFactory.createTypeFromText("scala.meta.Tree", stringContextApplicationRef, null)
    types.zipWithIndex.map {
      case (Some(tp), i) =>
        new Parameter(s"__meta$i", None, tp, isDefault = false, isRepeated = false, isByName = false, index = i)
      case (None, i) =>
        new Parameter(s"__meta$i", None, treeType.get, isDefault = false, isRepeated = false, isByName = false, index = i)
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

  private def collectQQParts(t: scala.meta.Tree): Seq[m.internal.ast.Quasi] = {
    t.children.flatMap {
      case qq: m.internal.ast.Quasi => Some(qq)
      case other => collectQQParts(other)
    }
  }

  def getMetaQQPatternTypes(pat: ScInterpolationPatternImpl): Seq[String] = {


    val prefix = pat.ref.refName
    val patternText = escapeQQ(pat)
    val qqDialect = if (pat.isMultiLineString)
      m.Dialect.forName("QuasiquotePat(Scala211, Multi)")
    else
      m.Dialect.forName("QuasiquotePat(Scala211, Single)")
    parseQQExpr(prefix, patternText, qqDialect) match {
      case Parsed.Success(qqparts) =>
        val parts = collectQQParts(qqparts)
        val classes = parts.map(_.pt)
        classes.map(classToScTypeString)
      case Parsed.Error(_, cause, exc) =>
        Seq.empty
    }
  }

  def escapeQQ(pat: ScInterpolated): String = {
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
