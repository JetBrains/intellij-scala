package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import scala.Some
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class GetGetOrElseInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new GetGetOrElse(this))
}

class GetGetOrElse(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {

  def hint = InspectionBundle.message("get.getOrElse.hint")

  override def getSimplification(last: MethodRepr, second: MethodRepr) = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef))
        if lastRef.refName == "getOrElse" &&
                secondRef.refName == "get" &&
                checkResolve(lastRef, likeOptionClasses) &&
                checkResolve(secondRef, likeCollectionClasses) &&
                checkResolveToMap(secondRef) =>

        createSimplification(second, last.itself, "getOrElse", second.args ++ last.args)
      case _ => Nil
    }
  }

  private def checkResolveToMap(memberRef: ScReferenceElement) = memberRef.resolve() match {
    case m: ScMember => Option(m.containingClass).exists(_.name.toLowerCase.contains("map"))
    case _ => false
  }

}
