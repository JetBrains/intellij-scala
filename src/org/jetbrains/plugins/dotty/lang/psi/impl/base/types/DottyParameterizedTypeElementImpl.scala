package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.api.base.types.DottyDesugarizableTypeElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScWildcardTypeElement}

/**
  * @author adkozlov
  */
class DottyParameterizedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node)
  with ScParameterizedTypeElement with DottyDesugarizableTypeElement {
  override def desugarizedText: String = {
    val designatorText = typeElement.getText

    val arguments = typeArgList.typeArgs.map {
      case wildcardElement: ScWildcardTypeElement if wildcardElement.hasBounds => wildcardElement.boundsText
      case _: ScWildcardTypeElement => ""
      case element => s"= ${element.getText}"
    }
      .zipWithIndex
      .filter {
        case (t, _) => t.nonEmpty
      }
      .map {
        case (t, i) => s"type $designatorText$$_$i $t"
      }
    s"$designatorText ${arguments.mkString("{", ";", "}")}"
  }
}
