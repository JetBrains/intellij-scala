package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.findInheritorObjectsForOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitConversionIndex
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.PredefFqn

final case class GlobalImplicitConversion(owner: ScObject, function: ScFunction) {
  def substitutor: ScSubstitutor =
    MixinNodes.asSeenFromSubstitutor(owner, function.containingClass)

  def qualifiedName: String = owner.qualifiedName + "." + function.name
}

object GlobalImplicitConversion {

  private[implicits] type ImplicitConversionMap = Map[GlobalImplicitConversion, ImplicitConversionData]

  private[implicits] def computeImplicitConversionMap(scope: GlobalSearchScope)
                                                     (implicit project: Project): ImplicitConversionMap =
    (for {
      globalConversion <- collectConversionsIn(scope)
      data <- ImplicitConversionData(globalConversion)
    } yield globalConversion -> data)
      .toMap

  private[this] def collectConversionsIn(scope: GlobalSearchScope)
                                        (implicit project: Project) =
    for {
      function         <- ImplicitConversionIndex.allConversions(scope)
      containingObject <- findInheritorObjectsForOwner(function)
      if containingObject.qualifiedName != PredefFqn
    } yield GlobalImplicitConversion(containingObject, function)

}