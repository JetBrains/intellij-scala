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

  override def getContainingFile: HoconPsiFile =
    super.getContainingFile.asInstanceOf[HoconPsiFile]

  def parent: Parent =
    getParent.asInstanceOf[Parent]

  def elementType =
    getNode.getElementType

  def getChild[T: ClassTag]: T =
    findChildByClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])

  def findChild[T: ClassTag] =
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

final class HObjectEntries(ast: ASTNode) extends HoconPsiElement(ast) with HScope {
  def forParent[T](forFile: HoconPsiFile => T, forObject: HObject => T) = parent match {
    case file: HoconPsiFile => forFile(file)
    case obj: HObject => forObject(obj)
  }

  def entries = findChildren[HObjectEntry]

  def objectFields = findChildren[HObjectField]

  def includes = findChildren[HInclude]

  def directKeyedFields = objectFields.flatMap(_.directKeyedFields)
}

sealed trait HObjectEntry extends HoconPsiElement with HInnerElement {
  type Parent = HObjectEntries
}

final class HObjectField(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry with HScope {
  def docComments = nonWhitespaceChildren
    .takeWhile(_.getNode.getElementType == HoconTokenType.HashComment)
    .map(ch => ch.asInstanceOf[PsiComment])

  def keyedField = getChild[HKeyedField]

  def endingValue = keyedField.endingField.endingValue

  def directKeyedFields = Iterator(keyedField)
}

sealed trait HKeyedField extends HoconPsiElement with HInnerElement with HScope {
  def forParent[T](forKeyedParent: HKeyedField => T, forObjectField: HObjectField => T): T = parent match {
    case kf: HKeyedField => forKeyedParent(kf)
    case of: HObjectField => forObjectField(of)
  }

  def key = findChild[HKey]
  def validKey = key.filter(_.isValidKey)

  /**
   * Goes up the tree in order to determine full path under which this keyed field is defined.
   * Stops when encounters file-toplevel entries or an array (including array-append field).
   *
   * @return stream of all encountered keyed fields (in bottom-up order, i.e. starting with itself)
   */
  def allFieldsUntilToplevel: Iterator[HKeyedField] =
    Iterator(this) ++ forParent(
      keyedField => keyedField.allFieldsUntilToplevel,
      objectField => {
        val entries = objectField.parent
        def forValue(value: HValue): Iterator[HKeyedField] = value.forParent(
          valuedField =>
            if (!valuedField.isArrayAppend) valuedField.allFieldsUntilToplevel
            else Iterator.empty,
          array => Iterator.empty,
          concat => forValue(concat)
        )
        entries.forParent(
          file => Iterator.empty,
          obj => forValue(obj)
        )
      }
    )

  /**
   * Like [[allFieldsUntilToplevel]] but returns [[HKey]]s instead of [[HKeyedField]]s, in reverse order (i.e. key
   * from this field is at the end) and ensures that all keys are valid. If not, [[None]] is returned.
   */
  def allKeysFromToplevel: Option[List[HKey]] = {
    // make sure we stop traversing when seeing invalid key
    // this is essentially a non-strict fold
    val it = allFieldsUntilToplevel
    def iterate(acc: List[HKey]): Option[List[HKey]] =
      if (it.hasNext) it.next().validKey.flatMap(key => iterate(key :: acc))
      else Some(acc)
    iterate(Nil)
  }

  def enclosingEntries: HObjectEntries =
    forParent(keyedParent => keyedParent.enclosingEntries, objectField => objectField.parent)

  def startingField: HKeyedField =
    forParent(_.startingField, _ => this)

  def endingField: HValuedField

  def endingValue = endingField.value

  /**
   * Scopes present in whatever is on the right side of key in that keyed field.
   */
  def subScopes: Iterator[HScope]

  def directKeyedFields = Iterator(this)
}

final class HPrefixedField(ast: ASTNode) extends HoconPsiElement(ast) with HKeyedField {
  def subField = getChild[HKeyedField]

  def endingField = subField.endingField

  def subScopes = Iterator(subField)
}

final class HValuedField(ast: ASTNode) extends HoconPsiElement(ast) with HKeyedField {
  def value = findChild[HValue]

  def isArrayAppend =
    separator.contains(HoconTokenType.PlusEquals)

  def separator = Option(findChildByType[PsiElement](HoconTokenSets.KeyValueSeparator))
    .map(_.getNode.getElementType.asInstanceOf[HoconTokenType])

  def endingField = this

  def subScopes =
    if (isArrayAppend) Iterator.empty
    else value.collect {
      case obj: HObject => Iterator(obj)
      case conc: HConcatenation => conc.findChildren[HObject]
    }.getOrElse(Iterator.empty)
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

  def allKeysFromToplevel: Option[List[HKey]] =
    forParent(
      path => path.allKeys,
      keyedEntry => keyedEntry.allKeysFromToplevel
    )

  def enclosingEntries: HObjectEntries =
    forParent(
      path => getContainingFile.toplevelEntries,
      keyedField => keyedField.enclosingEntries
    )

  def stringValue = allChildren.collect {
    case keyPart: HKeyPart => keyPart.stringValue
    case other => other.getText
  }.mkString

  def isValidKey = findChild[PsiErrorElement].isEmpty
}

final class HPath(ast: ASTNode) extends HoconPsiElement(ast) with HInnerElement {
  def forParent[T](forPath: HPath => T, forSubstitution: HSubstitution => T): T = parent match {
    case path: HPath => forPath(path)
    case subst: HSubstitution => forSubstitution(subst)
  }

  def allPaths: List[HPath] = {
    def allPathsIn(path: HPath, acc: List[HPath]): List[HPath] =
      path.prefix.map(prePath => allPathsIn(prePath, path :: acc)).getOrElse(path :: acc)
    allPathsIn(this, Nil)
  }

  /**
   * Some(all keys in this path) or None if there's an invalid key in path.
   */
  def allKeys: Option[List[HKey]] = {
    def allKeysIn(path: HPath, acc: List[HKey]): Option[List[HKey]] =
      path.validKey.flatMap(key => path.prefix
        .map(prePath => allKeysIn(prePath, key :: acc))
        .getOrElse(Some(key :: acc)))
    allKeysIn(this, Nil)
  }

  /**
   * If all keys are valid - all keys of this path.
   * If some keys are invalid - all valid keys from left to right until some invalid key is encountered
   * (i.e. longest valid prefix path)
   */
  def startingValidKeys: List[HKey] =
    allPaths.iterator.takeWhile(_.validKey.nonEmpty).flatMap(_.validKey).toList

  def startingPath: HPath = prefix.map(_.startingPath).getOrElse(this)

  def prefix = findChild[HPath]

  def validKey = findChild[HKey].filter(_.isValidKey)
}

sealed trait HValue extends HoconPsiElement with HInnerElement {
  def forParent[T](forValuedField: HValuedField => T, forArray: HArray => T, forConcatenation: HConcatenation => T): T =
    parent match {
      case vf: HValuedField => forValuedField(vf)
      case arr: HArray => forArray(arr)
      case conc: HConcatenation => forConcatenation(conc)
    }
}

final class HObject(ast: ASTNode) extends HoconPsiElement(ast) with HValue with HScope {
  def entries = getChild[HObjectEntries]

  def directKeyedFields = entries.directKeyedFields
}

final class HArray(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HSubstitution(ast: ASTNode) extends HoconPsiElement(ast) with HValue {
  def path = findChild[HPath]
}

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
