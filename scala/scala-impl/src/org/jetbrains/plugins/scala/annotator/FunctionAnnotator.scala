package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.annotator.quickfix._
import org.jetbrains.plugins.scala.extensions.{&&, Parent}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScTypesExt
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result._

trait FunctionAnnotator {
  self: ScalaAnnotator =>

  import FunctionAnnotator._

  def annotateFunction(function: ScFunctionDefinition, typeAware: Boolean = true)
                      (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val projectContext = function.projectContext

    if (!function.hasExplicitType && function.definedReturnType.isLeft) {
      val message = ScalaBundle.message("function.recursive.need.result.type", function.name)
      function.recursiveReferences.foreach {
        holder.createErrorAnnotation(_, message)
      }
    }

    for {
      annotation <- findTailRecursionAnnotation(function)
    } {
      val removeAnnotationFix = new RemoveAnnotationQuickFix(annotation)

      val functionNameId = function.nameId

      if (!canBeTailRecursive(function)) {
        holder.createErrorAnnotation(
          functionNameId,
          ScalaBundle.message("method.annotated.with.tailrec.is.neither.private.nor.final"),
          Seq(
            new ModifierQuickFix.Add(function, functionNameId, ScalaModifier.Private),
            new ModifierQuickFix.Add(function, functionNameId, ScalaModifier.Final),
            removeAnnotationFix
          )
        )
      }

      if (typeAware) {
        function.recursiveReferencesGrouped match {
          case references if references.noRecursion =>
            holder.createErrorAnnotation(
              functionNameId,
              ScalaBundle.message("method.annotated.with.tailrec.contains.no.recursive.calls"),
              Seq(removeAnnotationFix)
            )
          case references =>
            for {
              reference <- references.ordinaryRecursive
              target = reference.getParent match {
                case methodCall: ScMethodCall => methodCall
                case _ => reference
              }
            } yield holder.createErrorAnnotation(
              target,
              ScalaBundle.message("recursive.call.not.in.tail.position"),
              Seq(removeAnnotationFix)
            )
        }
      }
    }

    val returnUsages = function.returnUsages

    for (usage <- returnUsages) {
      val explicitType   = function.hasExplicitType
      val hasAssign      = function.hasAssign
      val explicitReturn = usage.isInstanceOf[ScReturn]

      if (explicitReturn && hasAssign && !explicitType) needsTypeAnnotation()

      def needsTypeAnnotation(): Unit = {
        val message = ScalaBundle.message("function.must.define.type.explicitly", function.name)
        val returnTypes = returnUsages.collect {
          case retStmt: ScReturn  => retStmt.expr.flatMap(_.`type`().toOption).getOrElse(Any)
          case expr: ScExpression => expr.`type`().getOrAny
        }
        holder.createErrorAnnotation(
          usage.asInstanceOf[ScReturn].keyword,
          message,
          Seq(new AddReturnTypeFix(function, returnTypes.toSeq.lub()))
        )
      }
    }
  }
}

object FunctionAnnotator {

  val TailrecAnnotationFQN = "scala.annotation.tailrec"

  def canBeTailRecursive(function: ScFunctionDefinition): Boolean = function.getParent match {
    case (_: ScTemplateBody) && Parent(Parent(owner: ScTypeDefinition)) =>
      owner.isInstanceOf[ScObject] ||
        owner.getModifierList.isFinal || {
        function.getModifierList match {
          case list => list.isPrivate || list.isFinal
        }
      }
    case _ => true
  }

  def findTailRecursionAnnotation(function: ScFunctionDefinition): Option[ScAnnotation] =
    function.annotations.find {
      _.typeElement.`type`().exists(_.canonicalText == "_root_." + TailrecAnnotationFQN)
    }

  private final class RemoveAnnotationQuickFix(annotation: ScAnnotation) extends IntentionAction {
    override def getFamilyName: String = ScalaBundle.message("family.name.remove.tailrec.annotation")

    override def getText: String = getFamilyName

    override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
      annotation.isValid

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
      annotation.delete()

    override def startInWriteAction = true
  }
}

