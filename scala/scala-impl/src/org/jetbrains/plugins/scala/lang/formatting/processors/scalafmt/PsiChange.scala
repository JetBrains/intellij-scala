package org.jetbrains.plugins.scala.lang.formatting.processors.scalafmt

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import com.intellij.psi.{PsiElement, PsiRecursiveElementVisitor}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.formatting.processors.scalafmt.PsiChange._
import org.jetbrains.plugins.scala.lang.formatting.processors.scalafmt.ScalaFmtPreFormatProcessor.addDelta
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.extensions.PsiElementExt

private sealed trait PsiChange {
  private var myIsValid = true
  def isValid: Boolean = myIsValid
  final def applyAndGetDelta(): Int = {
    myIsValid = false
    doApply()
  }
  def doApply(): Int
  def isInRange(range: TextRange): Boolean
  def getStartOffset: Int
}

private object PsiChange {

  /** Marker whitespace implementation that indicates absence of whitespace.
   * It is more convenient to use Replace psi change instead of Remove, but there is no way
   * to create a whitespace with empty text normally, via ScalaPsiElementFactory, so we use this hack
   */
  object EmptyPsiWhitespace extends PsiWhiteSpaceImpl("") {
    override def isValid: Boolean = true
  }

  val generatedVisitor: PsiRecursiveElementVisitor = new PsiRecursiveElementVisitor() {
    override def visitElement(element: PsiElement): Unit = {
      CodeEditUtil.setNodeGenerated(element.getNode, false)
      super.visitElement(element)
    }
  }

  def setNotGenerated(text: String, offset: Int, parent: PsiElement): Unit = {
    val children = parent.children.find(child => child.getTextRange.getStartOffset == offset && child.getText == text)
    children.foreach { child =>
      child.accept(generatedVisitor)
      //sometimes when an element is inserted, its neighbours also get the 'isGenerated' flag set, fix this
      Option(child.getPrevSibling).foreach(_.accept(generatedVisitor))
      Option(child.getNextSibling).foreach(_.accept(generatedVisitor))
    }
  }
}

private case class Insert(before: PsiElement, formatted: PsiElement) extends PsiChange {
  override def doApply(): Int = {
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

    setNotGenerated(formatted.getText, offset, parent)

    if (originalMarkedForAdjustment)
      inserted.foreach(TypeAdjuster.markToAdjust)

    addDelta(formatted, formatted.getTextLength)
  }
  override def isInRange(range: TextRange): Boolean = range.contains(before.getTextRange.getStartOffset)
  override def getStartOffset: Int = before.getTextRange.getStartOffset
}

private case class Replace(original: PsiElement, formatted: PsiElement) extends PsiChange {
  override def toString: String = s"${original.getTextRange}: ${original.getText} -> ${formatted.getText}"
  override def doApply(): Int = {
    if (!formatted.isValid || !original.isValid) {
      return 0
    }
    val commonPrefixLength = StringUtil.commonPrefix(original.getText, formatted.getText).length
    val delta = addDelta(
      original.getTextRange.getStartOffset + commonPrefixLength,
      original.getContainingFile,
      formatted.getTextLength - original.getTextLength
    )
    val parent = original.getParent
    val offset = original.getTextOffset
    formatted.accept(generatedVisitor)

    if (formatted == EmptyPsiWhitespace) {
      inWriteAction(original.delete())
    } else {
      inWriteAction(original.replace(formatted))
    }

    setNotGenerated(formatted.getText, offset, parent)
    delta
  }
  override def isInRange(range: TextRange): Boolean = original.getTextRange.intersectsStrict(range)
  override def getStartOffset: Int = original.getTextRange.getStartOffset
}

private case class Remove(remove: PsiElement) extends PsiChange {
  override def doApply(): Int = {
    val res = addDelta(remove, -remove.getTextLength)
    inWriteAction(remove.delete())
    res
  }
  override def isInRange(range: TextRange): Boolean = remove.getTextRange.intersectsStrict(range)
  override def getStartOffset: Int = remove.getTextRange.getStartOffset
}