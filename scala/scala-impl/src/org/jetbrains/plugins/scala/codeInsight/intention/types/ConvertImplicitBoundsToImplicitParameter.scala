package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.intention.types.ConvertImplicitBoundsToImplicitParameter._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeBoundsOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createImplicitClauseFromTextWithContext
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.util.InplaceRenameHelper
import org.jetbrains.plugins.scala.{ScalaBundle, isUnitTestMode}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

//@TODO: review and adapt to 3.6+ new context bounds
class ConvertImplicitBoundsToImplicitParameter extends PsiElementBaseIntentionAction with DumbAware {
  override def getFamilyName: String = ScalaBundle.message("family.name.convert.implicit.bounds")

  override def getText: String = ScalaBundle.message("convert.view.and.context.bounds.to.implicit.parameters")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    canBeConverted(element)
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val addedParams = doConversion(element)
    if (!isUnitTestMode && !IntentionPreviewUtils.isIntentionPreviewActive)
      runRenamingTemplate(addedParams)
  }
}

object ConvertImplicitBoundsToImplicitParameter {

  def canBeConverted(element: PsiElement): Boolean =
    element.parentOfType(classOf[ScTypeBoundsOwner], strict = false)
      .filter(_.hasImplicitBounds)
      .flatMap(_.parentOfType(classOf[ScTypeParametersOwner]))
      .exists {
        case _: ScTrait => false
        case _ => true
      }

  def doConversion(element: PsiElement): Seq[ScParameter] = {
    val parameterOwner = Option(element)
      .filter(_.isValid)
      .flatMap(_.parentOfType(classOf[ScParameterOwner], strict = false))
      .filterByType[ScTypeParametersOwner]
      .getOrElse(return Seq.empty)

    val (function, isClass) = parameterOwner match {
      case function: ScFunction                   => (function, false)
      case ScConstructorOwner.constructor(constr) => (constr, true)
      case _                                      => return Seq.empty
    }

    val clauses        = parameterOwner.allClauses
    val typeParameters = parameterOwner.typeParameters

    val usageIndex = ScParameterOwner.contextBoundUsageInParameterListIndex(clauses, typeParameters)

    //TODO: new expansion rules in SIP-64
    val existingClause = clauses.lastOption.filter(_.isImplicitOrUsing)
    val existingParams = existingClause.iterator.flatMap(_.parameters).toSeq

    val candidates = for {
      tp       <- typeParameters
      cb       <- tp.contextBounds
      cbTe     = cb.typeElement
      teText   = cbTe.getText
      cbName   = cb.name.getOrElse(teText.lowercased)
      typeText = teText.parenthesize(!ScalaNamesValidator.isIdentifier(teText))
    } yield (
      cbName.escapeNonIdentifiers,
      (cbName + tp.name.capitalize).escapeNonIdentifiers,
      s"$typeText[${tp.name}]"
    )

    val isUniqueName = candidates.groupBy(_._1).filter(_._2.sizeIs == 1).keySet
    val nextNumber   = mutable.Map.empty[String, Int]

    val newParamsTexts = for {
      (primaryName, altName, typeText) <- candidates
      name = if (isUniqueName(primaryName)) primaryName else altName
      suffix = nextNumber.updateWith(name)(old => Some(old.getOrElse(-1) + 1)).filter(_ >= 1).fold("")(_.toString)
    } yield s"$name$suffix: $typeText"

    val newClause =
      if (usageIndex == -1) {
        // context bounds are not referenced in parameters => add to the last clause
        // remove old clause
        existingClause.foreach(_.delete())

        // add clause
        val clause = createImplicitClauseFromTextWithContext(
          existingParams.map(_.getText) ++ newParamsTexts,
          parameterOwner,
          isClass
        )

        CodeEditUtil.setNodeGeneratedRecursively(clause.getNode, true)
        function.parameterList.addClause(clause)
        clause
      } else {
        // usage in parameters found => insert before the first usage
        val clause = createImplicitClauseFromTextWithContext(
          newParamsTexts,
          parameterOwner,
          isClass
        )

        CodeEditUtil.setNodeGeneratedRecursively(clause.getNode, true)
        val clauseAtIdx = clauses(usageIndex)
        function.parameterList.addBefore(clause, clauseAtIdx)
        clause
      }

    // remove bounds
    parameterOwner.typeParameters.foreach(_.removeImplicitBounds())

    UndoUtil.markPsiFileForUndo(function.getContainingFile)

    newClause.parameters.takeRight(newParamsTexts.size)
  }

  def runRenamingTemplate(params: Seq[ScParameter]): Unit = {
    if (params.isEmpty) return

    val parent = PsiTreeUtil.findCommonParent(params.asJava)
    if (parent == null) return

    val helper = new InplaceRenameHelper(parent)
    params.foreach(p => helper.addGroup(p, Seq.empty, Seq.empty))
    helper.startRenaming()
  }
}
