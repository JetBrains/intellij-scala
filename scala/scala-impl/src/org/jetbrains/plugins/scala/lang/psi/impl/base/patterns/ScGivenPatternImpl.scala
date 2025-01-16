package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScGivenPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
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
  override def nameId: NameId.Immaterial  = new NameId.Immaterial {
    override def isAnonymous: Boolean = false
    override def name: Some[String] = Some(forcedName)
    override def forcedName: String = ScalaPsiUtil.generateGivenName(Seq(typeElement))
    override def forHighlighting: PsiElement = typeElement
    override def prepareToReplace(): PsiElement = {
      // TODO:
      //  in theory this can be done by replacing the whole pattern with a named pattern
      //  aka
      //    case given Int => given_Int
      //  into
      //    case newName@given Int => newName
      throw new UnsupportedOperationException("Cannot rename given pattern")
    }
  }
}
