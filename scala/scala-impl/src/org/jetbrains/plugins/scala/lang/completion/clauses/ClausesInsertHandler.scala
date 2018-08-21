package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, TypeAdjuster}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

private[clauses] abstract class ClausesInsertHandler[E <: ScalaPsiElement](clazz: Class[E])
  extends InsertHandler[LookupElement] {

  import ClausesInsertHandler._

  override final def handleInsert(insertionContext: InsertionContext,
                                  lookupElement: LookupElement): Unit =
    handleInsert(insertionContext)

  protected def handleInsert(implicit insertionContext: InsertionContext): Unit

  protected final def onTargetElement[U >: Null](onElement: E => U)
                                                (implicit insertionContext: InsertionContext): U = {
    val elementAtOffset = insertionContext.getFile.findElementAt(insertionContext.getStartOffset)
    PsiTreeUtil.getContextOfType(elementAtOffset, false, clazz) match {
      case null => null
      case targetElement => onElement(targetElement)
    }
  }

  protected final def adjustTypesPhase(addImports: Boolean,
                                       pairs: (ScPattern, PatternComponents)*): Unit = {
    TypeAdjuster.adjustFor(
      pairs.flatMap {
        case (pattern, _) => findTypeElement(pattern)
      },
      addImports = addImports,
      useTypeAliases = false
    )

    for {
      (pattern, components) <- pairs
      typeElement <- findTypeElement(pattern)

      patternText = replacementText(typeElement, components)
      replacement = ScalaPsiElementFactory.createPatternFromTextWithContext(patternText, pattern.getContext, pattern)
    } pattern.replace(replacement)
  }

  protected def findTypeElement(pattern: ScPattern): Option[ScTypeElement] = pattern match {
    case ScTypedPattern(typeElement) => Some(typeElement)
    case _ => None
  }
}

private[clauses] object ClausesInsertHandler {

  def replaceTextPhase(replacement: String)
                      (implicit insertionContext: InsertionContext): Unit = {
    val startOffset = insertionContext.getStartOffset
    val InsertionContextExt(_, document, file, project) = insertionContext

    document.replaceString(
      startOffset,
      insertionContext.getSelectionEndOffset,
      replacement
    )
    CodeStyleManager.getInstance(project).reformatText(
      file,
      startOffset,
      startOffset + replacement.length
    )
    insertionContext.commitDocument()
  }

  private def replacementText(typeElement: ScTypeElement,
                              components: PatternComponents): String = {
    def referenceText = (typeElement match {
      case SimpleTypeReferenceReference(reference) => reference
      case ScParameterizedTypeElement(SimpleTypeReferenceReference(reference), _) => reference
    }).getText

    (typeElement, components) match {
      case (simpleTypeElement: ScSimpleTypeElement, _) if simpleTypeElement.singleton =>
        referenceText
      case (_, extractorComponents: SyntheticExtractorPatternComponents) =>
        extractorComponents.extractorText(referenceText) { parameter =>
          parameter.name + (if (parameter.isVarArgs) "@_*" else "")
        }
      case (_, extractorComponents: PhysicalExtractorPatternComponents) =>
        extractorComponents.defaultExtractorText(referenceText)
      case _ =>
        val name = typeElement.`type`().toOption
          .flatMap(NameSuggester.suggestNamesByType(_).headOption)
          .getOrElse(extensions.Placeholder)
        s"$name: ${typeElement.getText}"
    }
  }

  private[this] object SimpleTypeReferenceReference {

    def unapply(typeElement: ScSimpleTypeElement): Option[ScStableCodeReferenceElement] =
      typeElement.reference
  }

}
