package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.util.Iconable

import parser._
import psi.ScalaPsiElement
import lexer._
import packaging._
import templates._
import statements.ScFunction
import statements.params._
import types.ScType

/** 
* @autor Alexander Podkhalyuzin
*/

trait ScTypeDefinition extends ScNamedElement
  with NavigationItem with PsiClass with ScTypeDefinitionOwner with ScTopStatement with ScTypeParametersOwner with Iconable {

  def members(): Seq[ScMember]

  def functions(): Seq[ScFunction]

  def typeDefinitions(): Seq[ScTypeDefinition]

  def extendsBlock(): ScExtendsBlock

  def superTypes(): Seq[ScType]

  def getSuperClassNames() = Array[String]() //for build restore  

  def getContainingClass: PsiClass = getParent match {
    case clazz: PsiClass => clazz
    case _ => null
  }

  def getPath: String = {
    var qualName = getQualifiedName;
    val index = qualName.lastIndexOf('.');
    if (index < 0) "" else qualName.substring(0, index);
  }

  override def getTypeDefinitions(): Seq[ScTypeDefinition] =
    extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.typeDefinitions
    }
}