package org.jetbrains.plugins.scala.lang.completion

import handlers.ScalaInsertHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.{PsiElement, PsiReference, PsiFile}
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
import java.awt.Color
import com.intellij.codeInsight.lookup._

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
    registerDefTypeCompletion
    registerForSomeCompletion
    registerMatchCompletion
    registerImplicitCompletion
    registerTypeCompletion
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

  private def registerImplicitCompletion {
    registerStandardCompletion(new ImplicitFilter, "implicit")
  }

  private def registerCaseCompletion {
    registerStandardCompletion(new CaseFilter, "case")
  }

  private def registerImportCompletion {
    registerStandardCompletion(new ImportFilter, "import")
  }

  private def registerTemplateDefinitionCompletion {
    registerStandardCompletion(new TemplateFilter, "class", "object")
    registerStandardCompletion(new TraitFilter, "trait")
  }

  private def registerDefinitionsCompletion {
    registerStandardCompletion(new DefinitionsFilter, "val", "var")
  }

  private def registerValueDefinitionCompletion {
    registerStandardCompletion(new ValueDefinitionFilter, "val")
  }

  private def registerStatementCompletion {
    registerStandardCompletion(new StatementFilter, "for", "while", "do", "try", "return", "throw"/*, "if"*/)
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

  /*private def registerIfCompletion {
    registerStandardCompletion(new IfFilter, "if")
  }*/

  private def registerDefTypeCompletion {
    registerStandardCompletion(new DefTypeFilter, "def", "type")
  }

  private def registerForSomeCompletion {
    registerStandardCompletion(new ForSomeFilter, "forSome")
  }

  private def registerMatchCompletion {
    registerStandardCompletion(new MatchFilter, "match")
  }

  private def registerTypeCompletion {
    val afterDotFilter = new LeftNeighbour(new TextFilter("."))
    val variant = new CompletionVariant(new AndFilter(afterDotFilter, new TypeFilter))
    variant.setItemProperty(LookupItem.HIGHLIGHTED_ATTR, "")
    variant.includeScopeClass(classOf[LeafPsiElement])
    addCompletions(variant, "type")
    registerVariant(variant)
  }

  private def registerStandardCompletion(filter: ElementFilter, keywords: String*) {
    val afterDotFilter = new LeftNeighbour(new TextFilter("."))
    val variant = new CompletionVariant(new AndFilter(new NotFilter(afterDotFilter), filter))
    variant.setItemProperty(LookupItem.HIGHLIGHTED_ATTR, "")
    variant.includeScopeClass(classOf[LeafPsiElement])
    addCompletions(variant, keywords: _*)
    registerVariant(variant)
  }



  override def findPrefix(insertedElement: PsiElement, offset: Int): String = {
    CompletionData.findPrefixStatic(insertedElement, offset)
  }

  /**
  * Adds all completion variants in sequence
  */
  def addCompletions(variant: CompletionVariant, comps: String*) = {
    for (val completion <- comps) variant.addCompletion(completion, TailType.SPACE)
  }

  override def completeReference(reference: PsiReference,  set: java.util.Set[LookupElement], position: PsiElement,  file: PsiFile,
                                offset: Int) {
    val variants = findVariants(position, file)
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      def run() {
        var hasApplicableVariants = false
        for (variant <- variants) {
          if (variant.hasReferenceFilter()) {
            variant.setInsertHandler(new ScalaInsertHandler)
            variant.addReferenceCompletions(reference, position, set, file, ScalaCompletionData.this)
            hasApplicableVariants = true
          }
        }

        if (!hasApplicableVariants) {
          myGenericVariant.setInsertHandler(new ScalaInsertHandler)
          myGenericVariant.addReferenceCompletions(reference, position, set, file, ScalaCompletionData.this)
        }
      }
    })
  }

}
