package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_12
import org.jetbrains.plugins.scala.project._

object PrecedenceTypes {
  val PREFIX_COMPLETION: Int       = 0
  val JAVA_LANG: Int               = 1
  val SCALA: Int                   = 2
  val SCALA_PREDEF: Int            = 3
  val PACKAGE_LOCAL_PACKAGE: Int   = 4
  val WILDCARD_IMPORT_PACKAGE: Int = 5
  val IMPORT_PACKAGE: Int          = 6

  val PACKAGING: Int               = 7

  val WILDCARD_IMPORT_TOP: Int     = 8
  val IMPORT_TOP: Int              = 9

  //declarations available by packaging clause, but in another file
  val SAME_PACKAGE: Int            = 10

  val WILDCARD_IMPORT: Int         = 11
  val IMPORT: Int                  = 12

  //declarations that are local, inherited, or in the same file and available by packaging clause
  val OTHER_MEMBERS: Int           = 13

  def importPrecedence(place: PsiElement)
                      (isPackage: Boolean, isWildcard: Boolean, isTopLevel: Boolean): Int = {

    if (isPackage) {
      if (isWildcard) WILDCARD_IMPORT_PACKAGE else IMPORT_PACKAGE
    }
    //Pre 2.13 scalac did not conform to the spec, and preferred definitions in the same package
    //over top-level imports
    else if (isTopLevel && place.scalaLanguageLevelOrDefault <= Scala_2_12) {
      if (isWildcard) WILDCARD_IMPORT_TOP else IMPORT_TOP
    }
    else {
      if (isWildcard) WILDCARD_IMPORT else IMPORT
    }
  }
}
