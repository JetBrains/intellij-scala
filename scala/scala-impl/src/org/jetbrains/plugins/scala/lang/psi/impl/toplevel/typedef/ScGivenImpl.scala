package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.{PsiElement, PsiFile, PsiIdentifier}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameters, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven

trait ScGivenImpl extends ScGiven  {
  protected def typeElementForAnonymousName: Option[ScTypeElement]

  override def givenName: Option[PsiElement] =
    findFirstChildByType(ScalaTokenTypes.tIDENTIFIER).toOption

  override def givenTypeParamClause: Option[ScTypeParamClause] =
    findChild(classOf[ScTypeParamClause])

  override def givenParameters: Option[ScParameters] =
    findChild(classOf[ScParameters])

  override def nameInner: String = {
    def nameFromTypeElement = typeElementForAnonymousName.map(ScGiven.generateAnonymousGivenName)
    val nameFromSpecifiedName = givenName.map(_.getText)

    nameFromSpecifiedName
      .orElse(nameFromTypeElement)
      .getOrElse("given_unknown")
  }

  override def nameId: PsiElement = {
    givenName
      .orElse(typeElementForAnonymousName)
      .getOrElse(this)  // we cannot return null so return the complete given statement
  }

  override def getNameIdentifier: PsiIdentifier = {
    val nameId = this.nameId

    new LightIdentifier(getManager, name) {
      override def getTextRange: TextRange = nameId.getTextRange
      override def getStartOffsetInParent: Int = nameId.getStartOffsetInParent
      override def getTextOffset: Int = nameId.getTextOffset
      override def getContainingFile: PsiFile = nameId.getContainingFile
    }
  }
}
