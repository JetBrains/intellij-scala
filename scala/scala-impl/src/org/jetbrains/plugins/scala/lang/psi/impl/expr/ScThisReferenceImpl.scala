package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil.{getContextOfType, isContextAncestor}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScThisReferenceImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScThisReference {

  protected override def innerType: TypeResult =
    refTemplate match {
      case Some(td) =>
        ScThisReferenceImpl.getThisTypeForTypeDefinition(td, this)
      case _ =>
        Failure(ScalaBundle.message("cannot.infer.type"))
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
  /** The type an argument of type `argType` stands for in a parameter's dependent `p.type`. A stable
   *  argument (a path: `this`, or stable identifiers selected from a path) is substituted by its singleton
   *  type, as in scalac: `apply(this)` for `def apply(tree: Tree): tree.type` is a `C.this.type`, not a
   *  `C`, and `updateAttachment(tree, a)` for a `val tree: T` is a `tree.type`, not a `T`. Not when the
   *  argument was converted to fit the parameter: it is then the conversion's result. */
  def dependentArgumentType(arg: ScExpression, argType: ScType): ScType =
    stablePathType(arg).filter(_.conforms(argType)(using Context(arg))).getOrElse(argType)

  /** The singleton type of `expr` when it is a stable path. */
  private def stablePathType(expr: ScExpression): Option[ScType] = expr match {
    case ths: ScThisReference => ths.refTemplate.map(ScThisType(_))
    case ref: ScReferenceExpression =>
      ref.bind().flatMap { srr =>
        srr.element match {
          case td: ScTypedDefinition if td.isStable =>
            ref.qualifier match {
              case None            => Some(srr.fromType.map(ScProjectionType(_, td)).getOrElse(ScDesignatorType(td)))
              case Some(qualifier) => stablePathType(qualifier).map(ScProjectionType(_, td))
            }
          case _ => None
        }
      }
    case _ => None
  }

  def getThisTypeForTypeDefinition(td: ScTemplateDefinition, expr: ScExpression): TypeResult = {
    import td.projectContext
    implicit val context: Context = Context(expr)

    // SLS 6.5:  If the expression’s expected type is a stable type,
    // or C.this occurs as the prefix of a selection, its type is C.this.type,
    // otherwise it is the self type of class C.
    val result = expr.getContext match {
      case ref: ScStableCodeReference if ref.pathQualifier.contains(expr) => ScThisType(td)
      case referenceExpression: ScReferenceExpression if referenceExpression.qualifier.contains(expr) =>
        ScThisType(td)
      // So is the receiver of an operator (`this setType null`, `this resetFlag F`).
      case sugarCall: ScSugarCallExpr if sugarCall.getBaseExpr == expr =>
        ScThisType(td)
      case _ => expr.expectedType().map(_.removeAliasDefinitions()) match {
        case Some(_: ScThisType) =>
          ScThisType(td)
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