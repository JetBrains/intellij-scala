package org.jetbrains.plugins.hocon.psi

import java.{lang => jl}

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import org.jetbrains.plugins.hocon.CommonUtil._
import org.jetbrains.plugins.hocon.HoconConstants
import org.jetbrains.plugins.hocon.HoconConstants._
import org.jetbrains.plugins.hocon.lexer.{HoconTokenSets, HoconTokenType}
import org.jetbrains.plugins.hocon.parser.HoconElementType
import org.jetbrains.plugins.hocon.ref.IncludedFileReferenceSet

import scala.reflect.{ClassTag, classTag}

sealed abstract class HoconPsiElement(ast: ASTNode) extends ASTWrapperPsiElement(ast) {
  override def accept(visitor: PsiElementVisitor) = visitor match {
    case hoconVisitor: HoconElementVisitor => accept(hoconVisitor)
    case _ => super.accept(visitor)
  }

  def accept(visitor: HoconElementVisitor) = visitor.visitHoconElement(this)

  def elementType =
    getNode.getElementType

  def getChild[T <: HoconPsiElement : ClassTag]: T =
    findChildByClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])

  def findChild[T <: HoconPsiElement : ClassTag] =
    Option(getChild[T])

  def allChildren =
    Iterator.iterate(getFirstChild)(_.getNextSibling).takeWhile(_ != null)

  def prevSiblings =
    Iterator.iterate(getPrevSibling)(_.getPrevSibling).takeWhile(_ != null)

  def nextSiblings =
    Iterator.iterate(getNextSibling)(_.getNextSibling).takeWhile(_ != null)

  def nonWhitespaceChildren =
    allChildren.filterNot(ch => HoconTokenSets.Whitespace.contains(ch.getNode.getElementType))

  def findChildren[T <: HoconPsiElement : ClassTag] =
    allChildren.collect {
      case t: T => t
    }
}

sealed trait HValue extends HoconPsiElement

sealed trait HLiteral extends HValue with PsiLiteral

sealed trait HObjectEntry extends HoconPsiElement

final class HObjectEntries(ast: ASTNode) extends HoconPsiElement(ast) {
  def entries = findChildren[HObjectEntry]

  def fields = findChildren[HObjectField]

  def includes = findChildren[HInclude]

  override def accept(visitor: HoconElementVisitor) = visitor.visitHObjectEntries(this)
}

