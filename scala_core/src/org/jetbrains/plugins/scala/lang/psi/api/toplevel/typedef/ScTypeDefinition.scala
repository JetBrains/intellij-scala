package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import statements.ScVariable
import statements.ScValue
import com.intellij.psi._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.util.Iconable

import parser._
import psi.ScalaPsiElement
import lexer._
import packaging._
import templates._
import statements.{ScTypeAlias, ScFunction}
import statements.params._
import types.ScType
import base._

/** 
* @autor Alexander Podkhalyuzin
*/

trait ScTypeDefinition extends ScNamedElement
  with NavigationItem with PsiClass with ScTopStatement with ScTypeParametersOwner with Iconable {

  def members(): Seq[ScMember]

  def functions(): Seq[ScFunction]

  def aliases(): Seq[ScTypeAlias]

  def allAliases: Seq[ScTypeAlias]

  def innerTypeDefinitions(): Seq[ScTypeDefinition]

  def extendsBlock(): ScExtendsBlock

  def superTypes(): Seq[ScType]

  def allVals: Seq[ScValue]

  def allVars: Seq[ScVariable]

  def allMembers: Seq[PsiMember]

  def allFields: Seq[PsiField]

  def getSuperClassNames() = Array[String]() //for build restore

  def getPath: String = {
    var qualName = getQualifiedName;
    val index = qualName.lastIndexOf('.');
    if (index < 0) "" else qualName.substring(0, index);
  }

  def getTypeDefinitions(): Seq[ScTypeDefinition] =
    extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.typeDefinitions
    }

  def functionsByName(name : String) : Iterable[PsiMethod]
}