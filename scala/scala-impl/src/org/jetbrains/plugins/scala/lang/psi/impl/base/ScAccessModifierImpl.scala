package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAccessModifierStub

import scala.annotation.tailrec
import scala.collection.mutable

final class ScAccessModifierImpl private(stub: ScAccessModifierStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.ACCESS_MODIFIER, node) with ScAccessModifier {

  import ScalaTokenTypes._

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScAccessModifierStub) = this(stub, null)

  override def toString: String = "AccessModifier"

  override def idText: Option[String] = byStubOrPsi(_.idText) {
    Option(findId).map(_.getText)
  }

  //return ref only for {private|protected}[Id], not for private[this]
  override def isProtected: Boolean = byStubOrPsi(_.isProtected) {
    getFirstChild.getNode.getElementType == kPROTECTED
  }

  override def isPrivate: Boolean = byStubOrPsi(_.isPrivate) {
    getFirstChild.getNode.getElementType == kPRIVATE
  }

  override def isThis: Boolean = byStubOrPsi(_.isThis) {
    findChildByType(kTHIS) != null
  }

  override def getReference: PsiReference =
    idText.map(new AccessModifierReference(_))
      .orNull

  private def findId = findChildByType[PsiElement](tIDENTIFIER)

  private class AccessModifierReference(private val text: String) extends PsiReference {

    override def getElement: ScAccessModifier = ScAccessModifierImpl.this

    override def getRangeInElement: TextRange = {
      val id = findId
      new TextRange(0, id.getTextLength).shiftRight(id.getStartOffsetInParent)
    }

    override def getCanonicalText: String = resolve() match {
      case td: ScTypeDefinition => td.qualifiedName
      case p: PsiPackage => p.getQualifiedName
      case _ => null
    }

    override def isSoft = false

    override def handleElementRename(newElementName: String): ScAccessModifier = {
      val idNode = findId.getNode
      idNode.getTreeParent.replaceChild(idNode, ScalaPsiElementFactory.createIdentifier(newElementName))

      getElement
    }

    override def bindToElement(element: PsiElement): ScAccessModifier = {
      val newElementName = element match {
        case td: ScTypeDefinition => td.name
        case p: PsiPackage => p.name
        case _ => throw new IncorrectOperationException("cannot bind to anything but type definition or package")
      }

      handleElementRename(newElementName)
    }

    override def isReferenceTo(element: PsiElement): Boolean = element match {
      case td: ScTypeDefinition => td.name == text && resolve == td
      case p: PsiPackage => p.name == text && resolve == p
      case _ => false
    }

    override def resolve(): PsiElement = {
      def findPackage(qname: String): PsiPackage = {
        var pack: PsiPackage = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(qname))
        while (pack != null) {
          if (pack.name == text) return pack
          pack = pack.getParentPackage
        }
        null
      }

      @tailrec
      def find(e: PsiElement): PsiNamedElement = e match {
        case null => null
        case td: ScTypeDefinition if td.name == text => td
        case _: ScalaFile => findPackage("")
        case container: ScPackaging => findPackage(container.fullPackageName)
        case _ => find(e.getParent)
      }

      find(getParent)
    }

    override def getVariants: Array[Object] = {
      val buffer = mutable.ArrayBuffer.empty[Object]

      def processPackages(qname: String): Unit = {
        var pack: PsiPackage = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(qname))
        while (pack != null && pack.name != null) {
          buffer += pack
          pack = pack.getParentPackage
        }
      }

      @scala.annotation.tailrec
      def append(e: PsiElement): Unit = {
        e match {
          case null =>
          case td: ScTypeDefinition =>
            buffer += td
            append(td.getParent)
          case _: ScalaFile => processPackages("")
          case container: ScPackaging => processPackages(container.fullPackageName)
          case _ => append(e.getParent)
        }
      }

      append(getParent)
      buffer.toArray
    }
  }

}
