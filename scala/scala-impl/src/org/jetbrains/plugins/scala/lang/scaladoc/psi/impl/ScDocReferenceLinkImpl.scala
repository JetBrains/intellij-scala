package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.ILazyParseableElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocReferenceLink
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefQuery

import java.util

final class ScDocReferenceLinkImpl(elementType: ILazyParseableElementType, buffer: CharSequence)
  extends LazyParseablePsiElement(elementType, buffer) with ScDocReferenceLink {

  override def query: ScDocRefQuery = findChild[ScDocRefQuery].get

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): Array[T] = {
    val result = new util.ArrayList[T]
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) result.add(cur.asInstanceOf[T])
      cur = cur.getNextSibling
    }
    result.toArray[T](java.lang.reflect.Array.newInstance(aClass, result.size).asInstanceOf[Array[T]])
  }

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](aClass: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (aClass.isInstance(cur)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    null
  }

  override def toString: String = "ScDocReferenceLink"
}
