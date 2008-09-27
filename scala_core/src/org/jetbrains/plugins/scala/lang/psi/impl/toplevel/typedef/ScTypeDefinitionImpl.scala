package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

/**
 * @author ilyas
 */

import annotations.Nullable
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
import types.{ScSubstitutor, ScType}
import Misc._

abstract class ScTypeDefinitionImpl(node: ASTNode) extends ScalaStubBasedElementImpl[ScTypeDefinition](node) with ScTypeDefinition with PsiClassFake  {

  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)

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

  protected def getIconInner: Icon

  override def getIcon(flags: Int): Icon = {
    if (!isValid) return null
    val icon = getIconInner
    val isLocked = (flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !isWritable()
    val rowIcon = ElementBase.createLayeredIcon(icon, /*ElementPresentationUtil.getFlags(this, isLocked)*/ 0)
    if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
      VisibilityIcons.setVisibilityIcon(getModifierList, rowIcon);
    }
    rowIcon
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = functions.filter((m: PsiMethod) =>
          m.getName == name // todo check base classes
    ).toArray

  def extendsBlock: ScExtendsBlock = findChildByClass(classOf[ScExtendsBlock])

  override def checkDelete() {
  }
                                                                
  def members(): Seq[ScMember] = {
    val bodyMembers = extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.members
    }
    val earlyMembers = extendsBlock.earlyDefinitions match {
      case None => Seq.empty
      case Some(earlyDefs) => earlyDefs.members
    }

    bodyMembers ++ earlyMembers
  }

  def functions(): Seq[ScFunction] =
    extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.functions
    }

  def aliases(): Seq[ScTypeAlias] =
    extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.aliases
    }

  def allTypes = TypeDefinitionMembers.getTypes(this).values.map{
    n => (n.info, n.substitutor)
  }
  def allVals = TypeDefinitionMembers.getVals(this).values.map{
    n => (n.info, n.substitutor)
  }
  def allMethods = TypeDefinitionMembers.getMethods(this).values.map{
    n => n.info
  }

  def innerTypeDefinitions: Seq[ScTypeDefinition] =
    (extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.typeDefinitions
    })

  override def delete() = {
    var parent = getParent
    var remove: PsiElement = this
    while (parent.isInstanceOf[ScPackaging]) {
      remove = parent
      parent = parent.getParent
    }
    parent match {
      case f: ScalaFile => {
        if (f.getTypeDefinitions.length == 1) {
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

  override def getMethods = functions.toArray

  override def getAllMethods: Array[PsiMethod] = {
    val methods = TypeDefinitionMembers.getMethods(this)
    return methods.toArray.map[PsiMethod](_._1.method)
  }

  def superTypes() = extendsBlock.superTypes

  import com.intellij.psi.scope.PsiScopeProcessor

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    extendsBlock.templateParents match {
      case Some(p) if (PsiTreeUtil.isAncestor(p, place, true)) => {
        extendsBlock.earlyDefinitions match {
          case Some(ed) => for (m <- ed.members) {
            m match {
              case _var: ScVariable => for (declared <- _var.declaredElements) {
                if (!processor.execute(declared, state)) return false
              }
              case _val: ScValue => for (declared <- _val.declaredElements) {
                if (!processor.execute(declared, state)) return false
              }
            }
          }
          case None =>
        }
        true
      }
      case _ =>
        extendsBlock.earlyDefinitions match {
          case Some(ed) if PsiTreeUtil.isAncestor(ed, place, true) =>
          case _ => if (!TypeDefinitionMembers.processDeclarations(this, processor, state, lastParent, place)) return false
        }

        true
    }
  }

  override def getContainingClass: PsiClass = getParent match {
    case eb: ScExtendsBlock => eb.getParent.asInstanceOf[ScTypeDefinition]
    case _ => null
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
        case _ => drv.getSuperTypes.find{
          psiT =>
                  val c = psiT.resolveGenerics.getElement
                  if (c == null) false else c == clazz || (deep && isInheritorInner(base, c, deep, visited + drv))
        }
      }
    }
    isInheritorInner(clazz, this, deep, Set.empty)
  }

  def functionsByName(name: String) =
    for ((_, n) <- TypeDefinitionMembers.getMethods(this) if n.info.method == name) yield n.info.method

  def addMember(member: PsiElement, edit: Option[Editor], pos: Int) {
    val editor: Editor = edit match {case None => null case Some(x) => x}
    var meth: PsiElement = member
    //checking member
    meth match {
      case _: ScValue | _: ScFunction | _: ScVariable | _: ScTypeAlias =>
      case _ => return
    }
    val body = extendsBlock.templateBody match {
      case Some(x) => x
      case None => return
    }
    //if body is not empty
    if (body.getChildren.length != 0) {
      val offset = if (editor != null) editor.getCaretModel.getOffset
                   else if (pos != -1) pos else body.getTextRange.getStartOffset
      //current element
      var element = body.getContainingFile.findElementAt(offset)
      while (element != null && element.getParent != body) element = element.getParent
      if (element == null) return
      //Look at some exceptions
      val t = element.getNode.getElementType
      element.getNode.getElementType match {
        case ScalaTokenTypes.tLINE_TERMINATOR | TokenType.WHITE_SPACE => element = element.getNextSibling
        case ScalaTokenTypes.tLBRACE => {
          element = element.getNextSibling
          element.getNode.getElementType match {
            case ScalaTokenTypes.tLINE_TERMINATOR => element = element.getNextSibling
            case _ => body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), element.getNode)
          }
        }
        case _ =>
      }
      //now we can add new statement before this element or after if it is the end
      body.getNode.addChild(meth.getNode, element.getNode)
      body.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), element.getNode)
    } else {
      val newBody: ScTemplateBody = body.replace(ScalaPsiElementFactory.
          createBodyFromMember(meth, meth.getManager)).asInstanceOf[ScTemplateBody]
      meth = member match {
        case _: ScTypeAlias => newBody.aliases(0)
        case _: ScFunction => newBody.functions(0)
        case _: ScValue | _: ScVariable => newBody.members(0)
      }
      newBody.getNode.addChild(ScalaPsiElementFactory.createNewLineNode(meth.getManager), meth.getNode)
    }
    if (editor != null) {
      val body: PsiElement = meth match {
        case meth: ScTypeAliasDefinition => meth.aliasedTypeElement
        case meth: ScPatternDefinition => meth.expr
        case meth: ScVariableDefinition => meth.expr
        case method: ScFunctionDefinition => method.body match {
          case Some(x) => x
          case None => return
        }
        case _ => return
      }
      val offset = body.getTextRange.getStartOffset
      editor.getCaretModel.moveToOffset(offset)
      editor.getSelectionModel.setSelection(body.getTextRange.getStartOffset, body.getTextRange.getEndOffset)
    }
  }
}