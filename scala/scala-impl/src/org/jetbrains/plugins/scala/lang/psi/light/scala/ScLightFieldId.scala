package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.light.LightUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * @author Alefas
 * @since 04/04/14.
 */
class ScLightFieldId(rt: ScType, val f: ScFieldId)
  extends LightElement(f.getManager, f.getLanguage) with ScFieldId {

  override def nameId: PsiElement = f.nameId

  override def toString: String = f.toString

  override def getNavigationElement: PsiElement = LightUtil.originalNavigationElement(f)

  override def navigate(requestFocus: Boolean): Unit = f.navigate(requestFocus)

  override def canNavigate: Boolean = f.canNavigate

  override def canNavigateToSource: Boolean = f.canNavigateToSource

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    throw new UnsupportedOperationException("Operation on light element")

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    throw new UnsupportedOperationException("Operation on light element")

  override def `type`(): TypeResult = Right(rt)

  override def getParent: PsiElement = f.getParent //to find right context
}
