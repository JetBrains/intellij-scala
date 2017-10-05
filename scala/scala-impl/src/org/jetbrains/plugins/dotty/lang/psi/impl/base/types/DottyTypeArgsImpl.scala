package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.api.base.types.{DottyTypeArgs, DottyTypeArgumentNameElement}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScTypeArgsImpl

import scala.collection.mutable

/**
  * @author adkozlov
  */
class DottyTypeArgsImpl(node: ASTNode) extends ScTypeArgsImpl(node) with DottyTypeArgs {
  override def argumentsNames: mutable.ArraySeq[DottyTypeArgumentNameElement] = getChildren.collect {
    case element: DottyTypeArgumentNameElement => element
  }
}
