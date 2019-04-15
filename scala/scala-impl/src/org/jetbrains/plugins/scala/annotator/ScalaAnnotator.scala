package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInspection._
import com.intellij.lang.annotation._
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.impl.source.JavaDummyHolder
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._
import org.jetbrains.plugins.scala.annotator.annotationHolder.{DelegateAnnotationHolder, ErrorIndication}
import org.jetbrains.plugins.scala.annotator.modifiers.ModifierChecker
import org.jetbrains.plugins.scala.annotator.template._
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker._
import org.jetbrains.plugins.scala.components.HighlightingAdvisor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.annotator.ScReferenceAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.light.scala.DummyLightTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.ProcessSubtypes
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.collection.{Seq, mutable}

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */
abstract class ScalaAnnotator protected()(implicit val project: Project) extends Annotator
  with FunctionAnnotator with ScopeAnnotator
  with ConstructorInvocationAnnotator
  with OverridingAnnotator
  with ProjectContextOwner with DumbAware {

  override final implicit def projectContext: ProjectContext = project

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit = {

    val typeAware = isAdvancedHighlightingEnabled(element)
    val (compiled, isInSources) = element.getContainingFile match {
      case file: ScalaFile =>
        val isInSources = file.getVirtualFile.nullSafe.exists {
          ProjectRootManager.getInstance(file.getProject).getFileIndex.isInSourceContent
        }
        (file.isCompiled, isInSources)
      case _ => (false, false)
    }

    if (isInSources && (element eq element.getContainingFile)) {
      Stats.trigger {
        import FeatureKey._
        if (typeAware) annotatorTypeAware
        else annotatorNotTypeAware
      }
    }

    element match {
      case e: ScalaPsiElement => e.annotate(holder, typeAware)
      case _ =>
    }

    val visitor = new ScalaElementVisitor {
      override def visitExpression(expr: ScExpression) {
        if (!compiled) {
          ImplicitParametersAnnotator.annotate(expr, holder, typeAware)
          ByNameParameter.annotate(expr, holder, typeAware)
        }

        if (isAdvancedHighlightingEnabled(element)) {
          expr.getTypeAfterImplicitConversion() match {
            case ExpressionTypeResult(Right(t), _, Some(implicitFunction)) =>
              highlightImplicitView(expr, implicitFunction.element, t, expr, holder)
            case _ =>
          }
        }

        super.visitExpression(expr)
      }

      override def visitMacroDefinition(fun: ScMacroDefinition): Unit = {
        Stats.trigger(isInSources, FeatureKey.macroDefinition)
        super.visitMacroDefinition(fun)
      }

      override def visitReferenceExpression(ref: ScReferenceExpression) {
        visitExpression(ref)
      }

      override def visitGenericCallExpression(call: ScGenericCall) {
        //todo: if (typeAware) checkGenericCallExpression(call, holder)
        super.visitGenericCallExpression(call)
      }

      override def visitFor(expr: ScFor) {
        registerUsedImports(expr, ScalaPsiUtil.getExprImports(expr))
        super.visitFor(expr)
      }

      override def visitFunctionDefinition(fun: ScFunctionDefinition) {
        if (!compiled && !fun.isConstructor)
          annotateFunction(fun, holder, typeAware)
        super.visitFunctionDefinition(fun)
      }

      override def visitFunctionDeclaration(fun: ScFunctionDeclaration) {
        checkAbstractMemberPrivateModifier(fun, Seq(fun.nameId), holder)
        super.visitFunctionDeclaration(fun)
      }

      override def visitFunction(function: ScFunction) {
        if (typeAware && !compiled) checkOverrideMethods(function, isInSources)(holder)

        if (!function.isConstructor) checkFunctionForVariance(function, holder)
        super.visitFunction(function)
      }

      override def visitTypeProjection(proj: ScTypeProjection) {
        visitTypeElement(proj)
      }

      override def visitConstructorInvocation(constrInvocation: ScConstructorInvocation) {
        if (typeAware) {
          ImplicitParametersAnnotator.annotate(constrInvocation, holder, typeAware)
          annotateConstructorInvocation(constrInvocation, holder)
        }
        super.visitConstructorInvocation(constrInvocation)
      }

      override def visitModifierList(modifierList: ScModifierList) {
        ModifierChecker.checkModifiers(modifierList)(holder)
        super.visitModifierList(modifierList)
      }

      override def visitExistentialTypeElement(exist: ScExistentialTypeElement): Unit = {
        Stats.trigger(isInSources, FeatureKey.existentialType)
        super.visitExistentialTypeElement(exist)
      }

      override def visitTypeAlias(alias: ScTypeAlias) {
        if (typeAware && !compiled) checkOverrideTypeAliases(alias)(holder)

        if(!compoundType(alias)) checkBoundsVariance(alias, holder, alias.nameId, alias, checkTypeDeclaredSameBracket = false)
        super.visitTypeAlias(alias)
      }

      override def visitVariable(variable: ScVariable) {
        if (typeAware && !compiled) checkOverrideVariables(variable, isInSources)(holder)

        variable.typeElement match {
          case Some(typ) => checkBoundsVariance(variable, holder, typ, variable, checkTypeDeclaredSameBracket = false)
          case _ =>
        }
        if (!childHasAnnotation(variable.typeElement, "uncheckedVariance")) {
          checkValueAndVariableVariance(variable, Covariant, variable.declaredElements, holder)
          checkValueAndVariableVariance(variable, Contravariant, variable.declaredElements, holder)
        }
        super.visitVariable(variable)
      }

      override def visitValueDeclaration(v: ScValueDeclaration) {
        checkAbstractMemberPrivateModifier(v, v.declaredElements.map(_.nameId), holder)
        super.visitValueDeclaration(v)
      }

      override def visitValue(value: ScValue) {
        if (typeAware && !compiled) checkOverrideValues(value, isInSources)(holder)

        value.typeElement match {
          case Some(typ) => checkBoundsVariance(value, holder, typ, value, checkTypeDeclaredSameBracket = false)
          case _ =>
        }
        if (!childHasAnnotation(value.typeElement, "uncheckedVariance")) {
          checkValueAndVariableVariance(value, Covariant, value.declaredElements, holder)
        }
        super.visitValue(value)
      }

      override def visitClassParameter(parameter: ScClassParameter) {
        if (typeAware && !compiled) checkOverrideClassParameters(parameter)(holder)

        checkClassParameterVariance(parameter, holder)
        super.visitClassParameter(parameter)
      }

      override def visitTemplateParents(tp: ScTemplateParents): Unit = {
        checkTemplateParentsVariance(tp, holder)
        super.visitTemplateParents(tp)
      }
    }
    annotateScope(element, holder)
    element.accept(visitor)

    element match {
      case templateDefinition: ScTemplateDefinition =>
        checkBoundsVariance(templateDefinition, holder, templateDefinition.nameId, templateDefinition.nameId, Covariant)

        templateDefinition match {
          case cls: ScClass => CaseClassWithoutParamList.annotate(cls, holder, typeAware)
          case trt: ScTrait => TraitHasImplicitBound.annotate(trt, holder, typeAware)
          case _ =>
        }
      case _ =>
    }

    //todo: super[ControlFlowInspections].annotate(element, holder)
  }


  def isAdvancedHighlightingEnabled(element: PsiElement): Boolean =
    ScalaAnnotator.isAdvancedHighlightingEnabled(element)

  def checkBoundsVariance(toCheck: PsiElement, holder: AnnotationHolder, toHighlight: PsiElement, checkParentOf: PsiElement,
                          upperV: Variance = Covariant, checkTypeDeclaredSameBracket: Boolean = true, insideParameterized: Boolean = false) {
    toCheck match {
      case boundOwner: ScTypeBoundsOwner =>
        checkAndHighlightBounds(boundOwner.upperTypeElement, upperV)
        checkAndHighlightBounds(boundOwner.lowerTypeElement, -upperV)
      case _ =>
    }
    toCheck match {
      case paramOwner: ScTypeParametersOwner =>
        val inParameterized = if (paramOwner.isInstanceOf[ScTemplateDefinition]) false else true
        for (param <- paramOwner.typeParameters) {
          checkBoundsVariance(param, holder, param.nameId, checkParentOf, -upperV, insideParameterized = inParameterized)
        }
      case _ =>
    }

    def checkAndHighlightBounds(boundOption: Option[ScTypeElement], expectedVariance: Variance) {
      boundOption match {
        case Some(bound) if !childHasAnnotation(Some(bound), "uncheckedVariance") =>
          checkVariance(bound.calcType, expectedVariance, toHighlight, checkParentOf, holder, checkTypeDeclaredSameBracket, insideParameterized)
        case _ =>
      }
    }
  }

  def childHasAnnotation(teOption: Option[PsiElement], annotation: String): Boolean = teOption match {
    case Some(te) => te.breadthFirst().exists {
      case annot: ScAnnotationExpr =>
        annot.constructorInvocation.reference match {
          case Some(ref) => Option(ref.resolve()) match {
            case Some(res: PsiNamedElement) => res.getName == annotation
            case _ => false
          }
          case _ => false
        }
      case _ => false
    }
    case _ => false
  }

  private def checkFunctionForVariance(fun: ScFunction, holder: AnnotationHolder) {
    if (!modifierIsThis(fun) && !compoundType(fun)) { //if modifier contains [this] or if it is a compound type we do not highlight it
      checkBoundsVariance(fun, holder, fun.nameId, fun.getParent)
      if (!childHasAnnotation(fun.returnTypeElement, "uncheckedVariance")) {
        fun.returnType match {
          case Right(returnType) =>
            checkVariance(ScalaType.expandAliases(returnType).getOrElse(returnType), Covariant, fun.nameId,
              fun.getParent, holder)
          case _ =>
        }
      }
      for (parameter <- fun.parameters) {
        parameter.typeElement match {
          case Some(te) if !childHasAnnotation(Some(te), "uncheckedVariance") =>
            checkVariance(ScalaType.expandAliases(te.calcType).getOrElse(te.calcType), Contravariant,
              parameter.nameId, fun.getParent, holder)
          case _ =>
        }
      }
    }
  }

  private def checkTypeVariance(typeable: Typeable, variance: Variance, toHighlight: PsiElement, checkParentOf: PsiElement,
                                holder: AnnotationHolder): Unit = {
    typeable.`type`() match {
      case Right(tp) =>
        ScalaType.expandAliases(tp) match {
          case Right(newTp) => checkVariance(newTp, variance, toHighlight, checkParentOf, holder)
          case _ => checkVariance(tp, variance, toHighlight, checkParentOf, holder)
        }
      case _ =>
    }
  }

  def checkClassParameterVariance(toCheck: ScClassParameter, holder: AnnotationHolder): Unit = {
    if (toCheck.isVar && !modifierIsThis(toCheck) && !childHasAnnotation(Some(toCheck), "uncheckedVariance"))
      checkTypeVariance(toCheck, Contravariant, toCheck.nameId, toCheck, holder)
  }

  def checkTemplateParentsVariance(parents: ScTemplateParents, holder: AnnotationHolder): Unit = {
    for (typeElement <- parents.typeElements) {
      if (!childHasAnnotation(Some(typeElement), "uncheckedVariance") && !parents.parent.flatMap(_.parent).exists(_.isInstanceOf[ScNewTemplateDefinition]))
        checkTypeVariance(typeElement, Covariant, typeElement, parents, holder)
    }
  }

  def checkValueAndVariableVariance(toCheck: ScDeclaredElementsHolder, variance: Variance,
                                    declaredElements: Seq[Typeable with ScNamedElement], holder: AnnotationHolder) {
    if (!modifierIsThis(toCheck)) {
      for (element <- declaredElements) {
        checkTypeVariance(element, variance, element.nameId, toCheck, holder)
      }
    }
  }

  def modifierIsThis(toCheck: PsiElement): Boolean = {
    toCheck match {
      case modifierOwner: ScModifierListOwner =>
        Option(modifierOwner.getModifierList).flatMap(_.accessModifier).exists(_.isThis)
      case _ => false
    }
  }

  def compoundType(toCheck: PsiElement): Boolean = {
    toCheck.getParent.getParent match {
      case _: ScCompoundTypeElement => true
      case _ => false
    }
  }

  //fix for SCL-807
  private def checkVariance(typeParam: ScType, variance: Variance, toHighlight: PsiElement, checkParentOf: PsiElement,
                            holder: AnnotationHolder, checkIfTypeIsInSameBrackets: Boolean = false, insideParameterized: Boolean = false) = {

    def highlightVarianceError(elementV: Variance, positionV: Variance, name: String) = {
      if (positionV != elementV && elementV != Invariant) {
        val pos =
          if (toHighlight.isInstanceOf[ScVariable]) toHighlight.getText + "_="
          else toHighlight.getText
        val place = if (toHighlight.isInstanceOf[ScFunction]) "method" else "value"
        val elementVariance = elementV.name
        val posVariance = positionV.name
        val annotation = holder.createErrorAnnotation(toHighlight,
          ScalaBundle.message(s"$elementVariance.type.$posVariance.position.of.$place", name, typeParam.toString, pos))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
      }
    }

    def functionToSendIn(tp: ScType, v: Variance): AfterUpdate.ProcessSubtypes.type = {
      tp match {
        case paramType: TypeParameterType =>
          paramType.psiTypeParameter match {
            case _: DummyLightTypeParam => () // do not check variance for dummy type params in poly-types
            case scTypeParam: ScTypeParam =>
              val compareTo = scTypeParam.owner
              val parentIt = checkParentOf.parents
              //if it's a function inside function we do not highlight it unless trait or class is defined inside this function
              parentIt.find(e => e == compareTo || e.isInstanceOf[ScFunction]) match {
                case Some(_: ScFunction) =>
                case _ =>
                  def findVariance: Variance = {
                    if (!checkIfTypeIsInSameBrackets) return v
                    if (PsiTreeUtil.isAncestor(scTypeParam.getParent, toHighlight, false))
                    //we do not highlight element if it was declared inside parameterized type.
                      if (!scTypeParam.getParent.getParent.isInstanceOf[ScTemplateDefinition]) return scTypeParam.variance
                      else return -v
                    if (toHighlight.getParent == scTypeParam.getParent.getParent) return -v
                    v
                  }
                  highlightVarianceError(scTypeParam.variance, findVariance, paramType.name)
              }
            case _ =>
          }
        case _ =>
      }
      ProcessSubtypes
    }
    typeParam.recursiveVarianceUpdate(variance)(functionToSendIn)
  }
}

