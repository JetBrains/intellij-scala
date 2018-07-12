package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion._
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{JavaPsiFacade, PsiElement, PsiPackage}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.{PsiNamedElementExt, ResolvesTo, inReadAction}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.JavaConverters

/**
  * @author Nikolay.Tropin
  */

class ScalaPrefixPackageCompletionContributor extends ScalaCompletionContributor {

  extend(
    CompletionType.BASIC,
    psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceElement]),
    new CompletionProvider[CompletionParameters]() {
      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        implicit val p: CompletionParameters = parameters
        implicit val c: ProcessingContext = context

        val element = positionFromParameters
        if (!shouldRunClassNameCompletion(element, result.getPrefixMatcher)) {
          ScalaPrefixPackageCompletionContributor.completePrefixPackageNames(element, result)
        }
      }
    }
  )
}

object ScalaPrefixPackageCompletionContributor {

  private def completePrefixPackageNames(dummyPosition: PsiElement, result: CompletionResultSet)
                                        (implicit parameters: CompletionParameters, context: ProcessingContext): Unit = {
    val project = dummyPosition.getProject

    def addPackageForCompletion(packageFqn: String): Unit = {
      val isExcluded: Boolean = CodeInsightSettings.getInstance.EXCLUDED_PACKAGES.contains(packageFqn.startsWith(_: String))
      if (isExcluded) return

      if (parameters.getInvocationCount == 0) return

      if (PsiTreeUtil.getContextOfType(dummyPosition, classOf[ScImportStmt]) != null) return

      if (result.getPrefixMatcher.getPrefix == "") return

      val pckg = inReadAction(JavaPsiFacade.getInstance(project).findPackage(packageFqn))
      if (pckg == null) return

      ScalaPsiElementFactory.createExpressionWithContextFromText(pckg.name, dummyPosition.getContext, dummyPosition) match {
        case ResolvesTo(pack: PsiPackage) if pack.getQualifiedName == pckg.getQualifiedName => return
        case _ =>
      }

      val items = new ScalaResolveResult(pckg, prefixCompletion = true).getLookupElement(shouldImport = true)
      items.foreach { le =>
        le.elementToImport = Option(pckg)
      }

      import JavaConverters._
      result.addAllElements(items.asJava)
    }

    val prefixMatcher = result.getPrefixMatcher
    for {
      fqn <- prefixPackages(project)
      dotIdx = fqn.lastIndexOf('.')
      name = if (dotIdx < 0) fqn else fqn.substring(dotIdx)
      if prefixMatcher.prefixMatches(name)
    } {
      addPackageForCompletion(fqn)
    }

  }

  private[this] def prefixPackages(project: Project): Seq[String] = {
    def stripLastWord(pattern: String) = pattern.split('.').dropRight(1).mkString(".")

    val settings = ScalaCodeStyleSettings.getInstance(project)
    val patterns = settings.getImportsWithPrefix.filter(!_.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX))
    patterns.toSeq.map(stripLastWord).filter(!_.isEmpty).distinct
  }
}
