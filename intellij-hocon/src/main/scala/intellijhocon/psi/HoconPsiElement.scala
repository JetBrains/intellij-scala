package intellijhocon
package psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.{ContributedReferenceHost, PsiComment, PsiReferenceService}
import lexer.{HoconTokenSets, HoconTokenType}
import parser.HoconPsiParser

import scala.reflect.{ClassTag, classTag}

sealed abstract class HoconPsiElement(ast: ASTNode) extends ASTWrapperPsiElement(ast) {
  def getChild[T <: HoconPsiElement : ClassTag]: T =
    findChildByClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])

  def findChild[T <: HoconPsiElement : ClassTag] =
    Option(getChild[T])

  def allChildren =
    Iterator.iterate(getFirstChild)(_.getNextSibling).takeWhile(_ != null)

  def nonWhitespaceChildren =
    allChildren.filterNot(ch => HoconTokenSets.Whitespace.contains(ch.getNode.getElementType))

  def findChildren[T <: HoconPsiElement : ClassTag] =
    allChildren.collect {
      case t: T => t
    }
}

sealed trait HValue extends HoconPsiElement

sealed trait HLiteral extends HValue

sealed trait HObjectEntry extends HoconPsiElement

final class HObjectEntries(ast: ASTNode) extends HoconPsiElement(ast) {
  def entries = findChildren[HObjectEntry]

  def fields = findChildren[HObjectField]

  def includes = findChildren[HInclude]
}

final class HObjectField(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry {
  def bareField = getChild[HBareObjectField]

  def docComments = nonWhitespaceChildren
          .takeWhile(_.getNode.getElementType == HoconTokenType.HashComment)
          .map(ch => ch.asInstanceOf[PsiComment])

  def path = bareField.path

  def value = bareField.value

  def separator = bareField.separator
}

final class HBareObjectField(ast: ASTNode) extends HoconPsiElement(ast) {
  def path = findChild[HPath]

  def value = findChild[HValue]

  def separator = Option(findChildByType(HoconTokenSets.PathValueSeparator))
          .map(_.getNode.getElementType.asInstanceOf[HoconTokenType])
}

final class HInclude(ast: ASTNode) extends HoconPsiElement(ast) with HObjectEntry

final class HIncluded(ast: ASTNode) extends HoconPsiElement(ast)

final class HKey(ast: ASTNode) extends HoconPsiElement(ast)

final class HPath(ast: ASTNode) extends HoconPsiElement(ast)

final class HObject(ast: ASTNode) extends HoconPsiElement(ast) with HValue {
  def entries = getChild[HObjectEntries]
}

final class HArray(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HSubstitution(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HConcatenation(ast: ASTNode) extends HoconPsiElement(ast) with HValue

final class HNull(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral

final class HBoolean(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral

final class HNumber(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral

final class HUnquotedString(ast: ASTNode) extends HoconPsiElement(ast)

final class HString(ast: ASTNode) extends HoconPsiElement(ast) with HLiteral with ContributedReferenceHost {
  def stringType = getFirstChild.getNode.getElementType

  def isClosed = stringType match {
    case HoconTokenType.QuotedString =>
      HoconPsiParser.ProperlyClosedQuotedString.pattern.matcher(getText).matches
    case HoconTokenType.MultilineString =>
      getText.endsWith("\"\"\"")
    case _ =>
      true
  }

  override def getReferences =
    PsiReferenceService.getService.getContributedReferences(this)
}
