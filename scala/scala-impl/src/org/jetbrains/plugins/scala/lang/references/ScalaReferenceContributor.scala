package org.jetbrains.plugins.scala
package lang
package references

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.patterns.{PlatformPatterns, PsiJavaElementPattern}
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FilePathReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.{ArbitraryPlaceUrlReferenceProvider, CommentsReferenceContributor}
import com.intellij.psi.tree.TokenSet
import com.intellij.util.{IncorrectOperationException, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInterpolationPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScPsiDocToken

import scala.collection.mutable.ListBuffer

/**
 * @see [[com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaReferenceContributor]]
 */
final class ScalaReferenceContributor extends PsiReferenceContributor {

  override def registerReferenceProviders(registrar: PsiReferenceRegistrar): Unit = {
    def literalCapture: PsiJavaElementPattern.Capture[ScStringLiteral] = psiElement(classOf[ScStringLiteral])

    registrar.registerReferenceProvider(literalCapture, new ScalaFilePathReferenceProvider(false), PsiReferenceRegistrar.LOWER_PRIORITY)
    registrar.registerReferenceProvider(literalCapture, new InterpolatedStringReferenceProvider())
    registrar.registerReferenceProvider(literalCapture, new ArbitraryPlaceUrlReferenceProvider())

    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(classOf[ScPsiDocToken]),
      CommentsReferenceContributor.COMMENTS_REFERENCE_PROVIDER_TYPE.getProvider
    )
  }
}

private final class InterpolatedStringReferenceProvider extends PsiReferenceProvider {

  import PsiReference.EMPTY_ARRAY

  override def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = element match {
    case _: ScInterpolatedStringLiteral => EMPTY_ARRAY
    case literal: ScLiteral if literal.isString && literal.textContains('$') => // TODO remove this hack
      try {
        val interpolated = createExpressionFromText("s" + literal.getText, literal.getContext)
          .asInstanceOf[ScInterpolatedStringLiteral]

        for {
          child <- interpolated.getInjections.toArray
          if child.isInstanceOf[ScReferenceExpression]
        } yield new InterpolatedStringPsiReference(
          child.asInstanceOf[ScReferenceExpression],
          literal,
          interpolated
        )
      } catch {
        case _: IncorrectOperationException => EMPTY_ARRAY
      }
    case _ => EMPTY_ARRAY
  }
}

private class InterpolatedStringPsiReference(ref: ScReferenceExpression, literal: ScLiteral, interpolated: ScExpression) extends PsiReference {
  override def getCanonicalText: String = ref.getCanonicalText

  override def getElement: PsiElement = literal

  override def isReferenceTo(element: PsiElement): Boolean = ref.isReferenceTo(element)

  override def bindToElement(element: PsiElement): PsiElement = ref

  override def handleElementRename(newElementName: String): PsiElement = ref

  override def isSoft: Boolean = true

  override def getRangeInElement: TextRange = {
    val startOffset = interpolated.getTextRange.getStartOffset + 1
    ref.getTextRange.shiftLeft(startOffset)
  }

  override def resolve(): PsiElement = null
}

private class ScalaFilePathReferenceProvider(private val myEndingSlashNotAllowed: Boolean) extends FilePathReferenceProvider {
  import ScalaFilePathReferenceProvider._

  override def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] =
    element match {
      case interp: ScInterpolated => getReferencesForInterpolated(interp)
      case ScStringLiteral(text)  => getReferencesByElement(element, text, 1, true)
      case _                      => PsiReference.EMPTY_ARRAY
    }

  private def getReferencesForInterpolated(interpolated: ScInterpolated): Array[PsiReference] = {
    val parts = getStringParts(interpolated)
    val start = interpolated.startOffset
    parts.flatMap { element =>
      val offset = element.startOffset - start
      getReferencesByElement(interpolated, element.getText, offset, true)
    }
  }

  // do not replace with interpolated.getStringParts
  // comment from revision f4f57ef:
  // these references have nothing to do with ScInterpolatedExpressionPrefix
  private def getStringParts(interpolated: ScInterpolated): Array[PsiElement] = {
    val res = ListBuffer[PsiElement]()
    val children: Array[PsiElement] = interpolated match {
      case ip: ScInterpolationPattern => ip.args.children.toArray
      case sl: ScInterpolatedStringLiteral => Option(sl.getFirstChild.getNextSibling).toArray
    }
    for (child <- children) {
      if (acceptedInterpolatedTokens.contains(child.getNode.getElementType))
        res += child
    }
    res.toArray
  }
}

private object ScalaFilePathReferenceProvider {

  private val acceptedInterpolatedTokens = TokenSet.create(
    ScalaTokenTypes.tINTERPOLATED_STRING,
    ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING
  )
}

