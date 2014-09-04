package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeArgsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeArgs {
  override def toString: String = "TypeArgumentsList"

  //todo: this code is too complex to save epsilon% of performance?
  def typeArgs: Seq[ScTypeElement] = {
    var count: Int = 0
    val children = getChildren
    var i: Int = 0
    val size = children.length
    while (i < size) {
      val child = children(i)
      if (TokenSets.TYPE_ELEMENTS_TOKEN_SET.contains(child.getNode.getElementType)) {
        count += 1
      }
      i += 1
    }
    val result = JavaArrayFactoryUtil.ScTypeElementFactory.create(count)
    if (count > 0) {
      count = 0
      var i: Int = 0
      val size = children.length
      while (i < size) {
        val child = children(i)
        if (TokenSets.TYPE_ELEMENTS_TOKEN_SET.contains(child.getNode.getElementType)) {
          result(count) = child.asInstanceOf[ScTypeElement]
          count += 1
        }
        i += 1
      }
    }
    result.toSeq
  }
}