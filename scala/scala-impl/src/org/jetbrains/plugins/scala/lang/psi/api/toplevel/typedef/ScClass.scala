package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.PsiElement
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDecoratedIconOwner, ScFunction}

/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScClass extends ScTypeDefinition with ScConstructorOwner with ScDecoratedIconOwner {

  override protected def getBaseIcon(flags: Int): Icon =
    if (this.hasAbstractModifier) Icons.ABSTRACT_CLASS
    else Icons.CLASS

  def typeParamString: String = typeParameters
    .map(ScalaPsiUtil.typeParamString) match {
    case Seq() => ""
    case seq => seq.mkString("[", ", ", "]")
  }

  def tooBigForUnapply: Boolean = constructor.exists(_.parameters.length > 22)

  def getSyntheticImplicitMethod: Option[ScFunction]

  def getClassToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kCLASS)

  def getObjectClassOrTraitToken: PsiElement = getClassToken

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitClass(this)
}
