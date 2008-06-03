package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

/**
 * @author ilyas
 */

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiClass}
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
import com.intellij.openapi.util.Pair
import com.intellij.psi._
import com.intellij.navigation._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.meta.PsiMetaData
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations._
import com.intellij.util.IconUtil
import com.intellij.psi.impl._
import com.intellij.util.VisibilityIcons
import com.intellij.openapi.util.Iconable
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction


abstract class ScTypeDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeDefinition with PsiClassFake {
  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getQualifiedName: String = {

    var parent = getParent
    // todo improve formatter
    var nameList: List[String] = Nil
    // todo type-pattern matchin bug
    while (parent != null) {
      parent match {
        case t: ScTypeDefinition => nameList = t.getName :: nameList
        case p: ScPackaging => nameList = p.getPackageName :: nameList
        case f: ScalaFile if f.getPackageName.length > 0 => nameList = f.getPackageName :: nameList
        case _ =>
      }
      parent = parent.getParent
    }
    return (nameList :\ getName)((x: String, s: String) => x + "." + s)
  }

  override def getPresentation(): ItemPresentation = {
    new ItemPresentation() {

      import org.jetbrains.plugins.scala._
      import org.jetbrains.plugins.scala.icons._

      def getPresentableText(): String = {
        getName
      }
      override def getTextAttributesKey(): TextAttributesKey = null
      override def getLocationString(): String = getPath match {
        case "" => ""
        case _ => '(' + getPath + ')'
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

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = methods.filter((m: PsiMethod) =>
          m.getName == name // todo check base classes
  ).toArray

  override def getMethods: Array[PsiMethod] = methods.toArray

  def extendsBlock: ScExtendsBlock = findChildByClass(classOf[ScExtendsBlock])

  def members(): Seq[ScMember] =
    (extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.members
    }) ++ findChildrenByClass(classOf[ScMember])

  def functions(): Seq[ScMember] =
    (extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.functions
    }) ++ findChildrenByClass(classOf[ScFunction])

  def typeDefinitions: Seq[ScTypeDefinition] = 
    (extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.typeDefinitions
    })

  override def delete() = {
    var parent = getParent
    while (parent.isInstanceOf[ScPackaging]) {
      parent = parent.getParent
    }
    parent match {
      case f: ScalaFile => {
        if (f.getTypeDefinitions.length == 1) {
          f.delete
        } else {
          val node = f.getNode
          if (node != null){
            node.removeChild(getNode)
          }
        }
      }
      case e: ScalaPsiElement => {
        val node = e.getNode
        if (node != null){
          node.removeChild(getNode)
        }
      }
      case _ => throw new IncorrectOperationException("Invalid type definition")
    }
  }

  override def getTypeParameters = typeParameters.toArray
}