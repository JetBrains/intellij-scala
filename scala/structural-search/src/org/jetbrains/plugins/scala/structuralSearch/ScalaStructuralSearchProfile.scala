package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.codeInsight.template.{TemplateContextType, TemplateManager}
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiFile}
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate
import com.intellij.structuralsearch.impl.matcher.{CompiledPattern, GlobalMatchingVisitor, MatchContext}
import com.intellij.structuralsearch.inspection.SSBasedInspection
import com.intellij.structuralsearch.plugin.replace.impl.{ParameterInfo, ReplacementBuilder}
import com.intellij.structuralsearch.plugin.replace.{ReplaceOptions, ReplacementInfo}
import com.intellij.structuralsearch.plugin.ui.{Configuration, UIUtil}
import com.intellij.structuralsearch.{MatchOptions, MatchResult, MatchVariableConstraint, StructuralSearchProfile, StructuralSearchProfileBase}
import com.intellij.util.SmartList
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.codeInsight.template.impl.{Scala3FileTemplateContextType, ScalaFileTemplateContextType}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.incremental.Highlighting.builtInHighlightingDisabledIn
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScGuard, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterType, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDeclaration, ScTypeAliasDefinition, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGiven, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypeBoundsOwner}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchProfile.REPLACEMENT_CONTEXT
import org.jetbrains.plugins.scala.structuralSearch.predicates.ScExprTypePredicate
import org.jetbrains.plugins.scala.structuralSearch.replace.ScalaReplacementBuilder
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage, incremental}

import java.{lang, util}

final class ScalaStructuralSearchProfile extends StructuralSearchProfileBase {
  override protected def getVarPrefixes: Array[String] = Array("__$_")

  override def isMyLanguage(@NotNull language: Language): Boolean =
    language == ScalaLanguage.INSTANCE || language == Scala3Language.INSTANCE

  override def isMatchNode(element: PsiElement): Boolean = {
    val project = element.getProject

    // Structural search inspections might trigger type inference and interfere with incremental highlighting.
    if (builtInHighlightingDisabledIn(project) || incremental.Highlighting.enabledIn(project)) {
      if (hasStructuralSearchInspections(project))
        return false
    }

    super.isMatchNode(element)
  }

  private def hasStructuralSearchInspections(project: Project) = {
    val currentInspectionProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile
    val structuralSearchInspection = SSBasedInspection.getStructuralSearchInspection(currentInspectionProfile)
    structuralSearchInspection.getConfigurations.stream().anyMatch { configuration =>
      val fileType = configuration.getFileType
      fileType != null && isMyLanguage(fileType.getLanguage) && {
        val tool = currentInspectionProfile.getToolsOrNull(configuration.getUuid, project)
        tool != null && tool.isEnabled
      }
    }
  }

  override def getContext(pattern: String, @Nullable language: Language, contextId: String): String =
    StructuralSearchProfile.PATTERN_PLACEHOLDER

  override def getTemplateContextTypeClass(language: Language): Class[? <: TemplateContextType] =
    if language == Scala3Language.INSTANCE then classOf[Scala3FileTemplateContextType] else classOf[ScalaFileTemplateContextType]

  override def getTemplateContextTypeClass: Class[? <: TemplateContextType] = classOf[ScalaFileTemplateContextType]

  override def createMatchingVisitor(globalVisitor: GlobalMatchingVisitor): PsiElementVisitor =
    ScalaMatchingVisitor(globalVisitor)

  override def compile(elements: Array[PsiElement], globalVisitor: GlobalCompilingVisitor): Unit = {
    new ScalaCompilingVisitor(globalVisitor).compile(elements)
  }

  override def getPresentableElement(element: PsiElement): PsiElement = {
    element match {
      case leaf: LeafPsiElement =>
        leaf.getParent match {
          case par: (ScTypeDefinition | ScFunction) => par
          case _ => element
        }
      case _ => element
    }
  }

