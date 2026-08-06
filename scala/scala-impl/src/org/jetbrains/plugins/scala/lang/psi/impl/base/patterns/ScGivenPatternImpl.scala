package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScGivenPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

final class ScGivenPatternImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScPatternImpl
    with ScGivenPattern
    with TypedPatternLikeImpl {

  override def typeElement: ScTypeElement = findChild[ScTypeElement].get
  override def `type`(): TypeResult       = typeElement.`type`()
  override def isWildcard: Boolean        = false
  override def nameId: PsiElement         = typeElement
  override def name: String               = ScalaPsiUtil.generateGivenName(typeElement)
}
