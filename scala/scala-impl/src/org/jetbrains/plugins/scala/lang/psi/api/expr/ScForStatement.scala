package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScForStatement extends ScExpression {
  /**
    * @param forDisplay true if the desugaring is intended for being shown to the user,
    *                   false if it is intented for the type system.
    */
  def getDesugaredExpr(forDisplay: Boolean = false) : Option[ScExpression]

  def getDesugaredPatternAnalog(pattern: ScPattern): Option[ScPattern]

  def getDesugaredEnumeratorAnalog(enumerator: ScEnumerator): Option[ScEnumerator.Analog]

  def isYield: Boolean

  def enumerators: Option[ScEnumerators]

  def patterns: Seq[ScPattern]

  def body: Option[ScExpression] = findChild(classOf[ScExpression])

  def getLeftParenthesis: Option[PsiElement]

  def getRightParenthesis: Option[PsiElement]

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitForExpression(this)
  }
}

object ScForStatement {
  def unapply(forStmt: ScForStatement): Option[(ScEnumerators, ScExpression)] = {
    forStmt.enumerators.zip(forStmt.body).headOption
  }
}