package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import com.intellij.openapi.util.text.StringUtil.isEmpty
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings.EXCLUDE_PREFIX
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator._

import scala.reflect.NameTransformer

/**
  * User: Alexander Podkhalyuzin
  * Date: 24.06.2008
  */
object ScalaNamesUtil {

  def isOpCharacter(character: Char): Boolean = character match {
    case '~' | '!' | '@' | '#' | '%' | '^' | '*' | '+' | '-' | '<' | '>' | '?' | ':' | '=' | '&' | '|' | '/' | '\\' => true
    case _ =>
      import Character._
      getType(character) match {
        case MATH_SYMBOL | OTHER_SYMBOL => true
        case _ => false
      }
  }

  def isQualifiedName(text: String): Boolean =
    !isEmpty(text) && text.split('.').forall(isIdentifier)

  def isOperatorName(text: String): Boolean = isIdentifier(text) && isOpCharacter(text(0))

  def scalaName(element: PsiElement): String = element match {
    case scNamed: ScNamedElement => scNamed.name
    case psiNamed: PsiNamedElement => psiNamed.getName
  }

  def qualifiedName(named: PsiNamedElement): Option[String] =
    ScalaPsiUtil.nameContext(named) match {
      case pack: PsiPackage => Some(pack.getQualifiedName)
      case ClassQualifiedName(qualifiedName) => Some(qualifiedName)
      case member: PsiMember if !member.hasModifierProperty(PsiModifier.STATIC) => None
      case ContainingClass(ClassQualifiedName(qualifiedName)) if qualifiedName.nonEmpty =>
        val result = named.name match {
          case null | "" => qualifiedName
          case name => qualifiedName + "." + name
        }
        Some(result)
      case _ => None
    }

  object isBacktickedName {

    private[this] val BackTick = "`"

    def unapply(name: String): Option[String] = name match {
      case null | "" | BackTick => None
      case _ if name.startsWith(BackTick) && name.endsWith(BackTick) =>
        Some(name.substring(1, name.length - 1))
      case _ => None
    }

    def apply(name: String): Option[String] =
      isBacktickedName.unapply(name).orElse {
        Option(name)
      }
  }

  def splitName(name: String): Seq[String] = name match {
    case null | "" => Seq.empty
    case _ if name.contains(".") => name.split("\\.")
    case _ => Seq(name)
  }

  def toJavaName(name: String): String =
    withTransformation(name)(NameTransformer.encode)

  def clean(name: String): String =
    withTransformation(name)(NameTransformer.decode)

  def cleanFqn(fqn: String): String =
    fqnWithTransformation(fqn)(clean)

  def equivalentFqn(l: String, r: String): Boolean =
    l == r || cleanFqn(l) == cleanFqn(r)

  def equivalent(l: String, r: String): Boolean =
    l == r || clean(l) == clean(r)

  def escapeKeywordsFqn(fqn: String): String =
    fqnWithTransformation(fqn)(escapeKeyword)

  def escapeKeyword(s: String): String =
    if (isKeyword(s)) s"`$s`" else s

  def nameFitToPatterns(qualName: String, patterns: Array[String], strict: Boolean): Boolean = {
    val (exclude, include) = patterns.toSeq.partition(_.startsWith(EXCLUDE_PREFIX))

    !exclude.exists(excl => fitToPattern(excl.stripPrefix(EXCLUDE_PREFIX), qualName, strict)) &&
      include.exists(fitToPattern(_, qualName, strict))
  }

  private def withTransformation(name: String)
                                (transformation: String => String) =
    isBacktickedName(name)
      .map(transformation)
      .orNull

  private def fqnWithTransformation(fqn: String)
                                   (transformation: String => String) =
    splitName(fqn)
      .map(transformation)
      .mkString(".")

  private def fitToPattern(pattern: String, qualName: String, strict: Boolean): Boolean = {

    @scala.annotation.tailrec
    def fitToUnderscorePattern(pattern: String, qualName: String, strict: Boolean): Boolean = {
      val subPattern = pattern.stripSuffix("._")
      val dotIdx = qualName.lastIndexOf('.')

      if (dotIdx <= 0) false
      else {
        val subName = qualName.substring(0, dotIdx)

        if (subPattern.endsWith("._"))
          fitToUnderscorePattern(subPattern, subName, strict = true)
        else if (strict)
          subName == subPattern
        else
          subName.startsWith(subPattern)
      }
    }

    if (pattern.endsWith("._"))
      fitToUnderscorePattern(pattern, qualName, strict)
    else qualName == pattern
  }
}
