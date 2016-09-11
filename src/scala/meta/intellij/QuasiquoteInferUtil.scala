package scala.meta.intellij

import com.intellij.psi.PsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScInterpolationPatternImpl
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaTypeSystem}

import scala.meta.Term
import scala.meta.parsers.Parsed
import scala.meta.parsers.Parsed._

/**
  * @author Mikhail Mutcianko
  * @since 11.09.16
  */
object QuasiquoteInferUtil {
  import org.jetbrains.plugins.scala.extensions._

  def classToScTypeString(c: Class[_]): String = {
    if(c.isArray) {
      s"scala.collection.immutable.Seq[${classToScTypeString(c.getComponentType)}]"
    } else {
      c.getTypeName.replaceAll("\\$", ".")
    }
  }

  def getMetaQQPatternTypes(pat: ScInterpolationPatternImpl): Seq[String] = {
    import scala.{meta => m}

    def collectQQParts(t: scala.meta.Tree): Seq[m.internal.ast.Quasi] = {
      t.children.flatMap {
        case qq: m.internal.ast.Quasi => Some(qq)
        case other => collectQQParts(other)
      }
    }

    val patternText = if (pat.isMultiLineString) {
      pat.getText.replaceAll("^q\"\"\"", "").replaceAll("\"\"\"$", "").trim
    } else {
      pat.getText.replaceAll("^q\"", "").replaceAll("\"$", "").trim
    }

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
}
