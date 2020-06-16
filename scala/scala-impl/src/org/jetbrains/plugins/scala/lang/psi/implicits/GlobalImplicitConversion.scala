package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.getSignatures
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitConversionIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.findInheritorObjectsForOwner
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.PredefFqn

final case class GlobalImplicitConversion(containingObject: ScObject, function: ScFunction) {

  private def findSubstitutor: Option[ScSubstitutor] =
    getSignatures(containingObject)
      .forName(function.name)
      .findNode(function)
      .map(_.info.substitutor)
}

object GlobalImplicitConversion {

  private[implicits] type ImplicitConversionMap = collection.Map[GlobalImplicitConversion, ImplicitConversionData]

  private[implicits] def computeImplicitConversionMap(scope: GlobalSearchScope)
                                                     (implicit project: Project): ImplicitConversionMap =
    (for {
      globalConversion <- collectConversionsIn(scope)
      substitutor <- globalConversion.findSubstitutor
      data <- ImplicitConversionData(globalConversion.function, substitutor)
    } yield globalConversion -> data)
      .toMap

  private[this] def collectConversionsIn(scope: GlobalSearchScope)
                                        (implicit project: Project) = for {
    member <- ImplicitConversionIndex.allElements(scope)

    function <- member match {
      case f: ScFunction => f :: Nil
      case c: ScClass => c.getSyntheticImplicitMethod.toList
      case _ => Nil
    }

    containingObject <- findInheritorObjectsForOwner(function)
    if containingObject.qualifiedName != PredefFqn
  } yield GlobalImplicitConversion(containingObject, function)

}