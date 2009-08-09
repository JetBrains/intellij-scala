package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.openapi.editor.Editor
import com.intellij.psi.impl.ElementBase
import com.intellij.util.VisibilityIcons
import javax.swing.Icon
import statements._
import types.{ScType, PhysicalSignature, ScSubstitutor}
import base.types.ScSelfTypeElement
import com.intellij.psi._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.util.Iconable

import parser._
import psi.ScalaPsiElement
import lexer._
import packaging._
import templates._
import statements.params._
import base._

/**
 * @author AlexanderPodkhalyuzin
 */

trait ScTypeDefinition extends ScTemplateDefinition with ScMember
    with NavigationItem with PsiClass with ScTypeParametersOwner with Iconable with ScDocCommentOwner with ScAnnotationsHolder {

  def isCase : Boolean = false

  def getPath: String = {
    var qualName = getQualifiedName;
    val index = qualName.lastIndexOf('.');
    if (index < 0) "" else qualName.substring(0, index);
  }

  def getQualifiedNameForDebugger: String

  def functionsByName(name: String): Iterable[PsiMethod]

  def isPackageObject = false
}