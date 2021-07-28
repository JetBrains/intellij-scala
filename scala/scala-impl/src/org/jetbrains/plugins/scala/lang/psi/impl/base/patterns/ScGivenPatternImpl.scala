package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScGivenPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

final class ScGivenPatternImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScPatternImpl
    with ScGivenPattern
    with TypedPatternLikeImpl {

  override def typeElement: Option[ScTypeElement] = findChild[ScTypeElement]

  override def `type`(): TypeResult = typeElement match {
    case Some(te) => te.`type`()
    case None     => Failure("ScGivenPattern without type element.")
  }

  override def isWildcard: Boolean = false
  override def nameId: PsiElement  = typeElement.orNull
  override def name: String        = typeElement.fold("")(ScGiven.generateAnonymousGivenName)
}
