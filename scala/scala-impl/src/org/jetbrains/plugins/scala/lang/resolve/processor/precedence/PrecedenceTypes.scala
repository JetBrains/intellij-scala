package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_12
import org.jetbrains.plugins.scala.project._

class PrecedenceTypes(place: PsiElement) {
  private[this] val defaultImports = place.defaultImports.mapValues(_ + 1)

  val defaultImportsFqns: Set[String] = defaultImports.keySet
  val defaultImportMaxPrecedence: Int = defaultImports.size

  val PREFIX_COMPLETION: Int       = 0

  val PACKAGE_LOCAL_PACKAGE: Int   = defaultImportMaxPrecedence + 1
  val WILDCARD_IMPORT_PACKAGE: Int = defaultImportMaxPrecedence + 2
  val IMPORT_PACKAGE: Int          = defaultImportMaxPrecedence + 3

  val PACKAGING: Int               = defaultImportMaxPrecedence + 4

  val WILDCARD_IMPORT_TOP: Int     = defaultImportMaxPrecedence + 5
  val IMPORT_TOP: Int              = defaultImportMaxPrecedence + 6

  //declarations available by packaging clause, but in another file
  val SAME_PACKAGE: Int            = defaultImportMaxPrecedence + 7

  val WILDCARD_IMPORT: Int         = defaultImportMaxPrecedence + 8
  val IMPORT: Int                  = defaultImportMaxPrecedence + 9

  //declarations that are local, inherited, or in the same file and available by packaging clause
  val OTHER_MEMBERS: Int           = defaultImportMaxPrecedence + 10

  /**
   * Returns precedence (between 1 and [[defaultImportMaxPrecedence]]) of an implicitly imported symbol,
   * either from the predefined set or specified by `-Yimports`/`-Yno-imports`/`-Yno-predef`
   * compiler options.
   */
  def defaultImportPrecedence(fqn: String): Option[Int] = defaultImports.get(fqn)
  def defaultImportsWithPrecedence: Map[String, Int]    = defaultImports

  def importPrecedence(isPackage: Boolean, isWildcard: Boolean, isTopLevel: Boolean): Int =
    if (isPackage) {
      if (isWildcard) WILDCARD_IMPORT_PACKAGE
      else            IMPORT_PACKAGE
    }
    //Pre 2.13 scalac did not conform to the spec, and preferred definitions in the same package
    //over top-level imports
    else if (isTopLevel && place.scalaLanguageLevelOrDefault <= Scala_2_12) {
      if (isWildcard) WILDCARD_IMPORT_TOP
      else            IMPORT_TOP
    } else {
      if (isWildcard) WILDCARD_IMPORT
      else            IMPORT
    }
}
