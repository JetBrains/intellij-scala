package org.jetbrains.plugins.scala.highlighter

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.{PsiClass, PsiElement, PsiField, PsiMethod, PsiModifierListOwner}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiMemberExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScLiteral, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForBinding, ScGenerator, ScMethodCall, ScNameValuePair, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition, ScMacroDefinition, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.SOFT_KEYWORDS
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScBooleanLiteral, ScStringLiteral}

object ScalaColorsSchemeUtils {
  import DefaultHighlighter._
  import DefaultLanguageHighlighterColors.IDENTIFIER

  def highlightElement(element: PsiElement): (Option[TextAttributesKey], Boolean) = {
    element match {
      case _: ScAnnotation                                                    => (Some(ANNOTATION), false)
      case p: ScParameter if p.isAnonymousParameter                           => (Some(ANONYMOUS_PARAMETER), false)
      case _: ScParameter                                                     => (Some(PARAMETER), false)
      case _ if isSoftKeyword(element)                                        => (Some(KEYWORD), true)
      case _ if element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER => (highlightIdentifierByParent(element), false)
      case _: ScStringLiteral                                                 => (Some(STRING), false)
      case _: ScLiteral.Numeric                                               => (Some(NUMBER), false)
      case _: ScBooleanLiteral                                                => (Some(NUMBER), false) // todo: is this ok?
      case _                                                                  => (None, false)
    }
  }

  private def isSoftKeyword(element: PsiElement): Boolean =
    SOFT_KEYWORDS.contains(element.getNode.getElementType)

  private def highlightIdentifierByParent(element: PsiElement): Option[TextAttributesKey] =
    getParentByStub(element) match {
      case _: ScNameValuePair                         => Some(ANNOTATION_ATTRIBUTE)
      case _: ScTypeParam                             => Some(TYPEPARAM)
      case c: ScClass if c.getModifierList.isAbstract => Some(ABSTRACT_CLASS)
      case _: ScClass                                 => Some(CLASS)
      case _: ScObject                                => Some(OBJECT)
      case _: ScTrait                                 => Some(TRAIT)
      case x: ScBindingPattern =>
        x.nameContext match {
          case r@(_: ScValue | _: ScVariable) =>
            getParentByStub(r) match {
              case _: ScTemplateBody | _: ScEarlyDefinitions =>
                r match {
                  case mod: ScModifierListOwner if hasModifier(mod, "lazy") => Some(LAZY)
                  case _: ScValue                                                    => Some(VALUES)
                  case _: ScVariable                                                 => Some(VARIABLES)
                  case _                                                             => Some(IDENTIFIER)
                }
              case _ =>
                r match {
                  case mod: ScModifierListOwner if hasModifier(mod, "lazy") => Some(LOCAL_LAZY)
                  case _: ScValue                                                    => Some(LOCAL_VALUES)
                  case _: ScVariable                                                 => Some(LOCAL_VARIABLES)
                  case _                                                             => Some(IDENTIFIER)
                }
            }
          case _: ScCaseClause                  => Some(PATTERN)
          case _: ScGenerator | _: ScForBinding => Some(GENERATOR)
          case _                                => None
        }
      case _: ScFunctionDefinition | _: ScFunctionDeclaration => Some(METHOD_DECLARATION)
      case _ => None
    }

  def textAttributesKey(resolvedElement: PsiElement,
                        refElement: Option[ScReference] = None,
                        qualNameToType: Map[String, StdType] = Map.empty): TextAttributesKey =
    resolvedElement match {
      case c: PsiClass if qualNameToType.contains(c.qualifiedName)                       => PREDEF //this is td, it's important!
      case c: ScClass if c.getModifierList.isAbstract                                    => ABSTRACT_CLASS
      case _: ScTypeParam                                                                => TYPEPARAM
      case _: ScTypeAlias                                                                => TYPE_ALIAS
      case _: ScClass if refElement.exists(referenceIsToCompanionObjectOfClass)          => OBJECT
      case _: ScClass                                                                    => CLASS
      case _: ScObject                                                                   => OBJECT
      case _: ScTrait                                                                    => TRAIT
      case c: PsiClass if c.isInterface                                                  => TRAIT
      case c: PsiClass if hasModifier(c, "abstract")                                  => ABSTRACT_CLASS
      case _: PsiClass if refElement.exists(_.is[ScStableCodeReference])                 => CLASS
      case _: PsiClass if refElement.exists(_.is[ScReferenceExpression])                 => OBJECT
      case p: ScBindingPattern                                                           => attributesKey(p)
      case f: PsiField if !hasModifier(f, "final")                                    => VARIABLES
      case _: PsiField                                                                   => VALUES
      case p: ScParameter if p.isAnonymousParameter                                      => ANONYMOUS_PARAMETER
      case _: ScParameter                                                                => PARAMETER
      case f@(_: ScFunctionDefinition | _: ScFunctionDeclaration | _: ScMacroDefinition) => attributesKey(f.asInstanceOf[ScFunction])
      case m: PsiMethod                                                                  => attributesKey(m)
      case _                                                                             => IDENTIFIER
    }

  private def attributesKey(pattern: ScBindingPattern): TextAttributesKey = {
    val parent = pattern.nameContext
    parent match {
      case r@(_: ScValue | _: ScVariable) =>
        getParentByStub(parent) match {
          case _: ScTemplateBody | _: ScEarlyDefinitions =>
            r match {
              case mod: ScModifierListOwner if hasModifier(mod, "lazy") => LAZY
              case v: ScValue if isHighlightableScalaTestKeyword(v)     => SCALATEST_KEYWORD
              case _: ScValue                                           => VALUES
              case _: ScVariable                                        => VARIABLES
              case _                                                    => IDENTIFIER
            }
          case _ =>
            r match {
              case mod: ScModifierListOwner if hasModifier(mod, "lazy") => LOCAL_LAZY
              case _: ScValue                                           => LOCAL_VALUES
              case _: ScVariable                                        => LOCAL_VARIABLES
              case _                                                    => IDENTIFIER
            }
        }
      case _: ScCaseClause                                              => PATTERN
      case _: ScGenerator | _: ScForBinding                             => GENERATOR
      case _                                                            => IDENTIFIER
    }
  }

  private def attributesKey(function: ScFunction): TextAttributesKey =
    if (isHighlightableScalaTestKeyword(function))
      SCALATEST_KEYWORD
    else
      function.containingClass match {
        case o: ScObject if o.syntheticMethods.contains(function) =>
          OBJECT_METHOD_CALL
        case _ =>
          getParentByStub(function) match {
            case _: ScTemplateBody | _: ScEarlyDefinitions =>
              getParentByStub(getParentByStub(getParentByStub(function))) match {
                case _: ScClass | _: ScTrait => METHOD_CALL
                case _: ScObject             => OBJECT_METHOD_CALL
                case _                       => IDENTIFIER
              }
            case _ =>
              LOCAL_METHOD_CALL
          }
      }

  private def hasModifier(owner: ScModifierListOwner, property: String): Boolean =
    owner.hasModifierPropertyScala(property)

  private def hasModifier(owner: PsiModifierListOwner, property: String): Boolean =
    Option(owner.getModifierList).exists(_.hasModifierProperty(property))

  private def attributesKey(method: PsiMethod): TextAttributesKey =
    if (hasModifier(method, "static")) OBJECT_METHOD_CALL else METHOD_CALL

  private def getParentByStub(x: PsiElement): PsiElement = x match {
    case el: ScalaStubBasedElementImpl[_, _] => el.getParent
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
}
