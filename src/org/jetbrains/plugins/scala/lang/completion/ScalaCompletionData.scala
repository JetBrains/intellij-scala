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
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._

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
      val variant = new CompletionVariant(andFilter)
      if (handler != null) variant.setInsertHandler(handler)
      variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);
      for (val el <- elems) variant.addCompletion(el)
      registerVariant(variant)
    }

    register(ScalaTokenTypes.tIDENTIFIER,
             z DefaultInsertHandler(),
             ScalaKeyword.THIS,
             ScalaKeyword.SUPER)
    register(ScalaTokenTypes.tIDENTIFIER,
             null,
             ScalaKeyword.TYPE)
    register(ScalaTokenTypes.kTHIS,
             null,
             ScalaKeyword.TYPE)
  }

  /*
  *  Keyword completion on top=level of file
  */
  def topDefinitionsCompletion = {
    val afterDotFilter = new LeftNeighbour(new TextFilter("."))
    val variant = new CompletionVariant(new NotFilter(afterDotFilter));
    variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);
    addCompletions(
      variant,

      ScalaKeyword.CLASS,
      ScalaKeyword.OBJECT,
      ScalaKeyword.TRAIT,
      ScalaKeyword.IMPORT,
      ScalaKeyword.PACKAGE
    )
    registerVariant(variant)
  }



  def init = {

    /* Special cases */
    afterDotCompletion

    val afterDotFilter = new LeftNeighbour(new TextFilter("."))
    var variant = new CompletionVariant(new NotFilter(afterDotFilter));
    variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);

    //variant.includeScopeClass(classOf[Template].asInstanceOf[java.lang.Class[Template]], true);

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

  /**
  * Adds all completion variants in sequence
  */
  def addCompletions(variant: CompletionVariant, comps: String*) = {
    for (val el <- comps) variant.addCompletion(el)
  }

}
