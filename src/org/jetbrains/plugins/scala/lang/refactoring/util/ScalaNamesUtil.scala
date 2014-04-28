package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.application.ApplicationManager
import lexer.{ScalaLexer, ScalaTokenTypes}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import scala.reflect.NameTransformer
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import extensions._

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */
object ScalaNamesUtil {
  private def checkGeneric(text: String, predicate: ScalaLexer => Boolean): Boolean = {
    ApplicationManager.getApplication.assertReadAccessAllowed()
    if (text == null || text == "") return false
    
    val lexer = new ScalaLexer()
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

  def isKeyword(text: String): Boolean = {
    checkGeneric(text, lexer => lexer.getTokenType != null && ScalaTokenTypes.KEYWORDS.contains(lexer.getTokenType))
  }
  
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
      if (name.startsWith("`") && name.endsWith("`")) Some(name.substring(1, name.length - 1))
      else None
    }
  }

  def toJavaName(name: String) = {
    val toEncode = name match {
      case ScalaNamesUtil.isBacktickedName(s) => s
      case _ => name
    }
    NameTransformer.encode(toEncode)
  }

  def changeKeyword(s: String): String = {
    if (ScalaNamesUtil.isKeyword(s)) "`" + s + "`"
    else s
  }
}
