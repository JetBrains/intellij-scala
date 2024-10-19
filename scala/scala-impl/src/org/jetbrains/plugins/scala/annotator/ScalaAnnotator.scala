package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation._
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.{DumbAware, DumbService, Project}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._
import org.jetbrains.plugins.scala.annotator.annotationHolder.ScalaAnnotationHolderAdapter
import org.jetbrains.plugins.scala.annotator.element.ElementAnnotator
import org.jetbrains.plugins.scala.annotator.modifiers.ModifierChecker
import org.jetbrains.plugins.scala.annotator.template._
import org.jetbrains.plugins.scala.extensions._
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
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.ProcessSubtypes
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.statistics.ScalaAnnotatorUsagesCollector
import org.jetbrains.plugins.scala.{ScalaBundle, Tracing}

class ScalaAnnotator extends Annotator
  with FunctionAnnotator
  with OverridingAnnotator
  with DumbAware {

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit = {
    val file = element.getContainingFile

    if (InjectedLanguageManager.getInstance(element.getProject).isInjectedFragment(file))
      return

    val typeAware =
      if ((file ne null) && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(file)) false
      else HighlightingAdvisor.isTypeAwareHighlightingEnabled(element)

    annotate(element, typeAware)(new ScalaAnnotationHolderAdapter(holder))

    Tracing.annotator(element)
  }

  // Added for preserving binary compatibility.
  def annotate(element: PsiElement)(implicit holder: ScalaAnnotationHolder): Unit = {
    annotate(element, HighlightingAdvisor.isTypeAwareHighlightingEnabled(element))
  }

  def annotate(element: PsiElement, typeAware: Boolean, checkShouldInspect: Boolean = true)(implicit holder: ScalaAnnotationHolder): Unit = {
    val file = element.getContainingFile
    if (checkShouldInspect && !HighlightingAdvisor.shouldInspect(file))
      return

    val (compiled, isInSources) = file match {
      case file: ScalaFile =>
        val isInSources = file.getVirtualFile.nullSafe.exists {
          ProjectRootManager.getInstance(file.getProject).getFileIndex.isInSourceContent
        }
        (file.isCompiled, isInSources)
      case _ => (false, false)
    }

    if (isInSources && (element eq file)) {
      if (typeAware) {
        ScalaAnnotatorUsagesCollector.logTypeAware(file.getProject)
      } else {
        ScalaAnnotatorUsagesCollector.logNoneTypeAware(file.getProject)
      }
    }

    element match {
      case e: ScalaPsiElement => ElementAnnotator.annotate(e, typeAware)
      case _ =>
    }

    val visitor = new ScalaElementVisitor {
      override def visitExpression(expr: ScExpression): Unit = {
        if (!compiled) {
          ImplicitParametersAnnotator.annotate(expr, typeAware)
          ByNameParameter.annotate(expr, typeAware)
        }

        if (typeAware) {
          expr.getTypeAfterImplicitConversion() match {
            case ExpressionTypeResult(Right(_), _, Some(_)) =>
              highlightImplicitView(expr)
            case _ =>
          }
        }

        super.visitExpression(expr)
      }

      override def visitMacroDefinition(fun: ScMacroDefinition): Unit = {
        if (isInSources) {
          ScalaAnnotatorUsagesCollector.logMacroDefinition(fun.getProject)
        }
        super.visitMacroDefinition(fun)
      }

      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        visitExpression(ref)
      }

      override def visitGenericCallExpression(call: ScGenericCall): Unit = {
        //todo: if (typeAware) checkGenericCallExpression(call, holder)
        super.visitGenericCallExpression(call)
      }

      override def visitFunctionDefinition(fun: ScFunctionDefinition): Unit = {
        if (!compiled && !fun.isConstructor)
          annotateFunction(fun, typeAware)
        super.visitFunctionDefinition(fun)
      }

      override def visitFunctionDeclaration(fun: ScFunctionDeclaration): Unit = {
        checkAbstractMemberPrivateModifier(fun, Seq(fun.nameId))
        super.visitFunctionDeclaration(fun)
      }

      override def visitFunction(function: ScFunction): Unit = {
        if (typeAware && !compiled) checkOverrideMethods(function, isInSources)

        if (!function.isConstructor) checkFunctionForVariance(function)
        super.visitFunction(function)
      }

      override def visitTypeProjection(proj: ScTypeProjection): Unit = {
        visitTypeElement(proj)
      }

      override def visitModifierList(modifierList: ScModifierList): Unit = {
        ModifierChecker.checkModifiers(modifierList)
        super.visitModifierList(modifierList)
      }

      override def visitExistentialTypeElement(exist: ScExistentialTypeElement): Unit = {
        if (isInSources) {
          ScalaAnnotatorUsagesCollector.logExistentialType(exist.getProject)
        }
        super.visitExistentialTypeElement(exist)
      }

      override def visitTypeAlias(alias: ScTypeAlias): Unit = {
        if (typeAware && !compiled) checkOverrideTypeAliases(alias)

        if (!compoundType(alias)) checkBoundsVariance(alias, alias.nameId, alias, checkTypeDeclaredSameBracket = false)
        super.visitTypeAlias(alias)
      }

      override def visitVariable(variable: ScVariable): Unit = {
        if (typeAware && !compiled) checkOverrideVariables(variable, isInSources)

        variable.typeElement match {
          case Some(typ) => checkBoundsVariance(variable, typ, variable, checkTypeDeclaredSameBracket = false)
          case _ =>
        }
        if (!childHasAnnotation(variable.typeElement, "uncheckedVariance")) {
          checkValueAndVariableVariance(variable, Covariant, variable.declaredElements)
          checkValueAndVariableVariance(variable, Contravariant, variable.declaredElements)
        }
        super.visitVariable(variable)
      }

      override def visitValueDeclaration(v: ScValueDeclaration): Unit = {
        checkAbstractMemberPrivateModifier(v, v.declaredElements.map(_.nameId))
        super.visitValueDeclaration(v)
      }

      override def visitValue(value: ScValue): Unit = {
        if (typeAware && !compiled) checkOverrideValues(value, isInSources)

        value.typeElement match {
          case Some(typ) => checkBoundsVariance(value, typ, value, checkTypeDeclaredSameBracket = false)
          case _ =>
        }
        if (!childHasAnnotation(value.typeElement, "uncheckedVariance")) {
          checkValueAndVariableVariance(value, Covariant, value.declaredElements)
        }
        super.visitValue(value)
      }

      override def visitClassParameter(parameter: ScClassParameter): Unit = {
        if (typeAware && !compiled) checkOverrideClassParameters(parameter)

        checkClassParameterVariance(parameter)
        super.visitClassParameter(parameter)
      }

      override def visitTemplateParents(tp: ScTemplateParents): Unit = {
        checkTemplateParentsVariance(tp)
        super.visitTemplateParents(tp)
      }
    }

    implicit val project: Project = element.getProject
    if (!isDumbMode) {
      element.accept(visitor)
    }

    element match {
      case templateDefinition: ScTemplateDefinition if !isDumbMode =>
        checkBoundsVariance(templateDefinition, templateDefinition.nameId, templateDefinition.nameId, Covariant)

        templateDefinition match {
          case cls: ScClass => CaseClassWithoutParamList.annotate(cls, typeAware)
          case trt: ScTrait => TraitHasImplicitBound.annotate(trt, typeAware)
          case _ =>
        }
      case _ =>
    }

    //todo: super[ControlFlowInspections].annotate(element, holder)
  }

  def checkBoundsVariance(toCheck: PsiElement, toHighlight: PsiElement, checkParentOf: PsiElement,
                          upperV: Variance = Covariant, checkTypeDeclaredSameBracket: Boolean = true)
                         (implicit holder: ScalaAnnotationHolder): Unit = {
    toCheck match {
      case boundOwner: ScTypeBoundsOwner =>
        checkAndHighlightBounds(boundOwner.upperTypeElement, upperV)
        checkAndHighlightBounds(boundOwner.lowerTypeElement, -upperV)
      case _ =>
    }
    toCheck match {
      case paramOwner: ScTypeParametersOwner =>
        for (param <- paramOwner.typeParameters) {
          checkBoundsVariance(param, param.nameId, checkParentOf, -upperV)
        }
      case _ =>
    }

    def checkAndHighlightBounds(boundOption: Option[ScTypeElement], expectedVariance: Variance): Unit = {
      boundOption match {
        case Some(bound) if !childHasAnnotation(Some(bound), "uncheckedVariance") =>
          checkVariance(bound.calcType, expectedVariance, toHighlight, checkParentOf, checkTypeDeclaredSameBracket)
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

  private def checkFunctionForVariance(fun: ScFunction)
                                      (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!modifierIsThis(fun) && !compoundType(fun)) { //if modifier contains [this] or if it is a compound type we do not highlight it
      checkBoundsVariance(fun, fun.nameId, fun.getParent)
      if (!childHasAnnotation(fun.returnTypeElement, "uncheckedVariance")) {
        fun.returnType match {
          case Right(returnType) =>
            checkVariance(ScalaType.expandAliases(returnType).getOrElse(returnType), Covariant, fun.nameId,
              fun.getParent)
          case _ =>
        }
      }
      for (parameter <- fun.parameters) {
        parameter.typeElement match {
          case Some(te) if !childHasAnnotation(Some(te), "uncheckedVariance") =>
            checkVariance(ScalaType.expandAliases(te.calcType).getOrElse(te.calcType), Contravariant,
              parameter.nameId, fun.getParent)
          case _ =>
        }
      }
    }
  }

  private def checkTypeVariance(typeable: Typeable, variance: Variance, toHighlight: PsiElement, checkParentOf: PsiElement)
                               (implicit holder: ScalaAnnotationHolder): Unit = {
    typeable.`type`() match {
      case Right(tp) =>
        ScalaType.expandAliases(tp) match {
          case Right(newTp) => checkVariance(newTp, variance, toHighlight, checkParentOf)
          case _ => checkVariance(tp, variance, toHighlight, checkParentOf)
        }
      case _ =>
    }
  }

  def checkClassParameterVariance(toCheck: ScClassParameter)
                                 (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!modifierIsThis(toCheck) && !childHasAnnotation(Some(toCheck), "uncheckedVariance")) {
      if (toCheck.isVar) {
        checkTypeVariance(toCheck, Contravariant, toCheck.nameId, toCheck)
        checkTypeVariance(toCheck, Covariant, toCheck.nameId, toCheck)
      }
      else if (toCheck.isValEffectively) {
        checkTypeVariance(toCheck, Covariant, toCheck.nameId, toCheck)
      }
    }
  }

  def checkTemplateParentsVariance(parents: ScTemplateParents)
                                  (implicit holder: ScalaAnnotationHolder): Unit = {
    for (typeElement <- parents.typeElements) {
      if (!childHasAnnotation(Some(typeElement), "uncheckedVariance") && !parents.parent.flatMap(_.parent).exists(_.isInstanceOf[ScNewTemplateDefinition]))
        checkTypeVariance(typeElement, Covariant, typeElement, parents)
    }
  }

  def checkValueAndVariableVariance(toCheck: ScDeclaredElementsHolder, variance: Variance,
                                    declaredElements: Seq[Typeable with ScNamedElement])
                                   (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!modifierIsThis(toCheck)) {
      for (element <- declaredElements) {
        checkTypeVariance(element, variance, element.nameId, toCheck)
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
  private def checkVariance(typeParam: ScType,
                            variance: Variance,
                            toHighlight: PsiElement,
                            checkParentOf: PsiElement,
                            checkIfTypeIsInSameBrackets: Boolean = false)
                           (implicit holder: ScalaAnnotationHolder): ScType = {

    def highlightVarianceError(elementV: Variance, positionV: Variance, name: String): Unit = {
      if (positionV != elementV && elementV != Invariant) {
        val typePName = typeParam.toString
        val pos =
          if (toHighlight.isInstanceOf[ScVariable]) toHighlight.getText + "_="
          else toHighlight.getText
        val isMethod = toHighlight.isInstanceOf[ScFunction] // "method" else "value"
        val elementVariance = elementV.name
        val posVariance = positionV.name

        val message = (elementVariance, posVariance, isMethod) match {
          case ("covariant", "invariant", true)      => ScalaBundle.message("covariant.type.invariant.position.of.method", name, typePName, pos)
          case ("covariant", "invariant", false)     => ScalaBundle.message("covariant.type.invariant.position.of.value", name, typePName, pos)
          case ("covariant", "contravariant", true)  => ScalaBundle.message("covariant.type.contravariant.position.of.method", name, typePName, pos)
          case ("covariant", "contravariant", false) => ScalaBundle.message("covariant.type.contravariant.position.of.value", name, typePName, pos)
          case ("contravariant", "invariant", true)  => ScalaBundle.message("contravariant.type.invariant.position.of.method", name, typePName, pos)
          case ("contravariant", "invariant", false) => ScalaBundle.message("contravariant.type.invariant.position.of.value", name, typePName, pos)
          case ("contravariant", "covariant", true)  => ScalaBundle.message("contravariant.type.covariant.position.of.method", name, typePName, pos)
          case ("contravariant", "covariant", false) => ScalaBundle.message("contravariant.type.covariant.position.of.value", name, typePName, pos)
          case _ => ???
        }

        holder.createErrorAnnotation(toHighlight, message)
      }
    }

    def functionToSendIn(tp: ScType, v: Variance): AfterUpdate.ProcessSubtypes.type = {
      tp match {
        case paramType: TypeParameterType =>
          paramType.psiTypeParameter match {
            case _: LightElement => () // do not check variance for dummy type params in poly-types
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

  private def isDumbMode(implicit project: Project): Boolean = inReadAction {
    DumbService.isDumb(project)
  }
}
