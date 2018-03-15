package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import javax.swing.Icon

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScDecoratedIconOwner}

/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScObject extends ScTypeDefinition with ScTypedDefinition with ScMember with ScDeclaredElementsHolder with ScDecoratedIconOwner {

  override protected def getBaseIcon(flags: Int): Icon =
    if (isPackageObject) Icons.PACKAGE_OBJECT else Icons.OBJECT

  //Is this object generated as case class companion module
  private var isSyntheticCaseClassCompanion: Boolean = false
  def isSyntheticObject: Boolean = isSyntheticCaseClassCompanion
  def setSyntheticObject() {
    isSyntheticCaseClassCompanion = true
  }

  def getObjectToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kOBJECT)

  def getObjectClassOrTraitToken: PsiElement = getObjectToken

  def declaredElements = Seq(this)

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