package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder

trait ScObject extends ScTypeDefinition
  with ScTypedDefinition
  with ScMember
  with ScDeclaredElementsHolder
  with ScDerivesClauseOwner {

  //Is this object generated as case class companion module
  private var flag = false

  def isSyntheticObject: Boolean = flag

  //noinspection AccessorLikeMethodIsUnit
  def isSyntheticObject_=(flag: Boolean): Unit = {
    this.flag = flag
  }

  override def declaredElements: Seq[ScObject] = Seq(this)

  def hasPackageKeyword: Boolean

  def fakeCompanionClass: Option[PsiClass]

  def fakeCompanionClassOrCompanionClass: PsiClass

  /** Is this object accessible from a stable path from the root package? */
  def isStatic: Boolean = containingClass match {
    case obj: ScObject => obj.isStatic
    case null => true
    case _ => false
  }
}

object ScObject {
  object withModifierList {
    def unapply(obj: ScObject): Some[ScModifierList] = Some(obj.getModifierList)
  }
}