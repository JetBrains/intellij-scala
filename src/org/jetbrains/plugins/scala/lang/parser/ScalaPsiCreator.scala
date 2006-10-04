package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.parser._;
import org.jetbrains.plugins.scala.lang.psi.impl._;
import com.intellij.psi.PsiElement

/**
 * User: Dmitry.Krasilschikov
 * Date: 03.10.2006
 * Time: 19:28:50
 */
object ScalaPsiCreator {
  def create (node : ASTNode) : PsiElement = {
    val elType = node.getElementType()
    /*
    if (elType.equals(ScalaElementTypes.TYPE))
      new ScClass(node)
    */
    return null  
  }
}
