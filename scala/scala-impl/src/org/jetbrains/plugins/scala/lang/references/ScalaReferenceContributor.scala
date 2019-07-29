package org.jetbrains.plugins.scala.lang.references

import java.util
import java.util.Collections

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.{Condition, TextRange}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PsiJavaElementPattern
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.reference.ArbitraryPlaceUrlReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.{FilePathReferenceProvider, FileReference, FileReferenceSet}
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInterpolationPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedStringPartReference

import scala.collection.mutable.ListBuffer

class ScalaReferenceContributor extends PsiReferenceContributor {

  override def registerReferenceProviders(registrar: PsiReferenceRegistrar) {

    def literalCapture: PsiJavaElementPattern.Capture[ScLiteral] = psiElement(classOf[ScLiteral])

    registrar.registerReferenceProvider(literalCapture, new ScalaFilePathReferenceProvider(false))
    registrar.registerReferenceProvider(literalCapture, new InterpolatedStringReferenceProvider())
    registrar.registerReferenceProvider(literalCapture, new ArbitraryPlaceUrlReferenceProvider())
  }
}

private class InterpolatedStringReferenceProvider extends PsiReferenceProvider {

  override def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    element match {
      case _: ScInterpolatedStringLiteral => Array.empty
      case l: ScLiteral if (l.isString || l.isMultiLineString) && l.getText.contains("$") =>
        val interpolated = ScalaPsiElementFactory.createExpressionFromText("s" + l.getText, l.getContext)
        interpolated.getChildren.filter {
          case _: ScInterpolatedStringPartReference => false
          case _: ScReferenceExpression => true
          case _ => false
        }.map {
          case ref: ScReferenceExpression =>
            new PsiReference {
              override def getVariants: Array[AnyRef] = Array.empty

              override def getCanonicalText: String = ref.getCanonicalText

              override def getElement: PsiElement = l

              override def isReferenceTo(element: PsiElement): Boolean = ref.isReferenceTo(element)

              override def bindToElement(element: PsiElement): PsiElement = ref

              override def handleElementRename(newElementName: String): PsiElement = ref

              override def isSoft: Boolean = true

              override def getRangeInElement: TextRange = {
                val range = ref.getTextRange
                val startOffset = interpolated.getTextRange.getStartOffset + 1
                new TextRange(range.getStartOffset - startOffset, range.getEndOffset - startOffset)
              }

              override def resolve(): PsiElement = null
            }
        }
      case _ => Array.empty
    }
  }
}


private class ScalaFilePathReferenceProvider(private val myEndingSlashNotAllowed: Boolean) extends FilePathReferenceProvider {

  override def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    element match {
      case interpolated: ScInterpolationPattern =>
        val parts = getStringParts(interpolated)
        val start: Int = interpolated.getTextRange.getStartOffset
        parts.flatMap { element =>
          val offset = element.getTextRange.getStartOffset - start
          getReferencesByElement(interpolated, element.getText, offset, true)
        }
      case interpolatedString: ScInterpolatedStringLiteral =>
        val parts = getStringParts(interpolatedString)
        val start: Int = interpolatedString.getTextRange.getStartOffset
        parts.flatMap { element =>
          val offset = element.getTextRange.getStartOffset - start
          getReferencesByElement(interpolatedString, element.getText, offset, true)
        }
      case literal: ScLiteral =>
        literal.getValue match {
          case text: String => getReferencesByElement(element, text, 1, true)
          case _ => PsiReference.EMPTY_ARRAY
        }
      case _ => PsiReference.EMPTY_ARRAY
    }
  }

  private def getStringParts(interpolated: ScInterpolated): Array[PsiElement] = {
    val accepted = List(ScalaTokenTypes.tINTERPOLATED_STRING, ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING)
    val res = ListBuffer[PsiElement]()
    val children: Array[PsiElement] = interpolated match {
      case ip: ScInterpolationPattern => ip.args.children.toArray
      case sl: ScInterpolatedStringLiteral => Option(sl.getFirstChild.getNextSibling).toArray
    }
    for (child <- children) {
      if (accepted.contains(child.getNode.getElementType))
        res += child
    }
    res.toArray
  }
}

