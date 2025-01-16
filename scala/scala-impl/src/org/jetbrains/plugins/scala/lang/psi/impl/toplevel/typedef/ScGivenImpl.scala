package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven

trait ScGivenImpl extends ScGiven {
  def givenToken: PsiElement =
    findFirstChildByType(ScalaTokenType.GivenKeyword).get

  override def nameElement: Option[PsiElement] =
    findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)
}

object ScGivenImpl {
  abstract class GivenNameId[G <: ScGivenImpl](final val givenImpl: G) extends NameId.NonAnonymous {
    def generateName: String

    override def name: Some[String] =
      Some(explicitName.getOrElse(generateName))

    override def explicitName: Option[String] = explicitIdentifier.map(_.getText)
    override def forcedName: String = name.value
    override def explicitIdentifier: Option[PsiElement] = givenImpl.nameElement
    override def place: Option[PsiElement] = explicitIdentifier
    override def isElement(element: PsiElement): Boolean = explicitIdentifier.contains(element)

    override def prepareToReplace(): PsiElement =
      throw new UnsupportedOperationException("Not supported yet.")
  }
}