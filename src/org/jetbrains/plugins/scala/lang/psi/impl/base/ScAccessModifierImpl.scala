package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.ACCESS_MODIFIER
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAccessModifierStub

import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
class ScAccessModifierImpl private(stub: ScAccessModifierStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ACCESS_MODIFIER, node) with ScAccessModifier {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScAccessModifierStub) = this(stub, null)

  override def toString: String = "AccessModifier"

  def idText: Option[String] =
    byStubOrPsi(_.idText)(Option(getNode.findChildByType(tIDENTIFIER)).map(_.getPsi.getText))

  def scope: PsiNamedElement =
    Option(getReference) map {
      _.resolve
    } collect {
      case o: ScObject if o.isPackageObject => ScPackageImpl.ofPackageObject(o)
      case named: PsiNamedElement => named
    } getOrElse {
      getParentOfType(this, classOf[ScTypeDefinition], true)
    }

  //return ref only for {private|protected}[Id], not for private[this]
  def isProtected: Boolean = byStubOrPsi(_.isProtected)(getNode.hasChildOfType(kPROTECTED))

  def isPrivate: Boolean = byStubOrPsi(_.isPrivate)(getNode.hasChildOfType(kPRIVATE))

  def isThis: Boolean = byStubOrPsi(_.isThis)(getNode.hasChildOfType(kTHIS))

  override def getReference: PsiReference = {
    val text = idText
    if (text.isEmpty) null
    else new PsiReference {
      def getElement: ScAccessModifierImpl = ScAccessModifierImpl.this

      def getRangeInElement: TextRange = {
        val id = findChildByType[PsiElement](tIDENTIFIER)
        new TextRange(0, id.getTextLength).shiftRight(id.getStartOffsetInParent)
      }

      def getCanonicalText: String = resolve() match {
        case td: ScTypeDefinition => td.qualifiedName
        case p: PsiPackage => p.getQualifiedName
        case _ => null
      }

      def isSoft = false

      def handleElementRename(newElementName: String): ScAccessModifierImpl = doRename(newElementName)

      def bindToElement(e: PsiElement): ScAccessModifierImpl = e match {
        case td: ScTypeDefinition => doRename(td.name)
        case p: PsiPackage => doRename(p.name)
        case _ => throw new IncorrectOperationException("cannot bind to anything but type definition or package")
      }

      private def doRename(newName: String) = {
        val id = findChildByType[PsiElement](tIDENTIFIER)
        val parent = id.getNode.getTreeParent
        parent.replaceChild(id.getNode, createIdentifier(newName))
        ScAccessModifierImpl.this
      }

      def isReferenceTo(element: PsiElement): Boolean = element match {
        case td: ScTypeDefinition => td.name == text.get && resolve == td
        case p: PsiPackage => p.name == text.get && resolve == p
        case _ => false
      }

      def resolve(): PsiElement = {
        val name = text.get
        def findPackage(qname: String): PsiPackage = {
          var pack: PsiPackage = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(qname))
          while (pack != null) {
            if (pack.name == name) return pack
            pack = pack.getParentPackage
          }
          null
        }

        def find(e: PsiElement): PsiNamedElement = e match {
          case null => null
          case td: ScTypeDefinition if td.name == name => td
          case _: ScalaFile => findPackage("")
          case container: ScPackaging => findPackage(container.fullPackageName)
          case _ => find(e.getParent)
        }
        find(getParent)
      }

      def getVariants: Array[Object] = {
        val buff = new ArrayBuffer[Object]
        def processPackages(qname: String) {
          var pack: PsiPackage = ScPackageImpl(JavaPsiFacade.getInstance(getProject).findPackage(qname))
          while (pack != null && pack.name != null) {
            buff += pack
            pack = pack.getParentPackage
          }
        }
        def append(e: PsiElement) {
          e match {
            case null =>
            case td: ScTypeDefinition => buff += td; append(td.getParent)
            case _: ScalaFile => processPackages("")
            case container: ScPackaging => processPackages(container.fullPackageName)
            case _ => append(e.getParent)
          }
        }
        append(getParent)
        buff.toArray
      }
    }
  }


  def access: ScAccessModifier.Type.Value = {
    assert(isPrivate || isProtected)
    if (isPrivate && isThis) ScAccessModifier.Type.THIS_PRIVATE
    else if (isPrivate) ScAccessModifier.Type.PRIVATE
    else if (isThis) ScAccessModifier.Type.THIS_PROTECTED
    else ScAccessModifier.Type.PROTECTED
  }
}
