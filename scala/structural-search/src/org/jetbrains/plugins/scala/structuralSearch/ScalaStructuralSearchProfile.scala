package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.lang.Language
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.predicates.{ExprTypePredicate, MatchPredicate, NotPredicate}
import com.intellij.structuralsearch.impl.matcher.{CompiledPattern, GlobalMatchingVisitor}
import com.intellij.structuralsearch.plugin.replace.impl.{ParameterInfo, ReplacementBuilder, Replacer}
import com.intellij.structuralsearch.plugin.replace.{ReplaceOptions, ReplacementInfo}
import com.intellij.structuralsearch.plugin.ui.{Configuration, UIUtil}
import com.intellij.structuralsearch.{MatchOptions, MatchResult, MatchVariableConstraint, StructuralSearchProfile, StructuralSearchProfileBase}
import com.intellij.util.SmartList
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaFileTemplateContextType
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScBlockExpr, ScExpression, ScGuard, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterType, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDeclaration, ScTypeAliasDefinition, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypeBoundsOwner}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchProfile.PARAMETER_CONTEXT
import org.jetbrains.plugins.scala.structuralSearch.exceptions.StructuralReplaceException
import org.jetbrains.plugins.scala.structuralSearch.predicates.ScExprTypePredicate
import org.jetbrains.plugins.scala.structuralSearch.replace.ScalaSubstitutor
import org.jetbrains.plugins.scala.{NotImplementedError, Scala3Language, ScalaLanguage}

import java.{lang, util}
import scala.collection.mutable

final class ScalaStructuralSearchProfile extends StructuralSearchProfileBase {
  override protected def getVarPrefixes: Array[String] = Array("__$_")

  override def isMyLanguage(@NotNull language: Language): Boolean =
    language == ScalaLanguage.INSTANCE || language == Scala3Language.INSTANCE

  override def getContext(pattern: String, @Nullable language: Language, contextId: String): String =
    StructuralSearchProfile.PATTERN_PLACEHOLDER

  override def getTemplateContextTypeClass: Class[_ <: TemplateContextType] = classOf[ScalaFileTemplateContextType]

  override def createMatchingVisitor(globalVisitor: GlobalMatchingVisitor): PsiElementVisitor =
    ScalaMatchingVisitor(globalVisitor)

  override def compile(elements: Array[PsiElement], globalVisitor: GlobalCompilingVisitor): Unit = {
    new ScalaCompilingVisitor(globalVisitor).compile(elements)
  }

  // use this to configure which modifier should be shown
  override def isApplicableConstraint(constraintName: String, variableNode: PsiElement, completePattern: Boolean, target: Boolean): Boolean =
    constraintName match {
      case UIUtil.MINIMUM_ZERO =>
        isMinMaxApplicable(constraintName, variableNode, completePattern, target)
      case UIUtil.MAXIMUM_UNLIMITED =>
        isMinMaxApplicable(constraintName, variableNode, completePattern, target)
        && isMaxApplicable(constraintName, variableNode, completePattern, target)
      case UIUtil.TYPE | UIUtil.TYPE_REGEX =>
        isTypeRegexApplicable(variableNode)
      case _ =>
        super.isApplicableConstraint(constraintName, variableNode, completePattern, target)
    }

  private def isTypeRegexApplicable(variableNode: PsiElement): Boolean =
    variableNode.getParent.is[ScTypeDefinition] || variableNode.getParent.isInstanceOf[Typeable]
      || (variableNode.getParent.is[ScStableCodeReference] && variableNode.getParent.getParent.isInstanceOf[Typeable])

  private def isMinMaxApplicable(constraintName: String, variableNode: PsiElement, completePattern: Boolean, target: Boolean): Boolean =
    if (completePattern || target || variableNode == null) return false
    variableNode.getParent match {
      case parent => parent.getParent match {
        case _: ScImportExpr => false
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
      // we can also use getTypedVarString to extract the name (e.g. special for an annotation
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
            case methodInv: MethodInvocation =>
              val unwrapMethodName: ScExpression => PsiElement = {
                case ref: ScReferenceExpression => ref.nameId
                case expr => expr
              }
              unwrapMethodName(methodInv.getInvokedExpr).getText
            case _ =>
              super.getTypedVarString(element)
          }

      override def isTypedVar(str: String): Boolean = {
        !str.contains(' ') && getVarPrefixes.exists(str.startsWith)
      }
    }

