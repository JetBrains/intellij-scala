package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import annotations.Nullable
import com.intellij.openapi.editor.Editor
import com.intellij.psi.impl.ElementBase
import com.intellij.util.VisibilityIcons
import javax.swing.Icon
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

trait ScTypeDefinition extends ScTemplateDefinition with ScMember
    with NavigationItem with PsiClass with ScTypeParametersOwner with Iconable {

  def getPath: String = {
    var qualName = getQualifiedName;
    val index = qualName.lastIndexOf('.');
    if (index < 0) "" else qualName.substring(0, index);
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

  def addMember(member: ScMember, anchor: Option[PsiElement]): ScMember
}