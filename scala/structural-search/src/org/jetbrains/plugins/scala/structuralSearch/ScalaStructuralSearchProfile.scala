package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.lang.Language
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate
import com.intellij.structuralsearch.impl.matcher.{CompiledPattern, GlobalMatchingVisitor}
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo
import com.intellij.structuralsearch.plugin.replace.impl.ParameterInfo
import com.intellij.structuralsearch.plugin.ui.{Configuration, UIUtil}
import com.intellij.structuralsearch.{MatchOptions, MatchResult, MatchVariableConstraint, StructuralSearchProfile, StructuralSearchProfileBase}
import com.intellij.util.SmartList
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaFileTemplateContextType
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScStableCodeReference, patterns}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScGuard, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterType, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDeclaration, ScTypeAliasDefinition, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypeBoundsOwner}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.structuralSearch.predicates.ScExprTypePredicate
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ChildrenIterator

import java.{lang, util}
import scala.jdk.CollectionConverters.CollectionHasAsScala

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
      case UIUtil.TEXT_HIERARCHY => false
      case _ =>
        super.isApplicableConstraint(constraintName, variableNode, completePattern, target)
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

  //  override def provideAdditionalReplaceOptions(node: PsiElement, options: ReplaceOptions, builder: ReplacementBuilder): Unit = {
  //    val result = ""
  //    node.accept(new ScalaRecursiveElementVisitor() {
  //      override def visitElement(element: PsiElement): Unit = {
  //        super.visitElement(element)
  //        builder.findParameterization(element) match {
  //          case null =>
  //          case typeinfo =>
  //            typeinfo.putUserData(RESULT_CONTEXT, result)
  //        }
  //      }
  //
  //      override def visitScalaElement(element: ScalaPsiElement): Unit = {
  //        super.visitScalaElement(element)
  //        visitElement(element)
  //      }
  //    })
  //  }

  override def handleSubstitution(info: ParameterInfo, res: MatchResult, fullResult: lang.StringBuilder, replacementInfo: ReplacementInfo): Unit = {
    fullResult.delete(0, fullResult.length())
    val profile = this

    val repl: PsiElement = info.getElement
    if (repl == null)
      throw Exception("May not be null")
    val replRoot = repl.getContainingFile

    def findMatchResult(res: MatchResult, name: String): Option[MatchResult] = {
      res.getChildren.asScala.find(_.getName == name)
        .orElse(Option.when(res.getName == name)(res))
    }
    {
      val sb = StringBuilder()
      buildReplacement(replRoot, res.getRoot, sb)
      fullResult.append(sb.result())
    }

    def buildChildren(psi: PsiElement,
                      scopeRes: MatchResult,
                      result: StringBuilder,
                      insertBefore: Map[PsiElement, String] = Map(),
                      insertAfter: Map[PsiElement, String] = Map(),
                      endElement: Option[PsiElement] = None): Unit = {
      for (cur <- ChildrenIterator(psi)) {
        insertBefore.get(cur).foreach(result.append)
        buildReplacement(cur, scopeRes, result)
        insertAfter.get(cur).foreach(result.append)

        if (endElement.contains(cur))
          return
      }
    }

    def ifNotMentioned(patternOpt: Option[PsiElement], replaceOpt: Option[PsiElement], refEl: PsiElement, text: Option[String]): Map[PsiElement, String] = {
      if (patternOpt.isEmpty && replaceOpt.isEmpty) {
        text.map(t => Map(refEl -> t)).getOrElse(Map())
      } else Map()
    }

    def handleScope[T <: PsiElement](replacePattern: T, ident: Option[PsiElement], scopeRes: MatchResult, result: StringBuilder, body: ((PsiElement, MatchResult)) => Unit): Unit = {
      ident.filter(ident => profile.isReplacementTypedVariable(ident.getText))
        .map(ident =>
          findMatchResult(scopeRes, profile.stripReplacementTypedVariableDecorations(ident.getText))
            .map(subRes => {
              if (subRes.isMultipleMatch) {
                // TODO copy whitespaces between the matches
                if (!subRes.getChildren.isEmpty) {
                  val it = subRes.getChildren.iterator
                  var last = it.next
                  body(ident, last)
                  while (it.hasNext) {
                    val cur = it.next
                    {
                     var el = last.getMatch.getNextSibling
                     while (el != cur.getMatch) {
                       result.append(el.getText)
                       el = el.getNextSibling
                     }
                    }
                    body(ident, cur)
                    last = cur
                  }
                }
              } else {
                body(ident, subRes)
              }
            })
            .getOrElse(Some(()))
        )
        .getOrElse(buildChildren(replacePattern, scopeRes, result))
    }

    def mergeInserts(map1: Map[PsiElement, String], map2: Map[PsiElement, String]): Map[PsiElement, String] = {
      map1 ++ map2.map {
        case (el, text) => el -> (map1.getOrElse(el, "") + text)
      }
    }

    def buildReplacement(element: PsiElement, scopeRes: MatchResult, result: StringBuilder): Unit = {
      element match {
        case replacePattern: ScFunction =>
          handleScope(replacePattern, Option(replacePattern.nameId), scopeRes, result, (ident, subRes) => {
            val parameterMatch = subRes.getMatch.asInstanceOf[ScFunction]

            val searchPattern = findMatchResult(subRes, "__pattern__context").getOrElse(throw new Exception("Expected pattern context")).getMatch.asInstanceOf[ScFunction]
//            val insertBefore = ifNotMentioned(searchPattern.annotations.headOption, replacePattern.annotations.headOption, ident, Some(parameterMatch.annotations.map(_.getText).mkString(" ") + " "))
//            val typeCopy = ifNotMentioned(searchPattern.typeElement, replacePattern.typeElement, ident, parameterMatch.typeElement.map(": " + _.getText))
//            val defCopy = ifNotMentioned(searchPattern.getDefaultExpression, replacePattern.getDefaultExpression, searchPattern.typeElement.getOrElse(ident), parameterMatch.getDefaultExpression.map(" = " + _.getText))
//            val insertAfter = mergeInserts(typeCopy, defCopy)
            buildChildren(replacePattern, subRes, result)
          })
        case replacePattern: ScParameter =>
          handleScope(replacePattern, Option(replacePattern.nameId), scopeRes, result, body = (ident, subRes) => {
            val parameterMatch = subRes.getMatch.asInstanceOf[ScParameter]

            val searchPattern = findMatchResult(subRes, "__pattern__context").getOrElse(throw new Exception("Expected pattern context")).getMatch.asInstanceOf[ScParameter]

            replacePattern.annotations.headOption match {
              case Some(_) =>
                replacePattern.annotations.foreach(anno => {
                  val prevSize = result.length()
                  buildReplacement(anno, subRes, result)
                  if (result.length() > prevSize)
                    result.append(" ")
                })
              case None =>
                if (searchPattern.annotations.isEmpty) {
                  result.append(parameterMatch.annotations.map(_.getText + " ").mkString)
                }
            }
            buildReplacement(ident, subRes, result)
            replacePattern.typeElement match {
              case Some(typ) =>
                result.append(": ")
                buildReplacement(typ.getParent, subRes, result)
              case None =>
                if (searchPattern.typeElement.isEmpty && parameterMatch.typeElement.nonEmpty) {
                  result.append(": ")
                  result.append(parameterMatch.typeElement.map(_.getText).getOrElse(""))
                }
            }
            replacePattern.getDefaultExpression match {
              case Some(default) =>
                val sb = StringBuilder()
                buildReplacement(default, subRes, sb)
                if (sb.nonEmpty) {
                  result.append(" = ")
                  result.append(sb.result())
                }
              case None =>
                if (searchPattern.getDefaultExpression.isEmpty && parameterMatch.getDefaultExpression.nonEmpty) {
                  result.append(" = ")
                  result.append(parameterMatch.getDefaultExpression.map(_.getText).getOrElse(""))
                }
            }
          })
        case annotation: ScAnnotation =>
          val text = annotation.constructorInvocation.reference.map(_.getText).getOrElse("")
          if (profile.isReplacementTypedVariable(text) && findMatchResult(scopeRes, profile.stripReplacementTypedVariableDecorations(text)).isEmpty)
            return
          buildChildren(element, scopeRes, result)
        case replacePattern: ScCaseClause =>
          handleScope(replacePattern, replacePattern.pattern, scopeRes, result, (ident, subRes) => {
            val ccMatch = subRes.getMatch match {
              case ccM: ScCaseClause => ccM
              case _ => throw Exception("Invalid element")
            }

            val searchPattern = findMatchResult(subRes, "__pattern__context").getOrElse(throw new Exception("Expected pattern context")).getMatch.asInstanceOf[ScCaseClause]
            val insertAfter = ifNotMentioned(searchPattern.guard, replacePattern.guard, ident, ccMatch.guard.map(" " + _.getText))
            buildChildren(replacePattern, subRes, result, insertAfter = insertAfter)
          })
        case _ if element.getFirstChild == null =>
          val text = element.getText
          if (profile.isReplacementTypedVariable(text)) {
            findMatchResult(scopeRes, profile.stripReplacementTypedVariableDecorations(text)) match {
              case None =>
              case Some(res) => result.append(res.getMatchImage)
            }
          } else {
            result.append(text)
          }
        case _ => buildChildren(element, scopeRes, result)
      }
    }
  }

  override def handleNoSubstitution(info: ParameterInfo, result: lang.StringBuilder): Unit = {}
}

object ScalaStructuralSearchProfile {
  protected val RESULT_CONTEXT: Key[String] = Key("RESULT_CONTEXT")
}
