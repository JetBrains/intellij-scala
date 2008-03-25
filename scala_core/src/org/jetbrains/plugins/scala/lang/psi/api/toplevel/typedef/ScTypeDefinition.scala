package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.colors._

import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
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


/** 
* @autor Alexander Podkhalyuzin
*/

trait ScTypeDefinition extends ScalaPsiElement
  with NavigationItem with PsiClass with ScTypeDefinitionOwner with ScTypeDefinitionBase {

  def getNameIdentifierScala(): PsiElement

  def getContainingClass: PsiClass = getParent match {
    case clazz: PsiClass => clazz.asInstanceOf[PsiClass]
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
        case _  => '(' + getPath + ')'
      }
      override def getIcon(open: Boolean) = ScTypeDefinition.this.getIcon(0)
    }
  }


  def getQualifiedName: String = {
    def append(s1: String, s2: String) = {if (s1 == "")  s2 else s1 + "." + s2}
    def iAmInner(e: PsiElement): String = {
      val parent = e.getParent
      parent match {
        case pack: ScPackaging => append(iAmInner(parent), pack.getFullPackageName)
        case tmplBody: ScTemplateBody => {
          append(iAmInner(tmplBody.getParent.getParent),
              tmplBody.getParent.getParent.asInstanceOf[ScTypeDefinition].getName)
        }
        case f: ScalaFile => {
          val packageStatement = f.getChild(ScalaElementTypes.PACKAGE_STMT).asInstanceOf[ScPackageStatement]
          if (packageStatement == null) "" else {
            val packageName = packageStatement.getFullPackageName
            if (packageName == null) "" else packageName
          }
        }
        case null => ""
        case x if x.getParent != null => iAmInner(x)
        case _ => ""
      }
    }
    append(iAmInner(this), getName)
  }

  override def getName = if (nameNode != null) nameNode.getText else ""

  def nameNode = {
    def isName = (elementType: IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)
    childSatisfyPredicateForElementType(isName)
  }

}