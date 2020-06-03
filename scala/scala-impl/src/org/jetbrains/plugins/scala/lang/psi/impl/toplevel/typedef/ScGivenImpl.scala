package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier

trait ScGivenImpl extends ScGiven  {
  protected def typeElementForAnonymousName: Option[ScTypeElement]

  private lazy val syntheticId = {
    val name = typeElementForAnonymousName.fold("given_unknown")(ScGiven.generateAnonymousGivenName)
    createIdentifier(name).getPsi
  }

  override def nameId: PsiElement = {
    findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)
      .nullSafe
      .getOrElse(syntheticId)
  }
}
