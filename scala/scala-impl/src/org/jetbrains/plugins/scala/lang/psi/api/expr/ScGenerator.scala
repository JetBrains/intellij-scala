package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScEnumeratorImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
trait ScGenerator extends ScEnumerator with ScPatterned with ScEnumeratorImpl {
  def expr: Option[ScExpression]

  def valKeyword: Option[PsiElement]

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitGenerator(this)
  }
}

object ScGenerator {
  def unapply(gen: ScGenerator): Option[(ScPattern, Option[ScExpression])] = Some(gen.pattern -> gen.expr)
}