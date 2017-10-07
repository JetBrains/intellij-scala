package org.jetbrains.plugins.hocon.psi

import java.{lang => jl}

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.hocon.CommonUtil._
import org.jetbrains.plugins.hocon.HoconConstants
import org.jetbrains.plugins.hocon.HoconConstants._
import org.jetbrains.plugins.hocon.lexer.{HoconTokenSets, HoconTokenType}
import org.jetbrains.plugins.hocon.parser.HoconElementType
import org.jetbrains.plugins.hocon.ref.{HKeySelfReference, IncludedFileReferenceSet}

import scala.reflect.{ClassTag, classTag}

sealed abstract class HoconPsiElement(ast: ASTNode) extends ASTWrapperPsiElement(ast) {
  type Parent <: PsiElement

  override def getContainingFile: HoconPsiFile =
    super.getContainingFile.asInstanceOf[HoconPsiFile]

  def parent: Option[Parent] = getParent match {
    case p: PsiElement => Option(p.asInstanceOf[Parent])
    case _ => None
  }

  def parents: Iterator[HoconPsiElement] =
    Iterator.iterate(this)(_.parent match {
      case Some(he: HoconPsiElement) => he
      case _ => null
    }).takeWhile(_ != null)

  def elementType: IElementType =
    getNode.getElementType

  def getChild[T >: Null : ClassTag]: T =
    findChildByClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])

  def findChild[T >: Null : ClassTag] =
    Option(getChild[T])

  def findLastChild[T >: Null : ClassTag]: Option[PsiElement with T] =
    allChildrenReverse.collectFirst({ case t: T => t })

  def allChildren: Iterator[PsiElement] =
    Iterator.iterate(getFirstChild)(_.getNextSibling).takeWhile(_ != null)

  def allChildrenReverse: Iterator[PsiElement] =
    Iterator.iterate(getLastChild)(_.getPrevSibling).takeWhile(_ != null)

  def prevSibling =
    Option(getPrevSibling)

  def prevSiblings: Iterator[PsiElement] =
    Iterator.iterate(getPrevSibling)(_.getPrevSibling).takeWhile(_ != null)

  def nextSibling =
    Option(getNextSibling)

  def nextSiblings: Iterator[PsiElement] =
    Iterator.iterate(getNextSibling)(_.getNextSibling).takeWhile(_ != null)

  def nonWhitespaceChildren: Iterator[PsiElement] =
    allChildren.filterNot(ch => ch.getNode.getElementType == TokenType.WHITE_SPACE)

  def nonWhitespaceOrCommentChildren: Iterator[PsiElement] =
    allChildren.filterNot(ch =>
      (HoconTokenSets.Comment | TokenType.WHITE_SPACE).contains(ch.getNode.getElementType))

  def findChildren[T <: HoconPsiElement : ClassTag]: Iterator[T] =
    allChildren.collect {
      case t: T => t
    }
}

sealed trait HInnerElement extends HoconPsiElement {
  type Parent <: HoconPsiElement
}

final class HObjectEntries(ast: ASTNode) extends HoconPsiElement(ast) with HScope {
  def forParent[T](forFile: HoconPsiFile => T, forObject: HObject => T): T = parent match {
    case Some(file: HoconPsiFile) => forFile(file)
    case Some(obj: HObject) => forObject(obj)
  }

  def isToplevel: Boolean = forParent(_ => true, _ => false)

  def prefixingField: Option[HValuedField] = forParent(_ => None, obj => obj.prefixingField)

  def entries: Iterator[HObjectEntry] = findChildren[HObjectEntry]

  def objectFields: Iterator[HObjectField] = findChildren[HObjectField]

  def includes: Iterator[HInclude] = findChildren[HInclude]

  def directKeyedFields: Iterator[HKeyedField] = objectFields.flatMap(_.directKeyedFields)
}

sealed trait HObjectEntry extends HoconPsiElement with HInnerElement {
  type Parent = HObjectEntries

  def previousEntry: Option[HObjectEntry] = prevSiblings.collectFirst({ case e: HObjectEntry => e })

  def nextEntry: Option[HObjectEntry] = nextSiblings.collectFirst({ case e: HObjectEntry => e })
}

