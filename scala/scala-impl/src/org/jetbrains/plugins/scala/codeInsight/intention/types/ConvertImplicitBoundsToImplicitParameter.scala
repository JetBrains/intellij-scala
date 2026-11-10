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
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScSignatureClause.{TermClause, TypeClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScInterleavedClausesOwner, ScParameterOwner, ScSignatureClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeBoundsOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createImplicitClauseFromTextWithContext
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.util.InplaceRenameHelper
import org.jetbrains.plugins.scala.{ScalaBundle, isUnitTestMode}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

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

    val typeParameters = parameterOwner.typeParameters

    val signatureClauses = parameterOwner match {
      case owner: ScInterleavedClausesOwner => owner.signatureClauses
      case _                                => parameterOwner.allClauses.map(TermClause.apply)
    }

    val extensionClauses = parameterOwner match {
      case fun: ScFunction => fun.extensionMethodOwner.fold(Seq.empty[ScSignatureClause])(_.signatureClauses)
      case _               => Seq.empty
    }

    // The same placement and keyword of a new clause as in the desugaring of context bounds (and in the compiler),
    // see ScParameterOwner.insertSyntheticSignatureClauses
    val isUsing = ScParameterOwner.isUsingEvidence(
      signatureClauses.lastOption.orElse(extensionClauses.lastOption), 
      parameterOwner
    )

    val usageAnchor    = contextBoundUsageAnchor(signatureClauses, typeParameters)
    val existingClause = signatureClauses.lastOption.collect { case TermClause(clause) if clause.isImplicit => clause }
    val existingParams = existingClause.iterator.flatMap(_.parameters).toSeq

    val candidates = for {
      tp       <- typeParameters
      cb       <- tp.contextBounds
      cbTe     = cb.typeElement
      teText   = cbTe.getText
      cbName   = cb.nameOpt.getOrElse(teText.lowercased)
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

    def createClause(paramsTexts: Seq[String], isUsing: Boolean): ScParameterClause = {
      val clause = createImplicitClauseFromTextWithContext(paramsTexts, parameterOwner, isClass, isUsing)
      CodeEditUtil.setNodeGeneratedRecursively(clause.getNode, true)
      clause
    }

    val addedParams = usageAnchor match {
      case Some(anchor) =>
        // insert before the first usage
        val clause = createClause(newParamsTexts, isUsing)
        function.parameterList.addBefore(clause, elementOf(anchor))
        clause.parameters
      case None =>
        // merge into the last clause if it is an implicit or using clause (evidence goes first),
        // keeping the keyword of that clause, otherwise add a new clause at the end
        val keepsUsing = existingClause.fold(isUsing)(!_.hasImplicitKeyword)
        existingClause.foreach(_.delete())
        val clause = createClause(newParamsTexts ++ existingParams.map(_.getText), keepsUsing)
        function.parameterList.addClause(clause)
        clause.parameters.take(newParamsTexts.size)
    }

    // remove bounds
    parameterOwner.typeParameters.foreach(_.removeImplicitBounds())

    UndoUtil.markPsiFileForUndo(function.getContainingFile)

    addedParams
  }

  private def elementOf(signatureClause: ScSignatureClause): PsiElement = signatureClause match {
    case TypeClause(clause) => clause
    case TermClause(clause) => clause
  }

  /**
   * The first clause that refers to a named context bound.
   *
   * Unlike the compiler, which only looks at term clauses, it also takes type clauses into account,
   * e.g. `[B <: p.In]` in `def foo[A : Parser as p](a: A)[B <: p.In](b: B)`.
   * The compiler rejects such code, but placing the evidence before such a clause is the only way to fix it.
   */
  private def contextBoundUsageAnchor(
    signatureClauses: Seq[ScSignatureClause],
    typeParameters:   Seq[ScTypeParam],
  ): Option[ScSignatureClause] = {
    val clausesTypeParameters = signatureClauses.flatMap {
      case TypeClause(clause) => clause.typeParameters
      case TermClause(_)      => Seq.empty
    }

    val pendingTypeParameters = mutable.ArrayBuffer.from(
      typeParameters.filterNot(clausesTypeParameters.contains)
    )

    signatureClauses.find {
      case TypeClause(clause) =>
        val isUsed = contextBoundIsUsedIn(clause, pendingTypeParameters.toSeq)
        pendingTypeParameters ++= clause.typeParameters
        isUsed

      case TermClause(clause) =>
        contextBoundIsUsedIn(clause, pendingTypeParameters.toSeq)
    }
  }

  private def contextBoundIsUsedIn(element: PsiElement, typeParameters: Seq[ScTypeParam]): Boolean = {
    val contextBoundsNames = boundNames(typeParameters)
    var boundUsageFound    = false

    val visitor =
      new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReference): Unit = {
          boundUsageFound ||= contextBoundsNames.exists {
            name =>
              ref.textMatches(name) || ref.qualifier.exists(_.textMatches(name))
          }
        }
      }

    if (contextBoundsNames.isEmpty)
      false
    else {
      element.accept(visitor)
      boundUsageFound
    }
  }

  private def boundNames(typeParameters: Seq[ScTypeParam]): Seq[String] =
    for {
      tparam    <- typeParameters
      boundName <- tparam.contextBounds.collect { case ScContextBound.Named(_, name) => name }
    } yield boundName

  def runRenamingTemplate(params: Seq[ScParameter]): Unit = {
    if (params.isEmpty) return

    val parent = PsiTreeUtil.findCommonParent(params.asJava)
    if (parent == null) return

    val helper = new InplaceRenameHelper(parent)
    params.foreach(p => helper.addGroup(p, Seq.empty, Seq.empty))
    helper.startRenaming()
  }
}
