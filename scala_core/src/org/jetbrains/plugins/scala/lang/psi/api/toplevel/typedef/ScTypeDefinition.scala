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
import psi.api.statements.params._

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
import com.intellij.openapi.util.Iconable

import _root_.scala.collection.mutable._


/** 
* @autor Alexander Podkhalyuzin
*/

trait ScTypeDefinition extends ScNamedElement
  with NavigationItem with PsiClass with ScTypeDefinitionOwner with ScTypeDefinitionBase with ScTopStatement with ScTypeParametersOwner with Iconable {

  def getFieldsAndMethods(): Seq[ScMember]

  /**
   * Return sequence of inner type definitions
   * @return inner classes object and traits
   */
  def getInnerTypeDefinitions(): Seq[ScTypeDefinition]

  def methods = for (m <- getFieldsAndMethods if m.isInstanceOf[PsiMethod]) yield m.asInstanceOf[PsiMethod]

  def extendsBlock: ScExtendsBlock

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

  def getTypeDefinitions(): Seq[ScTypeDefinition] =
    extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.getChildren.flatMap(collectTypeDefs(_))
    }

  /*
   * Return does type definition have extends keyword.
   * @return has extends keyword
   */
  def hasExtendsKeyword: Boolean
}