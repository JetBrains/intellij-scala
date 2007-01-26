/*
package org.jetbrains.plugins.scala.lang.psi.impl

import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr

import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.GeneratedMarkerVisitor
import com.intellij.util.IncorrectOperationException
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiManager

class ScalaPsiElementFactory (manager : PsiManager ) {

  def createElement() = {

  }
  */
/*
  def createExpressionFromText(text : String , context: PsiElement ) : PsiExpression = {
    val treeHolder : FileElement = new DummyHolder(manager, context).getTreeElement();
    val psiManager : PsiManager = treeHolder.getManager()

    val treeElement : CompositeElement = Expr.parseExpressionText(psiManager, text, treeHolder.getCharTable());

    if (treeElement == null) throw new IncorrectOperationException("Incorrect expression \"" + text + "\".");

    TreeUtil.addChildren(treeHolder, treeElement)
    treeHolder.acceptTree(new GeneratedMarkerVisitor())

    SourceTreeToPsiMap.treeElementToPsi(treeElement).asInstanceOf[PsiExpression]
  }
  */
/*


}*/
