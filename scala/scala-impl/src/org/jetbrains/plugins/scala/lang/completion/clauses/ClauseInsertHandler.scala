package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

private[clauses] abstract class ClauseInsertHandler[E <: ScalaPsiElement](clazz: Class[E])
  extends InsertHandler[LookupElement] {

  protected def handleInsert(implicit context: InsertionContext): Unit

  override final def handleInsert(context: InsertionContext,
                                  lookupElement: LookupElement): Unit =
    handleInsert(context)

  protected final def onTargetElement[U >: Null](onElement: E => U)
                                                (implicit context: InsertionContext): U = {
    val elementAtOffset = context.getFile.findElementAt(context.getStartOffset)
    PsiTreeUtil.getContextOfType(elementAtOffset, false, clazz) match {
      case null => null
      case targetElement => onElement(targetElement)
    }
  }

  protected final def replaceText(text: String)
                                 (implicit context: InsertionContext): Unit = {
    context.getDocument.replaceString(
      context.getStartOffset,
      context.getSelectionEndOffset,
      text
    )
    context.commitDocument()
  }

  protected final def moveCaret(offset: Int)
                               (implicit context: InsertionContext): Unit = {
    val InsertionContextExt(editor, document, _, project) = context
    PsiDocumentManager.getInstance(project)
      .doPostponedOperationsAndUnblockDocument(document)
    editor.getCaretModel.moveToOffset(offset)
  }
}

private[clauses] object ClauseInsertHandler {

  def adjustTypes(addImports: Boolean,
                  pairs: (ScPattern, PatternComponents)*)
                 (collector: PartialFunction[ScPattern, ScTypeElement]): Unit = {
    val findTypeElement = collector.lift
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

  private[this] def replacementText(typeElement: ScTypeElement,
                                    components: PatternComponents): String = {
    def referenceText = (typeElement match {
      case SimpleTypeReferenceReference(reference) => reference
      case ScParameterizedTypeElement(SimpleTypeReferenceReference(reference), _) => reference
    }).getText

    typeElement match {
      case simpleTypeElement: ScSimpleTypeElement if simpleTypeElement.singleton => referenceText
      case _ =>
        components match {
          case extractorComponents: ExtractorPatternComponents[_] =>
            extractorComponents.extractorText(referenceText)
          case _ =>
            val name = typeElement.`type`().toOption
              .flatMap(NameSuggester.suggestNamesByType(_).headOption)
              .getOrElse(extensions.Placeholder)
            s"$name: ${typeElement.getText}"
        }
    }
  }

  private[this] object SimpleTypeReferenceReference {

    def unapply(typeElement: ScSimpleTypeElement): Option[ScStableCodeReference] =
      typeElement.reference
  }

}
