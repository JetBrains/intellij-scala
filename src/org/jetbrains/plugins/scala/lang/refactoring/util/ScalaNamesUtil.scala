package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.reflect.NameTransformer

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */
object ScalaNamesUtil {
  val keywordNames = ScalaTokenTypes.KEYWORDS.getTypes.map(_.toString).toSet

  private val lexerCache = new ThreadLocal[ScalaLexer] {
    override def initialValue(): ScalaLexer = new ScalaLexer()
  }

  private def checkGeneric(text: String, predicate: ScalaLexer => Boolean): Boolean = {
//    ApplicationManager.getApplication.assertReadAccessAllowed() - looks like we don't need it
    if (text == null || text == "") return false

    val lexer = lexerCache.get()
    lexer.start(text, 0, text.length(), 0)
    if (!predicate(lexer)) return false
    lexer.advance()
    lexer.getTokenType == null
  }

  def isOpCharacter(c : Char) : Boolean = {
    c match {
      case '~' | '!' | '@' | '#' | '%' | '^' | '*' | '+' | '-' | '<' | '>' | '?' | ':' | '=' | '&' | '|' | '/' | '\\' =>
        true
      case ch =>
        Character.getType(ch) == Character.MATH_SYMBOL.toInt || Character.getType(ch) == Character.OTHER_SYMBOL.toInt
    }
  }

  def isIdentifier(text: String): Boolean = {
    checkGeneric(text, lexer => lexer.getTokenType == ScalaTokenTypes.tIDENTIFIER)
  }

  def isQualifiedName(text: String): Boolean = {
    if (StringUtil.isEmpty(text)) return false
    text.split('.').forall(isIdentifier)
  }

  def isKeyword(text: String): Boolean = keywordNames.contains(text)

  def isOperatorName(text: String): Boolean = isIdentifier(text) && isOpCharacter(text(0))

  def scalaName(element: PsiElement) = element match {
    case scNamed: ScNamedElement => scNamed.name
    case psiNamed: PsiNamedElement => psiNamed.getName
  }

  def qualifiedName(named: PsiNamedElement): Option[String] = {
    ScalaPsiUtil.nameContext(named) match {
      case pack: PsiPackage => Some(pack.getQualifiedName)
      case clazz: PsiClass => Some(clazz.qualifiedName)
      case memb: PsiMember =>
        val containingClass = memb.containingClass
        if (containingClass != null && containingClass.qualifiedName != null && memb.hasModifierProperty(PsiModifier.STATIC)) {
          Some(Seq(containingClass.qualifiedName, named.name).filter(_ != "").mkString("."))
        } else None
      case _ => None
    }
  }

  object isBackticked {
    def unapply(named: ScNamedElement): Option[String] = {
      val name = named.name
      isBacktickedName.unapply(name)
    }
  }

  object isBacktickedName {
    def unapply(name: String): Option[String] = {
      if (name == null || name.isEmpty) None
      else if (name != "`" && name.startsWith("`") && name.endsWith("`")) Some(name.substring(1, name.length - 1))
      else None
    }
  }

  def splitName(name: String): Seq[String] = {
    if (name == null || name.isEmpty) Seq.empty
    else if (name.contains(".")) name.split("\\.")
    else Seq(name)
  }

  def toJavaName(name: String) = {
    val toEncode = name match {
      case ScalaNamesUtil.isBacktickedName(s) => s
      case _ => name
    }
    NameTransformer.encode(toEncode)
  }

  def convertMemberName(name: String): String = {
    val toDecode = name match {
      case ScalaNamesUtil.isBacktickedName(s) => s
      case _ => name
    }
    NameTransformer.decode(toDecode)
  }

  def convertMemberFqn(fqn: String): String =
    splitName(fqn).map(convertMemberName).mkString(".")

  def fqnNamesEquals(l: String, r: String): Boolean = {
    if (l == r) return true
    convertMemberFqn(l) == convertMemberFqn(r)
  }

  def memberNamesEquals(l: String, r: String): Boolean = {
    if (l == r) return true
    convertMemberName(l) == convertMemberName(r)
  }

  def removeBacktickedIfScalaKeyword(name: String): String = {
    name match {
      case ScalaNamesUtil.isBacktickedName(n) if isKeyword(n) => n
      case _ => name
    }
  }

  def removeBacktickedIfScalaKeywordFqn(fqn: String): String =
    splitName(fqn).map(removeBacktickedIfScalaKeyword).mkString(".")

  def addBacktickedIfScalaKeywordFqn(fqn: String): String =
    splitName(fqn).map(changeKeyword).mkString(".")

  def changeKeyword(s: String): String =
    if (ScalaNamesUtil.isKeyword(s)) s"`$s`" else s
}
