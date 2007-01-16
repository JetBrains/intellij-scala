package org.jetbrains.plugins.scala.lang.psi.impl.expressions
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.source.ChildRole
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes


abstract class ScPsiExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node)