final class HObjectField(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry with HScope {
  def docComments: Iterator[PsiComment] = nonWhitespaceChildren
    .takeWhile(_.getNode.getElementType == HoconTokenType.HashComment)
    .map(ch => ch.asInstanceOf[PsiComment])

  def keyedField: HKeyedField = getChild[HKeyedField]

  def endingValue: Option[HValue] = keyedField.endingField.endingValue

  def directKeyedFields = Iterator(keyedField)

  // there may be bound comments and text offset should be at the beginning of path
  override def getTextOffset: Int = keyedField.getTextOffset
}

sealed trait HKeyedField extends HoconPsiElement with HInnerElement with HScope {
  def forParent[T](forKeyedParent: HKeyedField => T, forObjectField: HObjectField => T): T = parent match {
    case Some(kf: HKeyedField) => forKeyedParent(kf)
    case Some(of: HObjectField) => forObjectField(of)
  }

  def key: Option[HKey] = findChild[HKey]

  def validKey: Option[HKey] = key.filter(_.isValidKey)

  /**
    * Goes up the tree in order to determine full path under which this keyed field is defined.
    * Stops when encounters file-toplevel entries or an array (including array-append field).
    *
    * @return stream of all encountered keyed fields (in bottom-up order, i.e. starting with itself)
    */
  def fieldsInAllPathsBackward: Stream[HKeyedField] =
    this #:: forParent(
      keyedField => keyedField.fieldsInAllPathsBackward,
      objectField => objectField.parent.map(_.forParent(
        _ => Stream.empty,
        obj => obj.prefixingField.map(_.fieldsInAllPathsBackward).getOrElse(Stream.empty)
      )).get
    )

  /**
    * Like [[fieldsInAllPathsBackward]] but returns [[HKey]]s instead of [[HKeyedField]]s, in reverse order (i.e. key
    * from this field is at the end) and ensures that all keys are valid. If not, [[None]] is returned.
    */
  def keysInAllPaths: Option[List[HKey]] = {
    def iterate(str: Stream[HKeyedField], acc: List[HKey]): Option[List[HKey]] =
      str match {
        case head #:: tail => head.validKey.flatMap(key => iterate(tail, key :: acc))
        case _ => Some(acc)
      }

    iterate(fieldsInAllPathsBackward, Nil)
  }

  def enclosingObjectField: HObjectField =
    forParent(keyedParent => keyedParent.enclosingObjectField, objectField => objectField)

  def enclosingEntries: HObjectEntries =
    enclosingObjectField.parent.get

  def fieldsInPathForward: Stream[HKeyedField]

  def fieldsInPathBackward: Stream[HKeyedField] =
    forParent(keyedField => this #:: keyedField.fieldsInPathBackward, _ => Stream(this))

  def startingField: HKeyedField =
    forParent(_.startingField, _ => this)

  def endingField: HValuedField

  def endingValue: Option[HValue] = endingField.value

  /**
    * Scopes present in whatever is on the right side of key in that keyed field.
    */
  def subScopes: Iterator[HScope]

  def directKeyedFields = Iterator(this)
}

final class HPrefixedField(ast: ASTNode) extends HoconPsiElement(ast) with HKeyedField {
  def subField: HKeyedField = getChild[HKeyedField]

  def fieldsInPathForward: Stream[HKeyedField] =
    this #:: subField.fieldsInPathForward

  def endingField: HValuedField = subField.endingField

  def subScopes = Iterator(subField)
}

final class HValuedField(ast: ASTNode) extends HoconPsiElement(ast) with HKeyedField {
  def value: Option[HValue] = findChild[HValue]

  def isArrayAppend: Boolean =
    separator.contains(HoconTokenType.PlusEquals)

  def separator: Option[HoconTokenType] = Option(findChildByType[PsiElement](HoconTokenSets.KeyValueSeparator))
    .map(_.getNode.getElementType.asInstanceOf[HoconTokenType])

  def fieldsInPathForward: Stream[HKeyedField] =
    Stream(this)

  def endingField: HValuedField = this

  def subScopes: Iterator[HObject] =
    if (isArrayAppend) Iterator.empty
    else value.collect {
      case obj: HObject => Iterator(obj)
      case conc: HConcatenation => conc.findChildren[HObject]
    }.getOrElse(Iterator.empty)
}

final class HInclude(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry {
  def included: HIncluded = getChild[HIncluded]

  // there may be bound comments and text offset should be on 'include' keyword
  override def getTextOffset: Int =
    allChildren.find(_.getNode.getElementType == HoconTokenType.UnquotedChars)
      .map(_.getTextOffset).getOrElse(super.getTextOffset)
}

final class HIncluded(ast: ASTNode) extends HoconPsiElement(ast) with HInnerElement {
  type Parent = HInclude

  def qualifier: Option[String] = getFirstChild.getNode.getElementType match {
    case HoconTokenType.UnquotedChars =>
      Some(getFirstChild.getText)
    case _ => None
  }

  def target: Option[HIncludeTarget] = findChild[HIncludeTarget]

  def fileReferenceSet: Option[IncludedFileReferenceSet] =
    for {
      hs <- target
      vf <- Option(getContainingFile.getOriginalFile.getVirtualFile)
      rs <- {
        val strVal = hs.stringValue

        val (absolute, forcedAbsolute, fromClasspath) = qualifier match {
          case Some(ClasspathQualifier) => (true, true, true)
          case None if !isValidUrl(strVal) =>
            val pfi = ProjectRootManager.getInstance(getProject).getFileIndex
            val fromClasspath = pfi.isInSource(vf) || pfi.isInLibraryClasses(vf)
            (strVal.trim.startsWith("/"), false, fromClasspath)
          case _ => (true, true, false)
        }

        // Include resolution is enabled for:
        // - classpath() includes anywhere
        // - unqualified includes in classpath (source or library) files
        // - relative unqualified includes in non-classpath files
        if (!absolute || fromClasspath)
          Some(new IncludedFileReferenceSet(strVal, hs, forcedAbsolute, fromClasspath))
        else
          None
      }
    } yield rs
}

final class HKey(ast: ASTNode) extends HoconPsiElement(ast) with HInnerElement {
  def forParent[T](forPath: HPath => T, forKeyedField: HKeyedField => T): T = parent match {
    case Some(path: HPath) => forPath(path)
    case Some(keyedField: HKeyedField) => forKeyedField(keyedField)
  }

  def allKeysFromToplevel: Option[List[HKey]] =
    forParent(
      path => path.allKeys,
      keyedEntry => keyedEntry.keysInAllPaths
    )

  def enclosingEntries: HObjectEntries =
    forParent(
      _ => getContainingFile.toplevelEntries,
      keyedField => keyedField.enclosingEntries
    )

  def stringValue: String = allChildren.collect {
    case keyPart: HKeyPart => keyPart.stringValue
    case other => other.getText
  }.mkString

  def keyParts: Iterator[HKeyPart] = findChildren[HKeyPart]

  def isValidKey: Boolean = findChild[PsiErrorElement].isEmpty

  override def getReference = new HKeySelfReference(this)
}

final class HPath(ast: ASTNode) extends HoconPsiElement(ast) with HInnerElement {
  def forParent[T](forPath: HPath => T, forSubstitution: HSubstitution => T): T = parent match {
    case Some(path: HPath) => forPath(path)
    case Some(subst: HSubstitution) => forSubstitution(subst)
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

  def prefix: Option[HPath] = findChild[HPath]

  def validKey: Option[HKey] = findChild[HKey].filter(_.isValidKey)
}

sealed trait HValue extends HoconPsiElement with HInnerElement {
  def forParent[T](forValuedField: HValuedField => Option[T], forArray: HArray => Option[T], forConcatenation: HConcatenation => Option[T]): Option[T] =
    parent match {
      case Some(vf: HValuedField) => forValuedField(vf)
      case Some(arr: HArray) => forArray(arr)
      case Some(conc: HConcatenation) => forConcatenation(conc)
      case _ => None
    }

  def prefixingField: Option[HValuedField] = forParent(
    vf => if (vf.isArrayAppend) None else Some(vf),
    _ => None,
    concat => concat.prefixingField
  )
}

final class HObject(ast: ASTNode) extends HoconPsiElement(ast) with HValue with HScope {
  def entries: HObjectEntries = getChild[HObjectEntries]

  def directKeyedFields: Iterator[HKeyedField] = entries.directKeyedFields
}

final class HArray(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HSubstitution(ast: ASTNode) extends HoconPsiElement(ast) with HValue {
  def path: Option[HPath] = findChild[HPath]
}

final class HConcatenation(ast: ASTNode) extends HoconPsiElement(ast) with HValue

sealed trait HLiteralValue extends HValue with PsiLiteral

final class HNull(ast: ASTNode) extends HoconPsiElement(ast) with HLiteralValue {
  def getValue: Object = null
}

final class HBoolean(ast: ASTNode) extends HoconPsiElement(ast) with HLiteralValue {
  def getValue: Object = jl.Boolean.valueOf(booleanValue)

  def booleanValue: Boolean = getText.toBoolean
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
  def stringType: IElementType = getFirstChild.getNode.getElementType

  def getValue: Object = stringValue

  def unquote: String = stringType match {
    case HoconTokenType.QuotedString =>
      getText.substring(1, getText.length - (if (isClosed) 1 else 0))
    case HoconTokenType.MultilineString =>
      getText.substring(3, getText.length - (if (isClosed) 3 else 0))
    case HoconElementType.UnquotedString =>
      getText
  }

  def stringValue: String = stringType match {
    case HoconTokenType.QuotedString =>
      StringUtil.unescapeStringCharacters(unquote)
    case _ =>
      unquote
  }

  def isClosed: Boolean = stringType match {
    case HoconTokenType.QuotedString =>
      HoconConstants.ProperlyClosedQuotedString.pattern.matcher(getText).matches
    case HoconTokenType.MultilineString =>
      getText.endsWith("\"\"\"")
    case _ =>
      true
  }

  override def getReferences: Array[PsiReference] =
    PsiReferenceService.getService.getContributedReferences(this)
}

final class HStringValue(ast: ASTNode) extends HoconPsiElement(ast) with HString with HLiteralValue

final class HKeyPart(ast: ASTNode) extends HoconPsiElement(ast) with HString {
  type Parent = HKey
}

final class HIncludeTarget(ast: ASTNode) extends HoconPsiElement(ast) with HString {
  type Parent = HIncluded

  def getFileReferences: Array[FileReference] =
    parent.flatMap(_.fileReferenceSet.map(_.getAllReferences)).getOrElse(FileReference.EMPTY)
}
