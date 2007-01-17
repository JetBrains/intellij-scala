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
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.position._;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.completion.filters._;
import _root_.scala.collection.mutable._;
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.top._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.parser._

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

  /*
  *  Keyword completion on top-level of file
  */
  def topDefinitionsCompletion = {
    val afterDotFilter = new LeftNeighbour(new TextFilter("."))
    var variant = new CompletionVariant(new NotFilter(afterDotFilter));
    variant.includeScopeClass(classOf[ScPackaging].asInstanceOf[java.lang.Class[ScPackaging]], true);
    variant.includeScopeClass(classOf[ScalaFile].asInstanceOf[java.lang.Class[ScalaFile]], true);
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

  /* keyword "requires" completion */
  def requiresCompletion = {
    val filter = new LeftNeighbour(new SuperParentFilter(new ElementTypeFilter(ScalaElementTypes.CLASS_DEF)))
    val variant = new CompletionVariant(filter);
    variant.includeScopeClass(classOf[ScalaFile].asInstanceOf[java.lang.Class[ScalaFile]], true);
    variant.addCompletion(ScalaKeyword.REQUIRES)
    registerVariant(variant)
  }

  /*
  * Keyword completion in expressions
  */
  def inExpressionCompletion = {
    val afterDotFilter = new LeftNeighbour(new TextFilter("."))
    var variant = new CompletionVariant(new NotFilter(afterDotFilter));
//    variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);
    variant.includeScopeClass(classOf[ScPsiExprImpl].asInstanceOf[java.lang.Class[ScPsiExprImpl]], false);
    variant.addCompletionFilterOnElement(TrueFilter.INSTANCE)
    var keywords = Array (
      "true"
      ,"false"
      ,"null"
      ,"abstract"
      ,"case"
      ,"catch"
      ,"def"
      ,"do"
      ,"else"
      ,"extends"
      ,"final"
      ,"finally"
      ,"for"
      ,"if"
      ,"implicit"
      ,"match"
      ,"new"
      ,"override"
      ,"private"
      ,"protected"
      ,"return"
      ,"sealed"
      ,"super"
      ,"this"
      ,"throw"
      ,"try"
      ,"type"
      ,"val"
      ,"var"
      ,"while"
      ,"with"
      ,"yield"
    )

    variant.addCompletion(keywords)
    registerVariant(variant)
  }

  /*
  *   Main initialization
  */
  def init = {

    /* Special cases */
    afterDotCompletion
    topDefinitionsCompletion
    requiresCompletion
    inExpressionCompletion



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
