package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import java.util

import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.ILazyParseableElementType
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.annotator.ScExpressionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/
class ScBlockExprImpl(elementType: ILazyParseableElementType, buffer: CharSequence)
  extends LazyParseablePsiElement(elementType, buffer)
    with ScBlockExpr with ScExpressionAnnotator {

  //todo: bad architecture to have it duplicated here, as ScBlockExprImpl is not instance of ScalaPsiElementImpl
  override def getContext: PsiElement = {
    context match {
      case null => super.getContext
      case _ => context
    }
  }

  override def toString: String = "BlockExpression"

  override def hasCaseClauses: Boolean = caseClauses.isDefined

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): Array[T] = {
    val result = new util.ArrayList[T]
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) result.add(cur.asInstanceOf[T])
      cur = cur.getNextSibling
    }
    result.toArray[T](java.lang.reflect.Array.newInstance(aClass, result.size).asInstanceOf[Array[T]])
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    null
  }

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitBlockExpression(this)

  override def accept(visitor: PsiElementVisitor): Unit = visitor match {
    case scalaVisitor: ScalaElementVisitor => accept(scalaVisitor)
    case _ => super.accept(visitor)
  }
}