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
  type Parent <: PsiElement

  def parent: Parent =
    getParent.asInstanceOf[Parent]

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

sealed trait HInnerElement extends HoconPsiElement {
  type Parent <: HoconPsiElement
}

final class HObjectEntries(ast: ASTNode) extends HoconPsiElement(ast) {
  def forParent[T](forFile: HoconPsiFile => T, forObject: HObject => T) = parent match {
    case file: HoconPsiFile => forFile(file)
    case obj: HObject => forObject(obj)
  }

  def entries = findChildren[HObjectEntry]

  def objectFields = findChildren[HObjectField]

  def includes = findChildren[HInclude]
}

sealed trait HObjectEntry extends HoconPsiElement with HInnerElement {
  type Parent = HObjectEntries
}

final class HObjectField(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry {
  def docComments = nonWhitespaceChildren
    .takeWhile(_.getNode.getElementType == HoconTokenType.HashComment)
    .map(ch => ch.asInstanceOf[PsiComment])

  def keyedField = getChild[HKeyedField]

  def endingValue = keyedField.endingField.endingValue
}

sealed trait HKeyedField extends HoconPsiElement with HInnerElement {
  def forParent[T](forKeyedParent: HKeyedField => T, forObjectField: HObjectField => T): T = parent match {
    case kf: HKeyedField => forKeyedParent(kf)
    case of: HObjectField => forObjectField(of)
  }

  def key = findChild[HKey]

  def startingField: HKeyedField =
    forParent(_.startingField, _ => this)

  def endingField: HValuedField

  def endingValue = endingField.value
}

final class HPrefixedField(ast: ASTNode) extends HoconPsiElement(ast) with HKeyedField {
  def subField = getChild[HKeyedField]

  def endingField = subField.endingField
}

final class HValuedField(ast: ASTNode) extends HoconPsiElement(ast) with HKeyedField {
  def value = findChild[HValue]

  def separator = Option(findChildByType[PsiElement](HoconTokenSets.KeyValueSeparator))
    .map(_.getNode.getElementType.asInstanceOf[HoconTokenType])

  def endingField = this
}

final class HInclude(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry {
  def included = getChild[HIncluded]
}

final class HIncluded(ast: ASTNode) extends HoconPsiElement(ast) with HInnerElement {
  type Parent = HInclude

  def qualifier = getFirstChild.getNode.getElementType match {
    case HoconTokenType.UnquotedChars =>
      Some(getFirstChild.getText)
    case _ => None
  }

  def target = findChild[HIncludeTarget]

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
}

final class HKey(ast: ASTNode) extends HoconPsiElement(ast) with HInnerElement {
  def forParent[T](forPath: HPath => T, forKeyedField: HKeyedField => T): T = parent match {
    case path: HPath => forPath(path)
    case keyedField: HKeyedField => forKeyedField(keyedField)
  }

  def parts = findChildren[HKeyPart]
}

final class HPath(ast: ASTNode) extends HoconPsiElement(ast) with HInnerElement {
  def forParent[T](forPath: HPath => T, forSubstitution: HSubstitution => T): T = parent match {
    case path: HPath => forPath(path)
    case subst: HSubstitution => forSubstitution(subst)
  }

  def prefix = findChild[HPath]

  def key = findChild[HKey]
}

sealed trait HValue extends HoconPsiElement with HInnerElement {
  def forParent[T](forValuedField: HValuedField => T, forArray: HArray => T, forConcatenation: HConcatenation => T): T =
    parent match {
      case vf: HValuedField => forValuedField(vf)
      case arr: HArray => forArray(arr)
      case conc: HConcatenation => forConcatenation(conc)
    }
}

final class HObject(ast: ASTNode) extends HoconPsiElement(ast) with HValue {
  def entries = getChild[HObjectEntries]
}

final class HArray(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HSubstitution(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HConcatenation(ast: ASTNode) extends HoconPsiElement(ast) with HValue

sealed trait HLiteralValue extends HValue with PsiLiteral

final class HNull(ast: ASTNode) extends HoconPsiElement(ast) with HLiteralValue {
  def getValue: Object = null
}

final class HBoolean(ast: ASTNode) extends HoconPsiElement(ast) with HLiteralValue {
  def getValue: Object = jl.Boolean.valueOf(booleanValue)

  def booleanValue = getText.toBoolean
}

final class HNumber(ast: ASTNode) extends HoconPsiElement(ast) with HLiteralValue {
  def getValue: Object = numberValue

  def numberValue: jl.Number =
    if (getText.exists(HNumber.DecimalIndicators.contains))
      jl.Double.parseDouble(getText)
    else
      jl.Long.parseLong(getText)
}

object HNumber {
  private final val DecimalIndicators = Set('.', 'e', 'E')
}

final class HUnquotedString(ast: ASTNode) extends HoconPsiElement(ast)

sealed trait HString extends HInnerElement with PsiLiteral with ContributedReferenceHost {
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

  override def getReferences =
    PsiReferenceService.getService.getContributedReferences(this)
}

final class HStringValue(ast: ASTNode) extends HoconPsiElement(ast) with HString with HLiteralValue

final class HKeyPart(ast: ASTNode) extends HoconPsiElement(ast) with HString {
  type Parent = HKey
}

final class HIncludeTarget(ast: ASTNode) extends HoconPsiElement(ast) with HString {
  type Parent = HIncluded

  def getFileReferences =
    parent.fileReferenceSet.map(_.getAllReferences).getOrElse(FileReference.EMPTY)
}
