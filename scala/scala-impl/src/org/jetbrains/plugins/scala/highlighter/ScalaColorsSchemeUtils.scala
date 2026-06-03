package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.{PsiClass, PsiElement, PsiField, PsiMethod, PsiModifierListOwner}
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, PsiClassExt, PsiMemberExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScAssignment, ScForBinding, ScFunctionExpr, ScGenerator, ScMethodCall, ScNameValuePair, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumClassCase, ScEnumSingletonCase, ScFunction, ScFunctionDeclaration, ScFunctionDefinition, ScMacroDefinition, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenDefinition.DesugaredTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScGiven, ScMember, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocResolvableCodeReference, ScDocTagValue}

object ScalaColorsSchemeUtils {
  def findAttributesKeyByParent(element: PsiElement): Option[TextAttributesKey] =
    findHighlightInfoTypeByParent(element).map(_.getAttributesKey)

  def findHighlightInfoTypeByParent(element: PsiElement): Option[HighlightInfoType] =
    getParentByStub(element) match {
      case _: ScGiven | DesugaredTypeDefinition(_)    => Some(ScalaHighlightInfoTypes.GIVEN)
      case _: ScEnum                                  => Some(ScalaHighlightInfoTypes.ENUM)
      case _: ScEnumClassCase                         => Some(ScalaHighlightInfoTypes.ENUM_CLASS_CASE)
      case _: ScEnumSingletonCase                     => Some(ScalaHighlightInfoTypes.ENUM_SINGLETON_CASE)
      case _: ScNameValuePair                         => Some(ScalaHighlightInfoTypes.ANNOTATION_ATTRIBUTE)
      case _: ScTypeParam                             => Some(ScalaHighlightInfoTypes.TYPEPARAM)
      case c: ScClass if c.getModifierList.isAbstract => Some(ScalaHighlightInfoTypes.ABSTRACT_CLASS)
      case _: ScClass                                 => Some(ScalaHighlightInfoTypes.CLASS)
      case _: ScObject                                => Some(ScalaHighlightInfoTypes.OBJECT)
      case _: ScTrait                                 => Some(ScalaHighlightInfoTypes.TRAIT)
      case x @ (_: ScReferencePattern | _: ScFieldId) =>
        x.asInstanceOf[ScNamedElement].nameContext match {
          case r@(_: ScValue | _: ScVariable) =>
            getParentByStub(r) match {
              case _: ScTemplateBody | _: ScEarlyDefinitions =>
                val attributes = r match {
                  case mod: ScModifierListOwner if hasLazyModifier(mod) => ScalaHighlightInfoTypes.LAZY
                  case _: ScValue                                       => ScalaHighlightInfoTypes.VALUES
                  case _: ScVariable                                    => ScalaHighlightInfoTypes.VARIABLES
                  case _                                                => ScalaHighlightInfoTypes.IDENTIFIER
                }
                Some(attributes)
              case _ =>
                val attributes = r match {
                  case mod: ScModifierListOwner if hasLazyModifier(mod) => ScalaHighlightInfoTypes.LOCAL_LAZY
                  case _: ScValue                                       => ScalaHighlightInfoTypes.LOCAL_VALUES
                  case _: ScVariable                                    => ScalaHighlightInfoTypes.LOCAL_VARIABLES
                  case _                                                => ScalaHighlightInfoTypes.IDENTIFIER
                }
                Some(attributes)
            }
          case _: ScCaseClause                  => Some(ScalaHighlightInfoTypes.PATTERN)
          case _: ScGenerator | _: ScForBinding => Some(ScalaHighlightInfoTypes.GENERATOR)
          case _ => None
        }
      case _: ScFunctionDefinition | _: ScFunctionDeclaration => Some(ScalaHighlightInfoTypes.METHOD_DECLARATION)
      case _ => None
    }


  def textAttributesKey(resolvedElement: PsiElement,
                        refElement: Option[ScReference] = None,
                        qualNameToType: Map[String, StdType] = Map.empty): TextAttributesKey =
    textHighlightInfoType(resolvedElement, refElement, qualNameToType).getAttributesKey

