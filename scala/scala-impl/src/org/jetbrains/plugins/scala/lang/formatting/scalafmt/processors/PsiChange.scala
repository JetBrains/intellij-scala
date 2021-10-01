package org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.{PsiElement, PsiRecursiveElementVisitor}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster

private sealed trait PsiChange {
  private var myIsValid = true
  def isValid: Boolean = myIsValid
  final def applyAndGetDelta(nextChange: PsiChange): Int = {
    myIsValid = false
    doApply(nextChange)
  }
  def doApply(nextChange: PsiChange): Int
  def isInRange(range: TextRange): Boolean
  def getStartOffset: Int
}

private object PsiChange {

  val generatedVisitor: PsiRecursiveElementVisitor = new PsiRecursiveElementVisitor() {
    override def visitElement(element: PsiElement): Unit = {
      CodeEditUtil.setNodeGenerated(element.getNode, false)
      super.visitElement(element)
    }
  }

  def setNotGenerated(element: PsiElement): Unit = {
    element.accept(generatedVisitor)
    //sometimes when an element is inserted, its neighbours also get the 'isGenerated' flag set, fix this
    element.prevSibling.foreach(_.accept(generatedVisitor))
    element.nextSibling.foreach(_.accept(generatedVisitor))
  }

  // NOTE: sometimes replaced element returned from `original.replace(formatted)` is invalid and is inside DummyHolder
  def findReplacedElement(parent: PsiElement, formatted: PsiElement, offset: Int): Option[PsiElement] =
    parent.children.find(child => child.getTextRange.getStartOffset == offset && child.textMatches(formatted.getText))

  final class Insert(before: PsiElement, formatted: PsiElement) extends PsiChange {
    override def doApply(nextChange: PsiChange): Int = {
      if (!formatted.isValid) {
        return 0
      }

      val originalMarkedForAdjustment = TypeAdjuster.isMarkedForAdjustment(before)
      val parent = before.getParent
      val offset = before.getTextRange.getStartOffset
      formatted.accept(generatedVisitor)

      val inserted =
        if (parent != null) {
          Option(inWriteAction(parent.addBefore(formatted, before)))
        } else None

      findReplacedElement(parent, formatted, offset)
        .foreach(setNotGenerated)

      if (originalMarkedForAdjustment)
        inserted.foreach(TypeAdjuster.markToAdjust)

      ScalaFmtPreFormatProcessor.addDelta(formatted, formatted.getTextLength)
    }
    override def isInRange(range: TextRange): Boolean = range.contains(before.getTextRange.getStartOffset)
    override def getStartOffset: Int = before.getTextRange.getStartOffset
  }

  final class Replace(private[PsiChange] var original: PsiElement, formatted: PsiElement) extends PsiChange {
    //noinspection HardCodedStringLiteral
    override def toString: String = s"${original.getTextRange}: ${original.getText} -> ${formatted.getText}"
    override def doApply(nextChange: PsiChange): Int = {
      if (!formatted.isValid || !original.isValid) {
        return 0
      }
      val commonPrefixLength = StringUtil.commonPrefix(original.getText, formatted.getText).length
      val delta = ScalaFmtPreFormatProcessor.addDelta(
        original.getTextRange.getStartOffset + commonPrefixLength,
        original.getContainingFile,
        formatted.getTextLength - original.getTextLength
      )
      val parent = original.getParent
      val offset = original.getTextOffset
      formatted.accept(generatedVisitor)

      import Replace._
      val nextChangeIsSibling: Boolean = isNextChangeSibling(nextChange, original)
      inWriteAction(original.replace(formatted))

      findReplacedElement(parent, formatted, offset).foreach { replaced =>
        setNotGenerated(replaced)
        if (nextChangeIsSibling) {
          fixNextChange(nextChange, replaced)
        }
      }

      delta
    }
    override def isInRange(range: TextRange): Boolean = original.getTextRange.intersectsStrict(range)
    override def getStartOffset: Int = original.getTextRange.getStartOffset
  }

  object Replace {

    private def isNextChangeSibling(nextChange: PsiChange, original: PsiElement): Boolean = {
      val nextSibling = original.getNextSibling
      nextChange match {
        case r: Remove  => r.remove == nextSibling
        case r: Replace => r.original == nextSibling
        case _          => false
      }
    }

    // After replaces sibling elements can become invalid  (their context become DummyHolder)
    // If subsequent change refer to this sibling element the change becomes invalid too.
    // So we have to fix the referred element
    // NOTE: Insert is not handled due to I didn't find a case when it will be applicable yet
    private def fixNextChange(nextChange: PsiChange, replaced: PsiElement): Unit = {
      val nextSibling = replaced.getNextSibling
      nextChange match {
        case r: Remove  => r.remove = nextSibling
        case r: Replace => r.original = nextSibling
        case _          =>
      }
    }
  }

  final class Remove(private[PsiChange] var remove: PsiElement) extends PsiChange {
    override def doApply(nextChange: PsiChange): Int = {
      if (!remove.isValid) {
        return 0
      }
      val res = ScalaFmtPreFormatProcessor.addDelta(remove, -remove.getTextLength)
      inWriteAction(remove.delete())
      res
    }
    override def isInRange(range: TextRange): Boolean = remove.getTextRange.intersectsStrict(range)
    override def getStartOffset: Int = remove.getTextRange.getStartOffset
  }
}