package org.jetbrains.plugins.scala.lang.scalacli.psi.impl.inner

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scalacli.psi.api.inner.ScCliDirectiveValue

class ScCliDirectiveValueImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCliDirectiveValue
