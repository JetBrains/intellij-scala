package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._;
import com.intellij.codeInsight.TailType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.codeInsight.completion.DefaultInsertHandler;
import com.intellij.psi.filters._;
import com.intellij.psi.filters.position._;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.completion.filters._;
import org.jetbrains.plugins.scala.lang.completion.filters.toplevel._
import org.jetbrains.plugins.scala.lang.completion.filters.expression._
import org.jetbrains.plugins.scala.lang.completion.filters.modifiers._
import org.jetbrains.plugins.scala.lang.completion.filters.definitions._
import _root_.scala.collection.mutable._;
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.completion.filters.other._

/*
* @author Ilya Sergey
*/

class ScalaCompletionData extends CompletionData {
  
  /* Initialization */
  registerAllCompletions

  private def registerAllCompletions = {
    registerPackageCompletion
    registerExpressionCompletion
    registerModifiersCompletion
    registerCaseCompletion
    registerImportCompletion
    registerTemplateDefinitionCompletion
    registerDefinitionsCompletion
    registerValueDefinitionCompletion
    registerStatementCompletion
    registerCatchCompletion
    registerFinallyCompletion
    registerElseCompletion
    registerExtendsCompletion
    registerYieldCompletion
    registerWithCompletion
    registerRequiresCompletion
    registerIfCompletion
    registerDefTypeCompletion
    registerForSomeCompletion
  }

  private def registerPackageCompletion {
    registerStandardCompletion(new PackageFilter(), "package")
  }

  private def registerExpressionCompletion {
    registerStandardCompletion(new ExpressionFilter, "true", "false", "null", "new", "super", "this")
  }

  private def registerModifiersCompletion {
    registerStandardCompletion(new ModifiersFilter, "private", "protected", "override",
    "abstract", "final", "sealed", "implicit", "lazy")
  }

  private def registerCaseCompletion {
    registerStandardCompletion(new CaseFilter, "case")
  }

  private def registerImportCompletion {
    registerStandardCompletion(new ImportFilter, "import")
  }

  private def registerTemplateDefinitionCompletion {
    registerStandardCompletion(new TemplateFilter, "class", "object", "trait")
  }

  private def registerDefinitionsCompletion {
    registerStandardCompletion(new DefinitionsFilter, "val", "var")
  }

  private def registerValueDefinitionCompletion {
    registerStandardCompletion(new ValueDefinitionFilter, "val")
  }

  private def registerStatementCompletion {
    registerStandardCompletion(new StatementFilter, "for", "while", "do", "try", "return", "throw", "if")
  }

  private def registerCatchCompletion {
    registerStandardCompletion(new CatchFilter, "catch")
  }

  private def registerFinallyCompletion {
    registerStandardCompletion(new FinallyFilter, "finally")
  }

  private def registerElseCompletion {
    registerStandardCompletion(new ElseFilter, "else")
  }

  private def registerExtendsCompletion {
    registerStandardCompletion(new ExtendsFilter, "extends")
  }

  private def registerYieldCompletion {
    registerStandardCompletion(new YieldFilter, "yield")
  }

  private def registerWithCompletion {
    registerStandardCompletion(new WithFilter, "with")
  }

  private def registerRequiresCompletion {
    registerStandardCompletion(new RequiresFilter, "requires")
  }

  private def registerIfCompletion {
    registerStandardCompletion(new IfFilter, "if")
  }

  private def registerDefTypeCompletion {
    registerStandardCompletion(new DefTypeFilter, "def", "type")
  }

  private def registerForSomeCompletion {
    registerStandardCompletion(new ForSomeFilter, "forSome")
  }

  private def registerStandardCompletion(filter: ElementFilter, keywords: String*) {
    val afterDotFilter = new LeftNeighbour(new TextFilter("."))
    val variant = new CompletionVariant(new AndFilter(new NotFilter(afterDotFilter), filter))
    variant.includeScopeClass(classOf[LeafPsiElement])
    addCompletions(variant, keywords: _*)
    registerVariant(variant);
  }

  /*
  * Adding completion variants after dot
  */
  def afterDotCompletion = {

    def register(after: IElementType, handler: DefaultInsertHandler, elems: String*) = {
      var andFilter = new AndFilter(new LeftNeighbour(new TextFilter(".")),
      new LeftLeftNeighbour(new BeforeDotFilter(after)))
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
    /*
        variant.includeScopeClass(classOf[ScPackaging].asInstanceOf[java.lang.Class[ScPackaging]], true);
        variant.includeScopeClass(classOf[ScalaFile].asInstanceOf[java.lang.Class[ScalaFile]], true);
    */
    variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);
    addCompletions(variant,
    ScalaKeyword.CLASS,
    ScalaKeyword.OBJECT,
    ScalaKeyword.TRAIT,
    ScalaKeyword.IMPORT,
    ScalaKeyword.PACKAGE,
    ScalaKeyword.ABSTRACT,
    ScalaKeyword.SEALED,
    ScalaKeyword.FINAL,
    ScalaKeyword.IMPLICIT,
    ScalaKeyword.PRIVATE,
    ScalaKeyword.PROTECTED)
    registerVariant(variant)
  }

