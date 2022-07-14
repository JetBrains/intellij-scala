package org.jetbrains.plugins.scala
package lang.types.existentialSimplification

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.types.utils.ScPsiElementAssertionTestBase

/**
 * @author Alexander Podkhalyuzin
 */
abstract class ExistentialSimplificationTestBase extends ScPsiElementAssertionTestBase[ScExpression] {
  override def folderPath: String = baseRootPath + "types/existentialSimplification/"

  override def computeRepresentation(expr: ScExpression): Either[String, String] = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(expr)
    expr.`type`() match {
      case Right(ttypez: ScExistentialType) => scala.Right(ttypez.simplify().presentableText)
      case Right(tp)                        => scala.Right(tp.presentableText)
      case Failure(msg)                     => scala.Left(msg)
    }
  }
}