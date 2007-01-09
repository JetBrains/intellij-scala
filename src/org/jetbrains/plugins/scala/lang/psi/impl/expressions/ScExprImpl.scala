package org.jetbrains.plugins.scala.lang.psi.impl.expressions
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiExpression
import org.jetbrains.plugins.scala.lang.psi._

abstract class ScPsiExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node)
                                           with PsiExpression {}