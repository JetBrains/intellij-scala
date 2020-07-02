package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.psi.{PsiElement, PsiMember}

abstract class GlobalMembersFinderBase protected(protected val place: PsiElement,
                                                 private val accessAll: Boolean)
                                                (protected val namePredicate: NamePredicate) extends GlobalMembersFinder {

  protected final def isAccessible(member: PsiMember): Boolean =
    accessAll ||
      completion.isAccessible(member)(place)
}
