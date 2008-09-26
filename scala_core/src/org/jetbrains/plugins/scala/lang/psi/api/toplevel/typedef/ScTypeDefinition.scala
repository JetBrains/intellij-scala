package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import annotations.Nullable
import com.intellij.openapi.editor.Editor
import types.{ScType, PhysicalSignature, ScSubstitutor}
import base.types.ScSelfTypeElement
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
import base._

/**
 * @author AlexanderPodkhalyuzin
 */

trait ScTypeDefinition extends ScNamedElement
with NavigationItem with PsiClass with ScTypeParametersOwner with Iconable {

  def members(): Seq[ScMember]

  def functions(): Seq[ScFunction]

  def aliases(): Seq[ScTypeAlias]

  def innerTypeDefinitions(): Seq[ScTypeDefinition]

  def extendsBlock(): ScExtendsBlock

  def superTypes(): Seq[ScType]

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

  def functionsByName(name: String): Iterable[PsiMethod]

  def selfTypeElement = findChild(classOf[ScSelfTypeElement])

  def selfType = selfTypeElement match {
    case Some(ste) => ste.typeElement match {
      case Some(te) => Some(te.getType)
      case None => None
    }
    case None => None
  }

  def allTypes(): Iterator[Pair[PsiNamedElement, ScSubstitutor]]
  def allVals(): Iterator[Pair[PsiNamedElement, ScSubstitutor]]
  def allMethods(): Iterator[PhysicalSignature]

  /**
   * Add only real members (not abstract PsiElement) to this class in current caret position. 
   * If editor is null, add in template body's start.
   * @param meth member which added to this type definition
   * @param editor current editor
   */
  def addMember(meth: PsiElement, @Nullable editor: Editor)
}