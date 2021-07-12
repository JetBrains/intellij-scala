package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_12
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

final class PrecedenceTypes private (val defaultImports: Seq[String]) {
  private[this] val defaultImportPrecedence =
    defaultImports.zipWithIndex.map { case (k,v) => (k, v + 1) }.toMap

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
  def defaultImportPrecedence(fqn: String): Option[Int] = defaultImportPrecedence.get(fqn)
  def defaultImportsWithPrecedence: Map[String, Int]    = defaultImportPrecedence

  def importPrecedence(
    place:      PsiElement,
    isPackage:  Boolean,
    isWildcard: Boolean,
    isTopLevel: Boolean
  ): Int =
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

object PrecedenceTypes {
  private val defaultImplicitlyImportedSymbols: Seq[String] =
    Seq("java.lang", "scala", "scala.Predef")

  private val defaultPrecedenceTypes =
    new PrecedenceTypes(defaultImplicitlyImportedSymbols)

  private def mayHaveCustomDefaultImports(e: PsiElement): Boolean =
    e.getContainingFile match {
      case sf: ScalaFile if !sf.isCompiled => true
      case _                               => false
    }

  @CachedInUserData(module, ScalaCompilerConfiguration.modTracker(module.getProject))
  def forModule(module: Module): PrecedenceTypes =
    module.customDefaultImports.fold(defaultPrecedenceTypes)(new PrecedenceTypes(_))

  def forElement(e: PsiElement): PrecedenceTypes =
    e.module.fold(defaultPrecedenceTypes) { m =>
      if (mayHaveCustomDefaultImports(e)) forModule(m)
      else                                defaultPrecedenceTypes
    }
}
