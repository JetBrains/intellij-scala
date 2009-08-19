package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import statements._
import types.{ScType, ScSubstitutor}
import com.intellij.psi._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.util.Iconable

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