object ScalaAnnotator {

  def apply(implicit project: Project): ScalaAnnotator = new ScalaAnnotator() {}

  def forProject(implicit context: ProjectContext): ScalaAnnotator = apply(context.project)

  // TODO place the method in HighlightingAdvisor
  def isAdvancedHighlightingEnabled(element: PsiElement): Boolean = {
    element.getContainingFile.toOption
      .exists { f =>
        isAdvancedHighlightingEnabled(f) && !isInIgnoredRange(element, f)
      }
  }

  def isAdvancedHighlightingEnabled(file: PsiFile): Boolean = file match {
    case scalaFile: ScalaFile =>
      HighlightingAdvisor.getInstance(file.getProject).enabled && !isLibrarySource(scalaFile) && !scalaFile.isInDottyModule
    case _: JavaDummyHolder =>
      HighlightingAdvisor.getInstance(file.getProject).enabled
    case _ => false
  }

  private def isInIgnoredRange(element: PsiElement, file: PsiFile): Boolean = {
    @CachedInUserData(file, file.getManager.getModificationTracker)
    def ignoredRanges(): Set[TextRange] = {
      val chars = file.charSequence
      val indexes = mutable.ArrayBuffer.empty[Int]
      var lastIndex = 0
      while (chars.indexOf("/*_*/", lastIndex) >= 0) {
        lastIndex = chars.indexOf("/*_*/", lastIndex) + 5
        indexes += lastIndex
      }
      if (indexes.isEmpty) return Set.empty

      if (indexes.length % 2 != 0) indexes += chars.length

      var res = Set.empty[TextRange]
      for (i <- indexes.indices by 2) {
        res += new TextRange(indexes(i), indexes(i + 1))
      }
      res
    }

    val ignored = ignoredRanges()
    if (ignored.isEmpty || element.isInstanceOf[PsiFile]) false
    else {
      val noCommentWhitespace = element.children.find {
        case _: PsiComment | _: PsiWhiteSpace => false
        case _ => true
      }
      val offset =
        noCommentWhitespace
          .map(_.getTextOffset)
          .getOrElse(element.getTextOffset)
      ignored.exists(_.contains(offset))
    }
  }

  private def isLibrarySource(file: ScalaFile): Boolean = {
    val vFile = file.getVirtualFile
    val index = ProjectFileIndex.SERVICE.getInstance(file.getProject)

    !file.isCompiled && vFile != null && index.isInLibrarySource(vFile)
  }

}
