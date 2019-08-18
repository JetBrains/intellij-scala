package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_13
import org.jetbrains.plugins.scala.project._

object PrecedenceTypes {
  val PREFIX_COMPLETION: Int       = 0
  val JAVA_LANG: Int               = 1
  val SCALA: Int                   = 2
  val SCALA_PREDEF: Int            = 3
  val PACKAGE_LOCAL_PACKAGE: Int   = 4
  val WILDCARD_IMPORT_PACKAGE: Int = 5
  val IMPORT_PACKAGE: Int          = 6
  val PACKAGE_LOCAL: Int           = 7

  def OTHER_MEMBERS(target: PsiElement, place: PsiElement): Int =
    if (target.scalaLanguageLevelOrDefault >= Scala_2_13) {
      val sameFile = target.getContainingFile == place.getContainingFile

      if (sameFile) 11
      else          8
    } else 11

  val otherMembersValuesSet: Set[Int] = Set(8, 11)

  val WILDCARD_IMPORT: Int         = 9
  val IMPORT: Int                  = 10
}
