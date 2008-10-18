package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

/**
 * @author ilyas
 */

import annotations.Nullable
import api.ScalaFile
import api.statements._
import com.intellij.openapi.editor.Editor
import com.intellij.psi.stubs.IStubElementType
import stubs.ScTypeDefinitionStub
import _root_.scala.collection.immutable.Set
import api.base.{ScStableCodeReferenceElement, ScPrimaryConstructor}
import base.ScStableCodeReferenceElementImpl
import api.base.ScStableCodeReferenceElement
import api.base.types.ScTypeElement
import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.collection.mutable.HashSet
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.colors._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParamClause, ScTypeParam}
import psi.api.toplevel.packaging._
import psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.util.IncorrectOperationException
import com.intellij.util.IconUtil
import com.intellij.psi.impl._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.VisibilityIcons
import com.intellij.openapi.util.Iconable
import javax.swing.Icon
import synthetic.JavaIdentifier
import types.{ScSubstitutor, ScType}
import Misc._

abstract class ScTypeDefinitionImpl(node: ASTNode) extends ScalaStubBasedElementImpl[ScTypeDefinition](node) with ScTypeDefinition with PsiClassFake  {

  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getModifierList = super[ScTypeDefinition].getModifierList

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def hasModifierProperty(name: String) = super[ScTypeDefinition].hasModifierProperty(name)

  override def getContainingClass = super[ScTypeDefinition].getContainingClass

  override def getQualifiedName: String = {
    def _packageName(e: PsiElement): String = e.getParent match {
      case t: ScTypeDefinition => _packageName(t) + "." + t.name
      case p: ScPackaging => {
        val _packName = _packageName(p)
        if (_packName.length > 0) _packName + "." + p.getPackageName else p.getPackageName
      }
      case f: ScalaFile => f.getPackageName
      case null => ""
      case parent => _packageName(parent)
    }
    val packageName = _packageName(this)
    if (packageName.length > 0) packageName + "." + name else name
  }

  override def getPresentation(): ItemPresentation = {
    new ItemPresentation() {
      def getPresentableText(): String = name
      def getTextAttributesKey(): TextAttributesKey = null
      def getLocationString(): String = getPath match {
        case "" => "<default>"
        case p => '(' + p + ')'
      }
      override def getIcon(open: Boolean) = ScTypeDefinitionImpl.this.getIcon(0)
    }
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = functions.filter((m: PsiMethod) =>
          m.getName == name // todo check base classes
    ).toArray

  def extendsBlock: ScExtendsBlock = findChildByClass(classOf[ScExtendsBlock])

  override def checkDelete() {
  }
                                                                
  def members(): Seq[ScMember] = extendsBlock.members
  def functions(): Seq[ScFunction] = extendsBlock.functions
  def aliases(): Seq[ScTypeAlias] = extendsBlock.aliases

  def allTypes = TypeDefinitionMembers.getTypes(this).values.map{ n => (n.info, n.substitutor) }
  def allVals = TypeDefinitionMembers.getVals(this).values.map{ n => (n.info, n.substitutor) }
  def allMethods = TypeDefinitionMembers.getMethods(this).elements

  def typeDefinitions: Seq[ScTypeDefinition] = extendsBlock.typeDefinitions

  override def delete() = {
    var parent = getParent
    var remove: PsiElement = this
    while (parent.isInstanceOf[ScPackaging]) {
      remove = parent
      parent = parent.getParent
    }
    parent match {
      case f: ScalaFile => {
        if (f.typeDefinitions.length == 1) {
          f.delete
        } else {
          f.getNode.removeChild(remove.getNode)
        }
      }
      case e: ScalaPsiElement => e.getNode.removeChild(remove.getNode)
      case _ => throw new IncorrectOperationException("Invalid type definition")
    }
  }

  override def getTypeParameters = typeParameters.toArray

  override def getSupers: Array[PsiClass] = extendsBlock.supers.toArray

  override def setName(name: String): PsiElement = {
    val id = nameId.getNode
    val parent = id.getTreeParent
    def shortName(s: String): String = {
      if (!s.endsWith(".scala")) return null
      else return s.substring(0, s.length - 6)
    }
    if (id.getText == shortName(id.getPsi.getContainingFile.getName)) {
      this.getParent match {
        case x: ScalaFile => x.setName(name + ".scala")
        case _ =>
      }
    }

    super.setName(name)
  }

  override def getMethods = functions.toArray

  override def getAllMethods: Array[PsiMethod] = {
    val methods = TypeDefinitionMembers.getMethods(this)
    return methods.toArray.map[PsiMethod](_.method)
  }

  def superTypes() = extendsBlock.superTypes

  import com.intellij.psi.scope.PsiScopeProcessor

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (lastParent == null) extendsBlock.processDeclarations(processor, state, null, place) else true
  }

  override def isInheritor(clazz: PsiClass, deep: Boolean) = {
    def isInheritorInner(base: PsiClass, drv: PsiClass, deep: Boolean, visited: Set[PsiClass]): Boolean = {
      if (visited.contains(drv)) false
      else drv match {
        case drv: ScTypeDefinition => drv.superTypes.find{
          t => ScType.extractClassType(t) match {
            case Some((c, _)) => c == clazz || (deep && isInheritorInner(base, c, deep, visited + drv))
            case _ => false
          }
        }
        case _ => drv.getSuperTypes.find{ psiT =>
                val c = psiT.resolveGenerics.getElement
                if (c == null) false else c == clazz || (deep && isInheritorInner(base, c, deep, visited + drv))
        }
      }
    }
    isInheritorInner(clazz, this, deep, Set.empty)
  }

  def functionsByName(name: String) =
    for (sig <- TypeDefinitionMembers.getMethods(this) if sig.method == name) yield sig.method

  def addMember(member: ScMember, anchor: Option[PsiElement]): ScMember = {
    extendsBlock.templateBody match {
      case Some(body) => {
        val before = anchor match {case Some(anchor) => anchor.getNode; case None => body.getNode.getLastChildNode}
        body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(member.getManager), before)
        body.getNode.addChild(member.getNode, before)
        body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(member.getManager), before)
      }
      case None => {
        extendsBlock.getNode.addChild(ScalaPsiElementFactory.createBodyFromMember(member, member.getManager).getNode)
        return members.apply(0)
      }
    }
    return member
  }

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  override def getIcon(flags: Int) = {
    val icon = getIconInner
    val isLocked = (flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !isWritable
    val rowIcon = ElementBase.createLayeredIcon(icon,  0)
    if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
      VisibilityIcons.setVisibilityIcon(getModifierList, rowIcon);
    }
    rowIcon
  }

  protected def getIconInner: Icon
}