  override def getPredefinedTemplates: Array[Configuration] = ScalaPredefinedConfigurations.createPredefinedTemplated()

  override def provideAdditionalReplaceOptions(node: PsiElement, options: ReplaceOptions, builder: ReplacementBuilder): Unit = {
    val profile = this
    node.accept(new ScalaRecursiveElementVisitor() {
      override def visitParameter(parameter: ScParameter): Unit = {
        val name = parameter.nameId

        val nameInfo = builder.findParameterization(name)
        if (nameInfo == null) return
        val infos = mutable.Map(nameInfo.getName -> nameInfo)
        nameInfo.putUserData(PARAMETER_CONTEXT, infos)
        nameInfo.setElement(parameter)
        nameInfo.setArgumentContext(false)

        def putInformation(info: ParameterInfo, element: PsiElement): Unit = {
          info.setArgumentContext(false)
          info.putUserData(PARAMETER_CONTEXT, mutable.Map(info.getName -> info))
          info.setElement(element)
          infos.put(info.getName, info)
        }

        parameter.typeElement match {
          case None =>
          case Some(ty) =>
            if (profile.isReplacementTypedVariable(ty.getText)) {
              builder.findParameterization(ty.getParent) match {
                case null =>
                case typeInfo => putInformation(typeInfo, ty.getParent)
              }
            }
        }
        if (parameter.hasInitializer) {
          val initializer = parameter.getInitializer
          if (profile.isReplacementTypedVariable(initializer.getText)) {
            builder.findParameterization(initializer) match {
              case null =>
              case initInfo => putInformation(initInfo, initializer)
            }
          }
        }
        for (anno <- parameter.annotations) {
          if (profile.isReplacementTypedVariable(anno.constructorInvocation.typeElement.getText)) {
            builder.findParameterization(anno.annotationExpr) match {
              case null => builder.findParameterization(anno.constructorInvocation.typeElement) match {
                case null =>
                case annoInfo => putInformation(annoInfo, anno)
              }
              case annoInfo => putInformation(annoInfo, anno)
            }
          }
        }
      }
    })
  }

  override def handleSubstitution(info: ParameterInfo, res: MatchResult, result: lang.StringBuilder, replacementInfo: ReplacementInfo): Unit = {
    if (info.getName != res.getName) return

    val element: PsiElement = info.getElement
    if (element == null) {
      super.handleSubstitution(info, res, result, replacementInfo)
      return
    }
    var offset = 0
    val sb = new StringBuilder()

    if (res.hasChildren && !res.isScopeMatch) {
      // compound matches
      // TODO class, definition, function, case clause, type case
      element match {
        case para: ScParameter => offset = ScalaSubstitutor.handleParameter(sb, info, res, result, para, info.getUserData(PARAMETER_CONTEXT), this)
        case anno: ScAnnotation => if (info.getUserData(PARAMETER_CONTEXT) == null) ScalaSubstitutor.appendAnnotation(sb, res, anno, this)
        case el => el.getParent match {
          case _: ScBlockExpr => ScalaSubstitutor.handleBlock(sb, info, res, result, replacementInfo)
          case _ => throw new StructuralReplaceException(s"Replacing is not implemented for ${el.getParent.getClass}")
        }
      }
    } else element match {
      case para: ScParameter => offset = ScalaSubstitutor.handleParameter(sb, info, res, result, para, info.getUserData(PARAMETER_CONTEXT), this)
      case _ => element.getParent match {
        case _ => res.getMatch match {
          case func: ScFunction => sb.append(func.name)
          case _ => if (info.getUserData(PARAMETER_CONTEXT) == null) sb.append(res.getMatchImage)
        }
      }
    }

    offset = Replacer.insertSubstitution(result, offset, info, sb.result())
  }
}

object ScalaStructuralSearchProfile {
  protected val PARAMETER_CONTEXT: Key[mutable.Map[String, ParameterInfo]] = Key("PARAMETER_CONTEXT")
}
