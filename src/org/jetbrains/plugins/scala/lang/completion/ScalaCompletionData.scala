package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._;
import com.intellij.codeInsight.TailType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.codeInsight.completion.DefaultInsertHandler;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.TextFilter;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.position.LeftNeighbour;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.completion.filters.LeftLeftNeighbour;
import org.jetbrains.plugins.scala.lang.completion.filters.BeforeDotFilter;
import _root_.scala.collection.mutable._;
/*
* @author Ilya Sergey
*/

class ScalaCompletionData extends CompletionData {

/* Adding variants */
    init

  /*
  * Adding completion variants after dot
  */
  def afterDotCompletion = {

    def register(after: IElementType, handler: DefaultInsertHandler, elems: String*) = {
      var andFilter = new AndFilter(
        new LeftNeighbour(new TextFilter(".")),
        new LeftLeftNeighbour(new BeforeDotFilter(after))
      )
      var variant = new CompletionVariant(andFilter)
      if (handler != null) variant.setInsertHandler(handler)
      variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);
      for (val el <- elems) variant.addCompletion(el)
      registerVariant(variant)
    }

    register(ScalaTokenTypes.tIDENTIFIER,
             new DefaultInsertHandler(),
             ScalaKeyword.THIS,
             ScalaKeyword.SUPER)
    register(ScalaTokenTypes.tIDENTIFIER,
             null,
             ScalaKeyword.TYPE)
    register(ScalaTokenTypes.kTHIS,
             null,
             ScalaKeyword.TYPE)
  }

  def init = {

    /* Special cases */
    afterDotCompletion

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
    registerVariant(variant);

  }

  override def findPrefix(insertedElement : PsiElement, offset: Int) : String = {
    WordCompletionData.findPrefixSimple(insertedElement, offset)
  }

}
