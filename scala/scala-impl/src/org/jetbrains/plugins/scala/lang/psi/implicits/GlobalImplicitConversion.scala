package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitConversionIndex

case class GlobalImplicitConversion(containingObject: ScObject, function: ScFunction) {
  def toImplicitConversionData: Option[ImplicitConversionData] = {
    val node = TypeDefinitionMembers.getSignatures(containingObject).forName(function.name).findNode(function)
    val substitutor = node.map(_.info.substitutor)
    substitutor.flatMap {
      ImplicitConversionData(function, _)
    }
  }
}

object GlobalImplicitConversion {
  private def allImplicitConversions(elementScope: ElementScope)(): Iterable[ScMember] = {
    ImplicitConversionIndex.allElements(elementScope.scope)(elementScope.projectContext)
  }

  private[implicits] def collectIn(elementScope: ElementScope): Iterable[GlobalImplicitConversion] = {
    val manager = ScalaPsiManager.instance(elementScope.project)

    def containingObjects(function: ScFunction): Set[ScObject] =
      Option(function.containingClass)
        .map(cClass => manager.inheritorOrThisObjects(cClass))
        .getOrElse(Set.empty)

    allImplicitConversions(elementScope).flatMap { member =>
      val conversion = member match {
        case f: ScFunction => Some(f)
        case c: ScClass    => c.getSyntheticImplicitMethod
        case _             => None
      }

      for {
        function <- conversion.toSeq
        obj <- containingObjects(function)
        if obj.qualifiedName != "scala.Predef"
      } yield GlobalImplicitConversion(obj, function)
    }
  }

}