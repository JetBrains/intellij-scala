package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.{getContextOfType, isContextAncestor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScThisReferenceImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScThisReference {

  protected override def innerType: TypeResult = {
    import scala.meta.intellij.psi._
    refTemplate match {
      case Some(hasInlineAnnotation()) => createTypeFromText("scala.meta.Stat", this, null).asTypeResult
      case Some(td) => ScThisReferenceImpl.getThisTypeForTypeDefinition(td, this)
      case _ => Failure(ScalaBundle.message("cannot.infer.type"))
    }
  }

  override def refTemplate: Option[ScTemplateDefinition] = reference match {
    case Some(ref) => ref.resolve() match {
      case td: ScTypeDefinition if isContextAncestor(td, ref, false) => Some(td)
      case _ => None
    }
    case None =>
      getContextOfType(this, false, classOf[ScTemplateBody])
        .nullSafe
        .map(getContextOfType(_, false, classOf[ScTemplateDefinition]))
        .toOption
  }

  override def toString: String = "ThisReference"
}

object ScThisReferenceImpl {
  def getThisTypeForTypeDefinition(td: ScTemplateDefinition, expr: ScExpression): TypeResult = {
    import td.projectContext
    // SLS 6.5:  If the expression’s expected type is a stable type,
    // or C .this occurs as the prefix of a selection, its type is C.this.type,
    // otherwise it is the self type of class C .
    val result = expr.getContext match {
      case ref: ScStableCodeReference if ref.pathQualifier.contains(expr) => ScThisType(td)
      case referenceExpression: ScReferenceExpression if referenceExpression.qualifier.contains(expr) =>
        ScThisType(td)
      case _ => expr.expectedType() match {
        case Some(designatorOwner: DesignatorOwner) if designatorOwner.isStable =>
          ScThisType(td)
        case _ =>

          td.getTypeWithProjections(thisProjections = true)
            .map(scType => td.selfType.map(scType.glb(_))
              .getOrElse(scType)
            ) match {
              case Right(scType) => scType
              case _ => return Failure(ScalaBundle.message("no.clazz.type.found"))
            }
      }
    }
    Right(result)
  }
}