  def textHighlightInfoType(resolvedElement: PsiElement,
                            refElement: Option[ScReference] = None,
                            qualNameToType: Map[String, StdType] = Map.empty): HighlightInfoType =
    resolvedElement match {
      case _: ScGiven | DesugaredTypeDefinition(_)                                             => ScalaHighlightInfoTypes.GIVEN
      case _: ScEnum | ScObject.Companion(_: ScEnum)                                           => ScalaHighlightInfoTypes.ENUM
      case _: ScEnumClassCase | ScObject.Companion(_: ScEnumClassCase)                         => ScalaHighlightInfoTypes.ENUM_CLASS_CASE
      case _: ScEnumSingletonCase                                                              => ScalaHighlightInfoTypes.ENUM_SINGLETON_CASE
      case c: PsiClass if qualNameToType.contains(c.qualifiedName)                             => ScalaHighlightInfoTypes.PREDEF //this is td, it's important!
      case c: ScClass if c.getModifierList.isAbstract                                          => ScalaHighlightInfoTypes.ABSTRACT_CLASS
      case _: ScTypeParam                                                                      => ScalaHighlightInfoTypes.TYPEPARAM
      case _: ScTypeAlias                                                                      => ScalaHighlightInfoTypes.TYPE_ALIAS
      case _: ScClass if refElement.exists(referenceIsToCompanionObjectOfClass)                => ScalaHighlightInfoTypes.OBJECT
      case _: ScClass                                                                          => ScalaHighlightInfoTypes.CLASS
      case _: ScObject                                                                         => ScalaHighlightInfoTypes.OBJECT
      case _: ScTrait                                                                          => ScalaHighlightInfoTypes.TRAIT
      case c: PsiClass if c.isInterface                                                        => ScalaHighlightInfoTypes.TRAIT
      case c: PsiClass if hasModifier(c, "abstract")                                           => ScalaHighlightInfoTypes.ABSTRACT_CLASS
      case _: PsiClass if refElement.exists(_.is[ScReferenceExpression])                       => ScalaHighlightInfoTypes.OBJECT
      case _: PsiClass if refElement.isEmpty || refElement.exists(_.is[ScStableCodeReference]) => ScalaHighlightInfoTypes.CLASS
      case p: ScBindingPattern                                                                 => highlightInfoType(p)
      case f: PsiField if !hasModifier(f, "final")                                             => ScalaHighlightInfoTypes.VARIABLES
      case _: PsiField                                                                         => ScalaHighlightInfoTypes.VALUES
      case p: ScParameter =>
        refElement match {
          case Some(_: ScDocTagValue | _: ScDocResolvableCodeReference) =>
            //when parameter/field is references from scaladoc, we always highlight it as a parameter, not as a field
            ScalaHighlightInfoTypes.PARAMETER
          case _ =>
            parameterHighlightInfoType(p)
        }
      case f@(_: ScFunctionDefinition | _: ScFunctionDeclaration | _: ScMacroDefinition) => highlightInfoType(f.asInstanceOf[ScFunction])
      case m: PsiMethod                                                                  => highlightInfoType(m)
      case _                                                                             => ScalaHighlightInfoTypes.IDENTIFIER
    }

  //SCL-7499
  def parameterHighlightInfoType(p: ScParameter): HighlightInfoType = p match {
    case cp: ScClassParameter =>
      classParamHighlightInfoType(cp)
    case _ if isParameterOfAnonymousFunction(p) =>
      ScalaHighlightInfoTypes.PARAMETER_OF_ANONIMOUS_FUNCTION
    case _ =>
      ScalaHighlightInfoTypes.PARAMETER
  }

  def classParamHighlightInfoType(classParameter: ScClassParameter): HighlightInfoType =
    if (classParameter.isClassMember)
      ScalaHighlightInfoTypes.VALUES
    else
      ScalaHighlightInfoTypes.PARAMETER

  private def isParameterOfAnonymousFunction(p: ScParameter): Boolean = p.getContext match {
    case clause: ScParameterClause => clause.getContext.getContext match {
      case _: ScFunctionExpr => true
      case _ => false
    }
    case _ => false
  }

