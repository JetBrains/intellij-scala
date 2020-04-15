package org.jetbrains.plugins.scala
package lang
package completion
package global

import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private[completion] final class CompanionObjectMembersFinder(reference: ScReferenceExpression)
                                                            (namePredicate: String => Boolean)
  extends GlobalMembersFinder {

  override protected def candidates: Iterable[GlobalMemberResult] = for {
    context <- reference.withContexts.toIterable
    if context.isInstanceOf[ScClass] || context.isInstanceOf[ScTrait]

    objectToImport <- context.asInstanceOf[ScTypeDefinition].baseCompanionModule.iterator

    member <- objectToImport.functions ++ objectToImport.members.flatMap {
      case value: ScValueOrVariable => value.declaredElements
      case _ => Seq.empty
    }

    if namePredicate(member.name)
  } yield CompanionObjectMemberResult(member, objectToImport.asInstanceOf[ScObject])

  private final case class CompanionObjectMemberResult(member: ScTypedDefinition,
                                                       objectToImport: ScObject)
    extends GlobalMemberResult(
      new ScalaResolveResult(member),
      member,
      objectToImport,
      Some(objectToImport)
    ) {}
}
