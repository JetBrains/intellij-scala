package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.colors._

import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.lexer._
import psi.api.toplevel.packaging._
import psi.api.toplevel.templates._

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi._;
import com.intellij.navigation._;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.util.IncorrectOperationException;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import _root_.java.util.Collection;
import _root_.java.util.Collections;
import _root_.java.util.List;
import com.intellij.psi._

import _root_.scala.collection.mutable._


/** 
* @autor Alexander Podkhalyuzin
*/

trait ScTypeDefinition extends ScalaPsiElement
  with NavigationItem with PsiClass with ScTypeDefinitionOwner with ScTypeDefinitionBase with ScTopStatement with ScNamedElement with ScTypeParametersOwner {

  def getNameIdentifierScala(): PsiElement

  def getFieldsAndMethods(): Array[ScMember] = {
    val res = new ArrayBuffer[ScMember]
    for (child <- getChildren) if (child.isInstanceOf[ScMember])   res += child.asInstanceOf[ScMember]
    for (child <- getExtendsBlock.getTemplateBody.getChildren
      if child.isInstanceOf[ScMember]) res+=child.asInstanceOf[ScMember]
    return res.toArray
  }

  def getExtendsBlock: ScExtendsBlock = getNode.findChildByType(ScalaElementTypes.EXTENDS_BLOCK).getPsi.asInstanceOf[ScExtendsBlock]

  def getSuperClassNames() = Array[String]()

  def getContainingClass: PsiClass = getParent match {
    case clazz: PsiClass => clazz
    case _ => null
  }

  def getPath: String = {
    var qualName = getQualifiedName;
    val index = qualName.lastIndexOf('.');
    if (index < 0 || index >= (qualName.length() - 1))
      ""
    else
      qualName.substring(0, index);
  }

  override def getName = nameNode match {
    case null => null
    case e => e.getText()
  }

  def typeParametersClause() : ScTypeParamClause

  def typeParameters() = typeParametersClause.typeParameters
}