  private def highlightInfoType(pattern: ScBindingPattern): HighlightInfoType = {
    val parent = pattern.nameContext
    parent match {
      case r@(_: ScValue | _: ScVariable) =>
        getParentByStub(parent) match {
          case _: ScTemplateBody | _: ScEarlyDefinitions =>
            r match {
              case mod: ScModifierListOwner if hasLazyModifier(mod) => ScalaHighlightInfoTypes.LAZY
              case v: ScValue if isHighlightableScalaTestKeyword(v) => ScalaHighlightInfoTypes.SCALATEST_KEYWORD
              case _: ScValue                                       => ScalaHighlightInfoTypes.VALUES
              case _: ScVariable                                    => ScalaHighlightInfoTypes.VARIABLES
              case _                                                => ScalaHighlightInfoTypes.IDENTIFIER
            }
          case _ =>
            r match {
              case mod: ScModifierListOwner if hasLazyModifier(mod) => ScalaHighlightInfoTypes.LOCAL_LAZY
              case _: ScValue                                       => ScalaHighlightInfoTypes.LOCAL_VALUES
              case _: ScVariable                                    => ScalaHighlightInfoTypes.LOCAL_VARIABLES
              case _                                                => ScalaHighlightInfoTypes.IDENTIFIER
            }
        }
      case _: ScCaseClause                                          => ScalaHighlightInfoTypes.PATTERN
      case _: ScGenerator | _: ScForBinding                         => ScalaHighlightInfoTypes.GENERATOR
      case _                                                        => ScalaHighlightInfoTypes.IDENTIFIER
    }
  }

  private def highlightInfoType(function: ScFunction): HighlightInfoType =
    if (isHighlightableScalaTestKeyword(function))
      ScalaHighlightInfoTypes.SCALATEST_KEYWORD
    else
      function.containingClass match {
        case o: ScObject if o.syntheticMethods.contains(function) =>
          ScalaHighlightInfoTypes.OBJECT_METHOD_CALL
        case _ =>
          getParentByStub(function) match {
            case _: ScTemplateBody | _: ScEarlyDefinitions =>
              getParentByStub(getParentByStub(getParentByStub(function))) match {
                case _: ScClass | _: ScTrait => ScalaHighlightInfoTypes.METHOD_CALL
                case _: ScObject             => ScalaHighlightInfoTypes.OBJECT_METHOD_CALL
                case _                       => ScalaHighlightInfoTypes.IDENTIFIER
              }
            case _ =>
              ScalaHighlightInfoTypes.LOCAL_METHOD_CALL
          }
      }

  private def hasLazyModifier(owner: ScModifierListOwner): Boolean =
    owner.hasModifierPropertyScala("lazy")

  private def hasModifier(owner: PsiModifierListOwner, property: String): Boolean =
    Option(owner.getModifierList).exists(_.hasModifierProperty(property))

  private def highlightInfoType(method: PsiMethod): HighlightInfoType =
    if (hasModifier(method, "static")) ScalaHighlightInfoTypes.OBJECT_METHOD_CALL else ScalaHighlightInfoTypes.METHOD_CALL

  private def getParentByStub(x: PsiElement): PsiElement = x match {
    case el: ScalaStubBasedElementImpl[_, _] => el.getContext
    case _ => x.getContext
  }

  private def referenceIsToCompanionObjectOfClass(r: ScReference): Boolean = Option(r.getContext).exists {
    // These references to 'Foo' should be 'object' references: case class Foo(a: Int); Foo(1); Foo.apply(1).
    case _: ScMethodCall | _: ScReferenceExpression => true
    case _ => false
  }

  private def isHighlightableScalaTestKeyword(m: ScMember): Boolean =
    m.containingClass != null &&
      ScalaTestHighlighterUtil.isHighlightableScalaTestKeyword(
        m.containingClass.qualifiedName,
        m.names.headOption.orNull,
        m.getProject
      )

  object NamedArgument {
    def unapply(psiElement: PsiElement): Option[ScAssignment] = psiElement match {
      case (a: ScAssignment) & Parent(_: ScArgumentExprList) =>
        a.leftExpression match {
          //NOTE: this ignores underscore lambdas with assignment, that represented as an ScAssignment as well.
          //Example: foo(_.field = null)
          //It's a much more lightweight alternative to using ScUnderScoreSectionUtil.isUnderscoreFunction
          case ref: ScReferenceExpression if ref.qualifier.isEmpty =>
            Some(a)
          case _ => None
        }
      case _ =>
        None
    }
  }
}