final class HObjectField(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry {
  def bareField = getChild[HBareObjectField]

  def docComments = nonWhitespaceChildren
          .takeWhile(_.getNode.getElementType == HoconTokenType.HashComment)
          .map(ch => ch.asInstanceOf[PsiComment])

  def path = bareField.path

  def value = bareField.value

  def separator = bareField.separator

  override def accept(visitor: HoconElementVisitor) = visitor.visitHObjectField(this)
}

final class HBareObjectField(ast: ASTNode) extends HoconPsiElement(ast) {
  def path = getChild[HPath]

  def value = findChild[HValue]

  def separator = Option(findChildByType[PsiElement](HoconTokenSets.PathValueSeparator))
          .map(_.getNode.getElementType.asInstanceOf[HoconTokenType])

  override def accept(visitor: HoconElementVisitor) = visitor.visitHBareObjectField(this)
}

final class HInclude(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry {
  def included = getChild[HIncluded]

  override def accept(visitor: HoconElementVisitor) = visitor.visitHInclude(this)
}

final class HIncluded(ast: ASTNode) extends HoconPsiElement(ast) {
  def qualifier = getFirstChild.getNode.getElementType match {
    case HoconTokenType.UnquotedChars =>
      Some(getFirstChild.getText)
    case _ => None
  }

  def target =
    findChild[HString].filter(_.stringType == HoconTokenType.QuotedString)

  def fileReferenceSet = target.flatMap { hs =>
    val strVal = hs.stringValue
    def refSet(absolute: Boolean) =
      new IncludedFileReferenceSet(strVal, hs, absolute)

    // com.typesafe.config.impl.SimpleIncluder#includeWithoutFallback
    qualifier match {
      case Some(ClasspathQualifier) =>
        Some(refSet(absolute = true))
      case None if !isValidUrl(strVal) =>
        Some(refSet(absolute = false))
      case _ =>
        None
    }
  }

  override def accept(visitor: HoconElementVisitor) =
    visitor.visitHIncluded(this)
}

final class HKey(ast: ASTNode) extends HoconPsiElement(ast) {
  override def accept(visitor: HoconElementVisitor) = visitor.visitHKey(this)
}

final class HPath(ast: ASTNode) extends HoconPsiElement(ast) {
  def prefix = findChild[HPath]

  def key = findChild[HKey]

  override def accept(visitor: HoconElementVisitor) = visitor.visitHPath(this)
}

final class HObject(ast: ASTNode) extends HoconPsiElement(ast) with HValue {
  def entries = getChild[HObjectEntries]

  override def accept(visitor: HoconElementVisitor) = visitor.visitHObject(this)
}

final class HArray(ast: ASTNode) extends HoconPsiElement(ast) with HValue {
  override def accept(visitor: HoconElementVisitor) = visitor.visitHArray(this)
}

final class HSubstitution(ast: ASTNode) extends HoconPsiElement(ast) with HValue {
  override def accept(visitor: HoconElementVisitor) = visitor.visitHSubstitution(this)
}

final class HConcatenation(ast: ASTNode) extends HoconPsiElement(ast) with HValue {
  override def accept(visitor: HoconElementVisitor) = visitor.visitHConcatenation(this)
}

final class HNull(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral {
  def getValue: Object = null

  override def accept(visitor: HoconElementVisitor) = visitor.visitHNull(this)
}

final class HBoolean(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral {
  def getValue: Object = jl.Boolean.valueOf(booleanValue)

  def booleanValue = getText.toBoolean

  override def accept(visitor: HoconElementVisitor) = visitor.visitHBoolean(this)
}

final class HNumber(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral {
  def getValue: Object = numberValue

  def numberValue: jl.Number =
    if (getText.exists(HNumber.DecimalIndicators.contains))
      jl.Double.parseDouble(getText)
    else
      jl.Long.parseLong(getText)

  override def accept(visitor: HoconElementVisitor) =
    visitor.visitHNumber(this)
}

object HNumber {
  private final val DecimalIndicators = Set('.', 'e', 'E')
}

final class HUnquotedString(ast: ASTNode) extends HoconPsiElement(ast) {
  override def accept(visitor: HoconElementVisitor) = visitor.visitHUnquotedString(this)
}

final class HString(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral with ContributedReferenceHost {
  def stringType = getFirstChild.getNode.getElementType

  def getValue: Object = stringValue

  def unquote = stringType match {
    case HoconTokenType.QuotedString =>
      getText.substring(1, getText.length - (if (isClosed) 1 else 0))
    case HoconTokenType.MultilineString =>
      getText.substring(3, getText.length - (if (isClosed) 3 else 0))
    case HoconElementType.UnquotedString =>
      getText
  }

  def stringValue = stringType match {
    case HoconTokenType.QuotedString =>
      StringUtil.unescapeStringCharacters(unquote)
    case _ =>
      unquote
  }

  def isClosed = stringType match {
    case HoconTokenType.QuotedString =>
      HoconConstants.ProperlyClosedQuotedString.pattern.matcher(getText).matches
    case HoconTokenType.MultilineString =>
      getText.endsWith("\"\"\"")
    case _ =>
      true
  }

  def isIncludeTarget =
    stringType == HoconTokenType.QuotedString &&
            getParent.getNode.getElementType == HoconElementType.Included

  def getFileReferences =
    if (isIncludeTarget)
      getParent.asInstanceOf[HIncluded].fileReferenceSet
              .map(_.getAllReferences).getOrElse(FileReference.EMPTY)
    else
      FileReference.EMPTY

  override def getReferences =
    PsiReferenceService.getService.getContributedReferences(this)

  override def accept(visitor: HoconElementVisitor) =
    visitor.visitHString(this)
}
