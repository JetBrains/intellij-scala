package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.{PsiElement, PsiPackage, JavaPsiFacade}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.{TypeAliasToImport, ClassTypeToImport}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.extensions.{ResolvesTo, PsiNamedElementExt, inReadAction}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.lookups.LookupElementManager
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */

class ScalaPrefixPackageCompletionContributor extends ScalaCompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).
          withParent(classOf[ScReferenceElement]), new CompletionProvider[CompletionParameters] {

    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      if (!shouldRunClassNameCompletion(positionFromParameters(parameters), parameters, result.getPrefixMatcher)) {
        ScalaPrefixPackageCompletionContributor.completePrefixPackageNames(positionFromParameters(parameters), parameters, context, result)
      }
    }
  })

}

object ScalaPrefixPackageCompletionContributor {

  def completePrefixPackageNames(dummyPosition: PsiElement, parameters: CompletionParameters,
                                 context: ProcessingContext, result: CompletionResultSet) = {
    val position = dummyPosition
    val project = position.getProject

    def addPackageForCompletion(packageFqn: String): Unit = {
      val isExcluded: Boolean = CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.contains(packageFqn.startsWith(_: String))
      if (isExcluded) return

      if (parameters.getInvocationCount == 0) return

      if (PsiTreeUtil.getContextOfType(position, classOf[ScImportStmt]) != null) return

      if (result.getPrefixMatcher.getPrefix == "") return

      val pckg = inReadAction(JavaPsiFacade.getInstance(project).findPackage(packageFqn))
      if (pckg == null) return

      ScalaPsiElementFactory.createExpressionWithContextFromText(pckg.name, position.getContext, position) match {
        case ResolvesTo(pack: PsiPackage) if pack.getQualifiedName == pckg.getQualifiedName => return
        case _ =>
      }

      val resolveResult = new ScalaResolveResult(pckg, prefixCompletion = true)
      val lookupElems = LookupElementManager.getLookupElement(resolveResult, isInImport = false, shouldImport = true)
      lookupElems.foreach { le =>
        le.elementToImport = pckg
      }
      result.addAllElements(lookupElems.asJava)
    }

    val prefixMatcher = result.getPrefixMatcher
    for {
      fqn <- prefixPackages(project)
      name = fqn.substring(fqn.lastIndexOf('.'))
      if prefixMatcher.prefixMatches(name)
    } {
      addPackageForCompletion(fqn)
    }

  }

  def prefixPackages(project: Project) = {
    def stripLastWord(pattern: String) = pattern.split('.').dropRight(1).mkString(".")
    
    val settings = ScalaCodeStyleSettings.getInstance(project)
    val patterns = settings.getImportsWithPrefix.filter(!_.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX))
    patterns.toSeq.map(stripLastWord).distinct
  }
}