  // use this to configure which modifier should be shown
  override def isApplicableConstraint(constraintName: String, variableNode: PsiElement, completePattern: Boolean, target: Boolean): Boolean = {
    if (variableNode == null) return false
    constraintName match {
      case UIUtil.MINIMUM_ZERO =>
        isMinMaxApplicable(constraintName, variableNode, completePattern, target)
      case UIUtil.MAXIMUM_UNLIMITED =>
        isMinMaxApplicable(constraintName, variableNode, completePattern, target)
          && isMaxApplicable(constraintName, variableNode, completePattern, target)
      case UIUtil.TYPE | UIUtil.TYPE_REGEX =>
        isTypeRegexApplicable(variableNode)
      case UIUtil.TEXT_HIERARCHY => false
      case _ =>
        super.isApplicableConstraint(constraintName, variableNode, completePattern, target)
    }
  }

  private def isTypeRegexApplicable(variableNode: PsiElement): Boolean =
    variableNode.getParent.is[ScTypeDefinition] ||
      (variableNode.getParent != null &&
        variableNode.getParent.isInstanceOf[Typeable] ||
          (variableNode.getParent.getParent != null &&
            variableNode.getParent.is[ScStableCodeReference] && variableNode.getParent.getParent.isInstanceOf[Typeable]))

  private def isMinMaxApplicable(constraintName: String, variableNode: PsiElement, completePattern: Boolean, target: Boolean): Boolean =
    if (completePattern || target || variableNode == null) return false
    variableNode.getParent match {
      case ref: ScReferenceExpression if ref.qualifier.nonEmpty => false
      case parent => parent.getParent match {
        case grandParent: ScImportExpr => grandParent.getParent match {
          case _: ScExportStmt => true
          case _ => false
        }
        case grandParent: ScSimpleTypeElement =>
          grandParent.getParent match {
            case _: ScParameterType => false
            case _ => true
          }
        case _ => true
      }
    }

  private def checkLowerUpperMaxApplicable(boundsOwner: ScTypeBoundsOwner, simpType: ScSimpleTypeElement): Boolean = {
    !(boundsOwner.lowerTypeElement.contains(simpType) || boundsOwner.upperTypeElement.contains(simpType))
  }

  private def isMaxApplicable(constraintName: String, variableNode: PsiElement, completePattern: Boolean, target: Boolean): Boolean = {
    variableNode.getParent match {
      case codeRef: ScStableCodeReference => codeRef.getParent match {
        case simpType: ScSimpleTypeElement => simpType.getParent match {
          case fun: ScFunction => !fun.returnTypeElement.contains(simpType)
          case valvar: ScValueOrVariable => !valvar.typeElement.contains(simpType)
          case typeParam: (ScTypeParam | ScTypeAliasDeclaration) => checkLowerUpperMaxApplicable(typeParam, simpType)
          case typeAlias: ScTypeAliasDefinition => checkLowerUpperMaxApplicable(typeAlias, simpType) && !typeAlias.aliasedTypeElement.contains(simpType)
          case _ => true
        }
        case _ => true
      }
      case refExp: ScReferenceExpression => refExp.getParent match {
        case param: ScParameter => !param.getDefaultExpression.contains(refExp)
        case param: ScValueOrVariableDefinition => !param.expr.contains(refExp)
        case _: ScGuard => false
        case _: ScReferenceExpression => false
        case _ => true
      }
      case g: ScGiven => !g.nameElement.contains(variableNode)
      case _ => true
    }
  }

  override def getCustomPredicates(constraint: MatchVariableConstraint, name: String, options: MatchOptions): util.List[MatchPredicate] = {
    val result = new SmartList[MatchPredicate]()

    if (!StringUtil.isEmptyOrSpaces(constraint.getNameOfExprType)) {
      result.add(new ScExprTypePredicate(constraint.getNameOfExprType, name, constraint.isExprTypeWithinHierarchy, constraint.isInvertExprType, options.isCaseSensitiveMatch, constraint.isRegexExprType))
    }

    result
  }

