package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

/**
 * @author ilyas
 */

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
import api.statements.{ScFunction, ScTypeAlias}
import types.ScSubstitutor

abstract class ScTypeDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeDefinition with PsiClassFake {
  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getQualifiedName: String = {
    def _packageName(e: PsiElement): String = e.getParent match {
      case t: ScTypeDefinition => _packageName(t) + "." + t.name
      case p: ScPackaging => _packageName(p) + "." + p.getPackageName
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
    val rowIcon = ElementBase.createLayeredIcon(icon, ElementPresentationUtil.getFlags(this, isLocked))
    if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
      VisibilityIcons.setVisibilityIcon(getModifierList, rowIcon);
    }
    rowIcon
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = functions.filter((m: PsiMethod) =>
          m.getName == name // todo check base classes
  ).toArray

  def extendsBlock: ScExtendsBlock = findChildByClass(classOf[ScExtendsBlock])

  override def checkDelete() {}

  def members(): Seq[ScMember] =
    (extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.members
    }) ++ findChildrenByClass(classOf[ScMember])

  def functions(): Seq[ScFunction] =
    (extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.functions
    }) ++ findChildrenByClass(classOf[ScFunction])

  def aliases(): Seq[ScTypeAlias] =
    extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.aliases
    }

  def typeDefinitions: Seq[ScTypeDefinition] =
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

  override def getMethods = functions.toArray

  def superTypes() = extendsBlock.superTypes

  import com.intellij.psi.scope.{PsiScopeProcessor, ElementClassHint}

  import TypeDefinitionMembers.{ValueNodes, TypeNodes}

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (!processor.execute(this, state)) return false
    extendsBlock.templateParents match {
      case Some(p) if (PsiTreeUtil.isAncestor(p, place, true)) => true
      case _ =>
        val classHint = processor.getHint(classOf[ElementClassHint])

        if (shouldProcessVals(processor)) {
          for ((_, n) <- TypeDefinitionMembers.getVals(this)) {
            if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor))) return false
          }
        }
        if (shouldProcessMethods(processor)) {
          for ((sig, _) <- TypeDefinitionMembers.getMethods(this)) {
            if (!processor.execute(sig.method, state.put(ScSubstitutor.key, sig.substitutor))) return false
          }
        }
        if (shouldProcessTypes(processor)) {
          for ((_, n) <- TypeDefinitionMembers.getTypes(this)) {
            if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor))) return false
          }
        }
        true
    }
  }

  import scala.lang.resolve._, scala.lang.resolve.ResolveTargets._

  def shouldProcessVals(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => (kinds contains VAR) || (kinds contains VAL) || (kinds contains OBJECT)
    case _ => {
      val hint = processor.getHint(classOf[ElementClassHint])
      hint == null || hint.shouldProcess(classOf[PsiVariable])
    }
  }

  def shouldProcessMethods(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains METHOD
    case _ => {
      val hint = processor.getHint(classOf[ElementClassHint])
      hint == null || hint.shouldProcess(classOf[PsiMethod])
    }
  }

  def shouldProcessTypes(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains CLASS
    case _ => false //important: do not process inner classes!
  }
}