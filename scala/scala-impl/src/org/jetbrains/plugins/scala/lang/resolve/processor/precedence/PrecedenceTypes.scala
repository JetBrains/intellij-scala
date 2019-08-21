package org.jetbrains.plugins.scala.lang.resolve.processor.precedence

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
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
  val OTHER_MEMBERS: Int           = 8
  val WILDCARD_IMPORT: Int         = 9
  val IMPORT: Int                  = 10
  val OTHER_MEMBERS_SAME_UNIT: Int = 11

  val otherMembersValuesSet: Set[Int] = Set(OTHER_MEMBERS, OTHER_MEMBERS_SAME_UNIT)

  /**
   * Pre 2.13 scalac did not conform to the spec, and treated all
   * declarations that are local, inherited, or made available by a package clause
   * as if they were defined in the same unit, and thus having higher precedence than
   * imported ones.
   */
  def OTHER_MEMBERS(target: PsiElement, place: PsiElement): Int =
    if (target.scalaLanguageLevelOrDefault >= Scala_2_13) {
      val sameFile = ScalaPsiUtil.fileContext(target) == ScalaPsiUtil.fileContext(place)

      if (sameFile) OTHER_MEMBERS_SAME_UNIT
      else          OTHER_MEMBERS
    } else OTHER_MEMBERS_SAME_UNIT
}
