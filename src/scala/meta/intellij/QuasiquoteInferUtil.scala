package scala.meta.intellij

import com.intellij.psi.{PsiElementFactory, PsiManager}
import org.jetbrains.plugins.scala.lang.parser.ScalaParser
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScInterpolationPatternImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypeSystem}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StableCodeReferenceElementResolver}

import scala.meta.Term
import scala.meta.inputs.Input
import scala.meta.internal.parsers.ScalametaParser
import scala.meta.parsers.Parsed
import scala.meta.parsers.Parsed._

/**
  * @author Mikhail Mutcianko
  * @since 11.09.16
  */
object QuasiquoteInferUtil {
  import org.jetbrains.plugins.scala.extensions._
  import scala.{meta => m}


  def isMetaQQ(fun: ScFunction): Boolean = {
    val fqnO  = Option(fun.containingClass).map(_.qualifiedName)
    fqnO.contains("scala.meta.quasiquotes.Api.XtensionQuasiquoteTerm.q")
  }


  def isMetaQQ(ref: ScStableCodeReferenceElement): Boolean = {
    ref.bind() match {
      case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.name == "unapply" && isMetaQQ(fun) => true
      case _ => false
    }
  }

  def isMetaQQ(ref: ScReferenceExpression): Boolean = {
    ref.bind() match {
      case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.name == "unapply" || fun.name == "apply" && isMetaQQ(fun) => true
      case _ => false
    }
  }

  private def classToScTypeString(c: Class[_]): String = {
    if(c.isArray) {
      s"scala.collection.immutable.Seq[${classToScTypeString(c.getComponentType)}]"
    } else {
      c.getTypeName.replaceAll("\\$", ".")
    }
  }


  def getMetaQQExprType(pat: ScInterpolatedStringLiteral): TypeResult[ScType] = {
    val patternText = escapeQQ(pat)

    val qqdialect = if (pat.isMultiLineString)
      m.Dialect.forName("QuasiquoteTerm(Scala211, Multi)")
    else
      m.Dialect.forName("QuasiquoteTerm(Scala211, Single)")
    val parser = new ScalametaParser(Input.String(patternText), qqdialect)
//    val parsed: Parsed[m.Stat] = qqdialect(patternText).parse[m.Stat]
    val parsed = parser.parseQuasiquoteStat()
    val element = ScalaPsiElementFactory.createTypeElementFromText(s"scala.meta.${parsed.productPrefix}", PsiManager.getInstance(pat.getProject))
    element.getType()
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
        val map = classes.map(classToScTypeString)
        map
      case Error(pos, message, details) =>
        Seq.empty
    }
  }

  def escapeQQ(pat: ScInterpolationPatternImpl): String = {
    if (pat.isMultiLineString) {
      pat.getText.replaceAll("^q\"\"\"", "").replaceAll("\"\"\"$", "").trim
    } else {
      pat.getText.replaceAll("^q\"", "").replaceAll("\"$", "").trim
    }
  }

  def escapeQQ(pat: ScInterpolatedStringLiteral): String = {
    if (pat.isMultiLineString) {
      pat.getText.replaceAll("^q\"\"\"", "").replaceAll("\"\"\"$", "").trim
    } else {
      pat.getText.replaceAll("^q\"", "").replaceAll("\"$", "").trim
    }
  }
}
