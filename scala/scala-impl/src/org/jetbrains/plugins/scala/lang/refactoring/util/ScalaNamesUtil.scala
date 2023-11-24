package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.openapi.util.text.StringUtil.isEmpty
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings.EXCLUDE_PREFIX
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator._

import scala.reflect.NameTransformer

object ScalaNamesUtil {

  //This method is a copy of:
  //dotty.tools.dotc.util.Chars.isOperatorPart
  //dotty.tools.dotc.util.Chars#isSpecial(int)
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

  //Q: Could this logic be legacy and invalid?
  // In the latest Scala 3 repo, it checks for text.last, not text(0)
  // See `dotty.tools.dotc.parsing.Scanners.TokenData#isOperator` in
  // https://github.com/lampepfl/dotty/blob/590e15920eecf93a1a15ae328cb94e0be666ec2a/compiler/src/dotty/tools/dotc/parsing/Scanners.scala#L89<br>
  def isOperatorName(text: String): Boolean = isIdentifier(text) && isOpCharacter(text(0))

  def scalaName(element: PsiElement): String = element match {
    case scNamed: ScNamedElement => scNamed.name
    case psiNamed: PsiNamedElement => psiNamed.getName
  }

  def qualifiedName(named: PsiNamedElement): Option[String] =
    named.nameContext match {
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

    val BackTick = "`"

    def unapply(name: String): Option[String] =
      withoutBackticks(name) match {
        case scalaName: String if name.lengthCompare(scalaName.length) > 0 => Some(scalaName)
        case _ => None
      }

    def apply(name: String): Option[String] =
      unapply(name).orElse {
        Option(name)
      }

    def withoutBackticks(string: String): String =
      if (string == null || string.length <= 1) string
      else string.substring(toDrop(string.head), string.length - toDrop(string.last))

    private def toDrop(char: Char) =
      if (char == BackTick.head) 1 else 0
  }

  def splitName(name: String): Seq[String] = name match {
    case null | "" => Seq.empty
    case _ if name.contains(".") => name.split("\\.").toSeq
    case _ => Seq(name)
  }

  def toJavaName(name: String): String =
    withTransformation(name)(NameTransformer.encode)

  def clean(name: String): String =
    withTransformation(name)(NameTransformer.decode)

  def cleanFqn(fqn: String): String =
    fqnWithTransformation(fqn)(clean)

  def equivalentFqn(l: CharSequence, r: CharSequence): Boolean = {
    val ls = l.toString
    val rs = r.toString
    ls == rs || cleanFqn(ls) == cleanFqn(rs)
  }

  def equivalent(l: String, r: String): Boolean =
    l == r || clean(l) == clean(r)

  def escapeKeywordsFqn(fqn: String): String =
    fqnWithTransformation(fqn)(escapeKeyword)

  def escapeKeywordsFqnParts(fqn: String): Seq[String] =
    fqnPartsWithTransformation(fqn)(escapeKeyword)

  def escapeKeyword(s: String): String =
    if (isKeyword(s)) s"`$s`" else s

  def nameFitToPatterns(qualName: String, patterns: Seq[String], strict: Boolean): Boolean = {
    val (exclude, include) = patterns.partition(_.startsWith(EXCLUDE_PREFIX))

    !exclude.exists(excl => fitToPattern(excl.stripPrefix(EXCLUDE_PREFIX), qualName, strict)) &&
      include.exists(fitToPattern(_, qualName, strict))
  }

  private def withTransformation(name: String)
                                (transformation: String => String) =
    isBacktickedName.withoutBackticks(name) match {
      case null => null
      case scalaName => transformation(scalaName)
    }


  private def fqnPartsWithTransformation(fqn: String)
                                        (transformation: String => String): Seq[String] = {
    val parts = splitName(fqn)
    parts.map(transformation)
  }

  private def fqnWithTransformation(fqn: String)
                                   (transformation: String => String) = {
    val partsTransformed = fqnPartsWithTransformation(fqn)(transformation)
    partsTransformed.mkString(".")
  }

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
