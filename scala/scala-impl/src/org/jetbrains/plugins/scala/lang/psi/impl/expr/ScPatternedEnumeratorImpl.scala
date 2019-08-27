package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPatternedEnumerator
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

trait ScPatternedEnumeratorImpl
  extends ScalaPsiElementImpl
    with ScEnumeratorImpl
    with ScPatternedEnumerator {

  override def pattern: ScPattern = findChildByClassScala(classOf[ScPattern])

  override def valKeyword: Option[PsiElement] = Option(getNode.findChildByType(ScalaTokenTypes.kVAL)).map(_.getPsi)

  override def caseKeyword: Option[PsiElement] = Option(getNode.findChildByType(ScalaTokenTypes.kCASE)).map(_.getPsi)
}
