package org.jetbrains.plugins.scala.lang.completion.filters

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.TextFilter;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.position.LeftNeighbour;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;


class BeforeDotFilter(elems : IElementType*) extends LeftNeighbour {

  def contains[T](seq: Seq[T], elem: T): Boolean = seq.contains(elem)

  override def isAcceptable (element: java.lang.Object, context: PsiElement): Boolean =
    element match {
      case psi : PsiElement => contains(elems, psi.getNode.getElementType)
      case _ => false
    }
}