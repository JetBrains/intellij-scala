package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.{ScalaBundle, isUnitTestMode}
import org.jetbrains.plugins.scala.codeInsight.intention.types.ConvertImplicitBoundsToImplicitParameter._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScImplicitBoundsOwner, ScTypeBoundsOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createImplicitClauseFromTextWithContext
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.util.InplaceRenameHelper

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class ConvertImplicitBoundsToImplicitParameter extends PsiElementBaseIntentionAction {
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
    element.parentOfType(classOf[ScImplicitBoundsOwner], strict = false)
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
      case function: ScFunction => (function, false)
      case ScConstructorOwner.constructor(constr) => (constr, true)
      case _ => return Seq.empty
    }

    val existingClause = parameterOwner.allClauses.lastOption.filter(_.isImplicitOrUsing)
    val existingParams = existingClause.iterator.flatMap(_.parameters).toSeq

    val candidates = for {
      tp <- parameterOwner.typeParameters
      cb <- tp.contextBoundTypeElement
      cbText = cb.getText
      cbName = cbText.lowercased
      typeText = cbText.parenthesize(!ScalaNamesValidator.isIdentifier(cbText))
    } yield (cbName.escapeNonIdentifiers, (cbName + tp.name.capitalize).escapeNonIdentifiers, s"$typeText[${tp.name}]")

    val isUniqueName = candidates.groupBy(_._1).filter(_._2.sizeIs == 1).keySet

    val nextNumber = mutable.Map.empty[String, Int]
    val newParamsTexts = for {
      (primaryName, altName, typeText) <- candidates
      name = if (isUniqueName(primaryName)) primaryName else altName
      suffix = nextNumber.updateWith(name)(old => Some(old.getOrElse(-1) + 1)).filter(_ >= 1).fold("")(_.toString)
    } yield s"$name$suffix: $typeText"

    // remove old clause
    existingClause.foreach(_.delete())

    // add clause
    val clause = createImplicitClauseFromTextWithContext(existingParams.map(_.getText) ++ newParamsTexts, parameterOwner, isClass)
    CodeEditUtil.setNodeGeneratedRecursively(clause.getNode, true)
    function.parameterList.addClause(clause)

    // remove bounds
    parameterOwner.typeParameters.foreach(_.removeImplicitBounds())

    UndoUtil.markPsiFileForUndo(function.getContainingFile)

    clause.parameters.takeRight(newParamsTexts.size)
  }

  def runRenamingTemplate(params: Seq[ScParameter]): Unit = {
    if (params.isEmpty) return

    val parent = PsiTreeUtil.findCommonParent(params.asJava)
    val helper = new InplaceRenameHelper(parent)
    params.foreach(p => helper.addGroup(p, Seq.empty, Seq.empty))
    helper.startRenaming()
  }
}