  override def createCompiledPattern(): CompiledPattern =
    new CompiledPattern {
      override def getTypedVarPrefixes: Array[String] = getVarPrefixes

      // if a variable is set inside the template, normally getText is used to extract the name
      // we can also use getTypedVarString to extract the name (e.g. special for an annotations, functions, ...)
      override def getTypedVarString(element: PsiElement): String =
        element match {
          case par: ScNamedElement =>
            par.name
          case annot: ScAnnotation =>
            annot.constructorInvocation.typeElement.getText
          case caseClause: ScCaseClause =>
            caseClause.pattern.map(pat => if pat.bindings.size == 1 then pat.bindings.head.name else pat.getText).getOrElse("")
          case valvar: ScValueOrVariable =>
            if (valvar.declaredNames.size == 1) valvar.declaredNames.head
            else super.getTypedVarString(valvar)
          case constrInv: ScConstructorInvocation =>
            constrInv.typeElement.getText
          case typeElement: ScTypeElement =>
            typeElement.getFirstChild.getText
          case guard: ScGuard =>
            guard.expr.map(_.getText).getOrElse("")
          case reference: ScReference => reference.refName
          case methodInv: MethodInvocation =>
            val unwrapMethodName: ScExpression => PsiElement = {
              case ref: ScReferenceExpression => ref.nameId
              case expr => expr
            }
            unwrapMethodName(methodInv.getInvokedExpr).getText
          case exportStmt: ScExportStmt =>
            if (exportStmt.importExprs.size == 1) exportStmt.importExprs.head.getText
            else super.getTypedVarString(exportStmt)
          case _ =>
            super.getTypedVarString(element)
        }

      override def isTypedVar(str: String): Boolean = !str.contains(' ') && getVarPrefixes.exists(str.startsWith)

      override def doCreateSubstitutionHandler(name: String, target: Boolean, minOccurs: Int, maxOccurs: Int, greedy: Boolean): SubstitutionHandler = {
        new SubstitutionHandler(name, target, minOccurs, maxOccurs, greedy) {
          override def addResult(`match`: PsiElement, start: Int, end: Int, context: MatchContext): Unit = {
            if (context.getResult.findChild(name) == null)
              this.reset()
            super.addResult(`match`, start, end, context)
          }
        }
      }
    }

  override def getPredefinedTemplates: Array[Configuration] = ScalaPredefinedConfigurations.createPredefinedTemplated()

  override def provideAdditionalReplaceOptions(node: PsiElement, options: ReplaceOptions, builder: ReplacementBuilder): Unit = {
    val originalReplacement = TemplateManager.getInstance(node.getProject).createTemplate("", "", options.getReplacement).getTemplateText

    val sb = StringBuilder()
    ScalaReplacementBuilder(this).buildReplacement(node, None, sb)
    val emptyReplacement = sb.toString()

    node.elements.foreach(el => {
      builder.findParameterization(el) match {
        case null =>
        case typeinfo =>
          typeinfo.putUserData(REPLACEMENT_CONTEXT, (originalReplacement, emptyReplacement))
      }
    })
  }

  override def handleSubstitution(info: ParameterInfo, res: MatchResult, result: lang.StringBuilder, replacementInfo: ReplacementInfo): Unit = {
    info.getUserData(REPLACEMENT_CONTEXT) match {
      case null =>
      case (orig, empty) =>
        if (result.toString != orig && result.toString != empty)
          return
    }
    result.delete(0, result.length())

    val repl: PsiElement = info.getElement
    if (repl == null)
      throw Exception("May not be null")
    val replRoot: PsiFile = repl.getContainingFile

    ScalaReplacementBuilder(this).buildReplacement(replRoot, res.getRoot, StringBuilder(result))
  }

  override def handleNoSubstitution(info: ParameterInfo, result: lang.StringBuilder): Unit = {
    info.getUserData(REPLACEMENT_CONTEXT) match {
      case null =>
      case (orig, empty) =>
        if (result.toString == orig) {
          result.delete(0, result.length())
          result.append(empty)
        }
    }
  }
}

object ScalaStructuralSearchProfile {
  val PATTERN_CONTEXT = "__pattern__context"
  val SCOPE_ID = "__scopematch__id"
  val REPLACEMENT_CONTEXT: Key[(String, String)] = Key("PARAMETER_CONTEXT")
}
