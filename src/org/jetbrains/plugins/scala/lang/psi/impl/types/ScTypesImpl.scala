package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi._

trait ScTypes extends PsiElement

class ScTypesImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with ScTypes {
      override def toString: String = "Types list"
}