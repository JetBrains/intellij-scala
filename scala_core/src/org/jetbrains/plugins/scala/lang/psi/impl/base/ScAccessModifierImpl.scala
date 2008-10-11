package org.jetbrains.plugins.scala.lang.psi.impl.base

import _root_.scala.collection.mutable.ArrayBuffer
import api.base.ScAccessModifier
import api.ScalaFile
import api.toplevel.packaging.ScPackageContainer
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.util.IncorrectOperationException
import psi.ScalaPsiElementImpl
import lexer.ScalaTokenTypes

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil;

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScAccessModifierImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScAccessModifier{
  override def toString: String = "AccessModifier"

  def scope() = getReference match {
    case null => PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition], true)
    case ref => ref.resolve match {case named : PsiNamedElement => named}
  }

  //return ref only for {private|protected}[Id], not for private[this]
  override def getReference = {
    val id = findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (id == null) null else new PsiReference {
      def getElement = ScAccessModifierImpl.this
      def getRangeInElement = new TextRange(0, id.getTextLength).shiftRight(id.getStartOffsetInParent)
      def getCanonicalText = resolve match {
        case td : ScTypeDefinition => td.getQualifiedName
        case p : PsiPackage => p.getQualifiedName
        case _ => null
      }
      def isSoft() = false

      def handleElementRename(newElementName: String) = doRename(newElementName)
      def bindToElement(e : PsiElement) = e match {
        case td : ScTypeDefinition => doRename(td.name)
        case p : PsiPackage => doRename(p.getName)
        case _ => throw new IncorrectOperationException("cannot bind to anything but type definition or package")
      }

      private def doRename(newName : String) = {
        val parent = id.getNode.getTreeParent
        parent.replaceChild(id.getNode, ScalaPsiElementFactory.createIdentifier(newName, getManager))
        ScAccessModifierImpl.this
      }

      def isReferenceTo(element: PsiElement) = element match {
        case td : ScTypeDefinition => td.name == id.getText && resolve == td
        case p : PsiPackage => p.getName == id.getText && resolve == p
        case _ => false
      }

      def resolve(): PsiElement = {
        val name = id.getText
        def findPackage(qname : String) : PsiPackage = {
          var pack = JavaPsiFacade.getInstance(getProject).findPackage(qname)
          while (pack != null) {
            if (pack.getName == name) return pack
            pack = pack.getParentPackage
          }
          null
        }

        def find(e : PsiElement) : PsiNamedElement = e match {
          case null => null
          case td : ScTypeDefinition if td.name == name => td
          case file : ScalaFile => findPackage(file.getPackageName)
          case container : ScPackageContainer => findPackage(container.fqn)
          case _ => find(e.getParent)
        }
        find(getParent)
      }

      def getVariants(): Array[Object] = {
        val buff = new ArrayBuffer[Object]
        def processPackages(qname : String) = {
          var pack = JavaPsiFacade.getInstance(getProject).findPackage(qname)
          while (pack != null) {
            buff += pack
            pack = pack.getParentPackage
          }
        }
        def append(e : PsiElement) : Unit = e match {
          case null =>
          case td : ScTypeDefinition => buff += td; append(td.getParent)
          case file : ScalaFile => processPackages(file.getPackageName)
          case container : ScPackageContainer => processPackages(container.fqn)
          case _ => append(e.getParent)
        }
        append(getParent)
        buff.toArray
      }
    }
  }


  def access() = {
    val isThis = findChildByType(ScalaTokenTypes.kTHIS) != null
    val isPrivate = findChildByType(ScalaTokenTypes.kPRIVATE) != null
    val isProtected = findChildByType(ScalaTokenTypes.kPROTECTED) != null
    assert(isPrivate || isProtected)
    if (isPrivate) if (isThis) Access.THIS_PRIVATE else Access.PRIVATE
    else if (isThis) Access.THIS_PROTECTED else Access.PROTECTED 
  }
}