  /* keyword "requires" completion */
  def requiresCompletion = {
    var filter = new OrFilter()
    filter.addFilter(new AndFilter(new LeftNeighbour(new SuperParentFilter(new ElementTypeFilter(ScalaElementTypes.CLASS_DEF, "requires"))),
    new LeftNeighbour(new SuperParentFilter(new ElementTypeFilter(ScalaElementTypes.CLASS_DEF, "extends")))))
    filter.addFilter(new AndFilter(new LeftNeighbour(new SuperParentFilter(new ElementTypeFilter(ScalaElementTypes.TRAIT_DEF, "requires"))),
    new LeftNeighbour(new SuperParentFilter(new ElementTypeFilter(ScalaElementTypes.TRAIT_DEF, "extends")))))
    var filter1 = new AndFilter(filter, new NotFilter(new LeftNeighbour(new TextFilter("class"))))
    filter1 = new AndFilter(filter1, new NotFilter(new LeftNeighbour(new TextFilter("trait"))))
    filter1 = new AndFilter(filter1, new NotFilter(new LeftNeighbour(new TextFilter("."))))

    val variant = new CompletionVariant(filter1);
    //variant.includeScopeClass(classOf[ScalaFile].asInstanceOf[java.lang.Class[ScalaFile]], true);
    variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);
    // Exclude all unnecessary

    variant.addCompletion(ScalaKeyword.REQUIRES)
    registerVariant(variant)
  }

  /* keyword "extends" completion */
  def extendsCompletion = {
    var filter = new OrFilter()
    filter.addFilter(new LeftNeighbour(new SuperParentFilter(new ElementTypeFilter(ScalaElementTypes.CLASS_DEF, "extends"))))
    filter.addFilter(new LeftNeighbour(new SuperParentFilter(new ElementTypeFilter(ScalaElementTypes.OBJECT_DEF, "extends"))))
    filter.addFilter(new LeftNeighbour(new SuperParentFilter(new ElementTypeFilter(ScalaElementTypes.TRAIT_DEF, "extends"))))
    var filter1 = new AndFilter(filter, new NotFilter(new LeftNeighbour(new TextFilter("class"))))
    filter1 = new AndFilter(filter1, new NotFilter(new LeftNeighbour(new TextFilter("trait"))))
    filter1 = new AndFilter(filter1, new NotFilter(new LeftNeighbour(new TextFilter("object"))))
    filter1 = new AndFilter(filter1, new NotFilter(new LeftNeighbour(new TextFilter("requires"))))
    filter1 = new AndFilter(filter1, new NotFilter(new LeftNeighbour(new TextFilter("."))))
    val variant = new CompletionVariant(filter1);
    //variant.includeScopeClass(classOf[ScalaFile].asInstanceOf[java.lang.Class[ScalaFile]], true);
    variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);
    // Exclude all unnecessary

    variant.addCompletion(ScalaKeyword.EXTENDS)
    registerVariant(variant)
  }

  /*
  * Keyword completion in expressions
  */
  def inExpressionCompletion = {
    val afterDotFilter = new LeftNeighbour(new TextFilter("."))
    var variant = new CompletionVariant(new NotFilter(afterDotFilter));
    variant.includeScopeClass(classOf[LeafPsiElement].asInstanceOf[java.lang.Class[LeafPsiElement]], true);
    //    variant.addCompletionFilterOnElement(TrueFilter.INSTANCE)
    var keywords = Array("true", "false", "null", "case", "catch", "def", "else", "finally", "for", "if", "match",
    "new", "override", "return", "super", "this", "throw", "try", "type", "while", "with", "yield")
    variant.addCompletion(keywords)
    registerVariant(variant)
  }



  override def findPrefix(insertedElement: PsiElement, offset: Int): String = {
    WordCompletionData.findPrefixSimple(insertedElement, offset)
  }

  /**
  * Adds all completion variants in sequence
  */
  def addCompletions(variant: CompletionVariant, comps: String*) = {
    for (val completion <- comps) variant.addCompletion(completion, TailType.SPACE)
  }

}
