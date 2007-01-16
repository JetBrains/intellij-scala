package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._;
import com.intellij.codeInsight.TailType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.TextFilter;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.position.LeftNeighbour;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
/*
* @author Ilya Sergey
*
*/

class ScalaCompletionData extends CompletionData {

  val afterDotFilter = new LeftNeighbour(new TextFilter("."))
  var variant = new CompletionVariant(new NotFilter(afterDotFilter));
  variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);
  variant.addCompletionFilterOnElement(TrueFilter.INSTANCE)

  var keywords = Array (
    "true"
    ,"false"
    ,"null"
    ,"abstract"
    ,"case"
    ,"catch"
    ,"class"
    ,"def"
    ,"do"
    ,"else"
    ,"extends"
    ,"final"
    ,"finally"
    ,"for"
    ,"if"
    ,"implicit"
    ,"import"
    ,"match"
    ,"new"
    ,"object"
    ,"override"
    ,"package"
    ,"private"
    ,"protected"
    ,"requires"
    ,"return"
    ,"sealed"
    ,"super"
    ,"this"
    ,"throw"
    ,"trait"
    ,"try"
    ,"type"
    ,"val"
    ,"var"
    ,"while"
    ,"with"
    ,"yield"
  )

  variant.addCompletion(keywords);
//  variant.setInsertHandler( new DefaultInsertHandler() );
  registerVariant(variant);

  override def findPrefix(insertedElement : PsiElement, offset: Int) : String = {
    WordCompletionData.findPrefixSimple(insertedElement, offset)
  }


}
