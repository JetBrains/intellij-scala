package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr


import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import parser.ScalaElementTypes
import com.intellij.psi.PsiElement
import com.intellij.util.ReflectionCache
import java.util.{List, ArrayList}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScBlockExprImpl(text: CharSequence) extends LazyParseablePsiElement(ScalaElementTypes.BLOCK_EXPR, text) with ScBlockExpr {
  override def toString: String = "BlockExpression"

  override def isAnonymousFunction: Boolean = caseClauses != None

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): Array[T] = {
    val result: List[T] = new ArrayList[T]
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (ReflectionCache.isInstance(cur, aClass)) result.add(cur.asInstanceOf[T])
      cur = cur.getNextSibling
    }
    return result.toArray[T](java.lang.reflect.Array.newInstance(aClass, result.size).asInstanceOf[Array[T]])
  }

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (ReflectionCache.isInstance(cur, aClass)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    return null
  }
}