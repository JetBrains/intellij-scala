package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers

/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScObject extends ScTypeDefinition with ScTypedDefinition with ScMember with ScDeclaredElementsHolder {
  //Is this object generated as case class companion module
  private var isSyntheticCaseClassCompanion: Boolean = false
  def isSyntheticObject: Boolean = isSyntheticCaseClassCompanion
  def setSyntheticObject() {
    isSyntheticCaseClassCompanion = true
  }

  def getObjectClassOrTraitToken = findFirstChildByType(ScalaTokenTypes.kOBJECT)

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

  /**
   * @return returns every time the same result, even after modification
   *         so it's reaonable to use it only for Predef and scala classes
   */
  def getHardParameterlessSignatures: TypeDefinitionMembers.ParameterlessNodes.Map

  /**
   * @return returns every time the same result, even after modification
   *         so it's reaonable to use it only for Predef and scala classes
   */
  def getHardTypes: TypeDefinitionMembers.TypeNodes.Map

  /**
   * @return returns every time the same result, even after modification
   *         so it's reaonable to use it only for Predef and scala classes
   */
  def getHardSignatures: TypeDefinitionMembers.SignatureNodes.Map
}