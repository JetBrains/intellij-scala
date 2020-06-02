package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScGivenSignature

class ScGivenSignatureImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScGivenSignature
{

}
