package org.jetbrains.plugins.scala
package highlighter

import com.intellij.lang.annotation.{AnnotationHolder, Annotator, HighlightSeverity}
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.annotationHolder.ScalaAnnotationHolderAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, ScalaType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.ScalaCollectionHighlightingLevel
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

class ScalaColorSchemeAnnotator extends Annotator {
  import ScalaColorSchemeAnnotator._

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit = {
    highlightElement(element)(new ScalaAnnotationHolderAdapter(holder))
  }
}

object ScalaColorSchemeAnnotator {
  private val JAVA_COLLECTIONS_BASES = List("java.util.Map", "java.util.Collection")
  private val SCALA_FACTORY_METHODS_NAMES = Set("make", "apply")
  private val SCALA_COLLECTION_MUTABLE_BASE = "_root_.scala.collection.mutable."
  private val SCALA_COLLECTION_IMMUTABLE_BASE = "_root_.scala.collection.immutable."
  private val SCALA_COLLECTION_GENERIC_BASE = "_root_.scala.collection.generic."
  private val SCALA_PREDEFINED_OBJECTS = Set("scala", "scala.Predef")
  private val SCALA_PREDEF_IMMUTABLE_BASES = Set("_root_.scala.PredefMap", "_root_.scala.PredefSet", "scalaList",
    "scalaNil", "scalaStream", "scalaVector", "scalaSeq")

  private def getParentByStub(x: PsiElement): PsiElement = {
    x match {
      case el: ScalaStubBasedElementImpl[_, _] => el.getParent
      case _ => x.getContext
    }
  }

  def highlightReferenceElement(refElement: ScReference)(implicit holder: ScalaAnnotationHolder): Unit = {
    annotateCollectionType(refElement)

    val project: ProjectContext = refElement.projectContext

    if (refElement.parentOfType(classOf[ScConstructorInvocation], strict = false)
      .exists(_.getParent.is[ScAnnotationExpr])) return

    val resolvedElement = refElement.resolve()

    val QualNameToType = project.stdTypes.QualNameToType

    val attributes = resolvedElement match {
      case c: PsiClass if QualNameToType.contains(c.qualifiedName) => //this is td, it's important!
        DefaultHighlighter.PREDEF
      case x: ScClass if x.getModifierList.isAbstract =>
        DefaultHighlighter.ABSTRACT_CLASS
      case _: ScTypeParam =>
        DefaultHighlighter.TYPEPARAM
      case _: ScTypeAlias =>
        DefaultHighlighter.TYPE_ALIAS
      case _: ScClass if referenceIsToCompanionObjectOfClass(refElement) =>
        DefaultHighlighter.OBJECT
      case _: ScClass =>
        DefaultHighlighter.CLASS
      case _: ScObject =>
        DefaultHighlighter.OBJECT
      case _: ScTrait =>
        DefaultHighlighter.TRAIT
      case x: PsiClass if x.isInterface =>
        DefaultHighlighter.TRAIT
      case x: PsiClass if x.getModifierList != null && x.getModifierList.hasModifierProperty("abstract") =>
        DefaultHighlighter.ABSTRACT_CLASS
      case _: PsiClass if refElement.is[ScStableCodeReference] =>
        DefaultHighlighter.CLASS
      case _: PsiClass if refElement.is[ScReferenceExpression] =>
        DefaultHighlighter.OBJECT
      case x: ScBindingPattern =>
        val parent = x.nameContext
        parent match {
          case r@(_: ScValue | _: ScVariable) =>
            getParentByStub(parent) match {
              case _: ScTemplateBody | _: ScEarlyDefinitions =>
                r match {
                  case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                    DefaultHighlighter.LAZY
                  case v: ScValue if isHighlightableScalaTestKeyword(v) =>
                    DefaultHighlighter.SCALATEST_KEYWORD
                  case _: ScValue => DefaultHighlighter.VALUES
                  case _: ScVariable => DefaultHighlighter.VARIABLES
                  case _ => DefaultLanguageHighlighterColors.IDENTIFIER
                }
              case _ =>
                r match {
                  case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                    DefaultHighlighter.LOCAL_LAZY
                  case _: ScValue => DefaultHighlighter.LOCAL_VALUES
                  case _: ScVariable => DefaultHighlighter.LOCAL_VARIABLES
                  case _ => DefaultLanguageHighlighterColors.IDENTIFIER
                }
            }
          case _: ScCaseClause =>
            DefaultHighlighter.PATTERN
          case _: ScGenerator | _: ScForBinding =>
            DefaultHighlighter.GENERATOR
          case _ => DefaultLanguageHighlighterColors.IDENTIFIER
        }
      case x: PsiField =>
        if (!x.hasModifierProperty("final")) DefaultHighlighter.VARIABLES
        else DefaultHighlighter.VALUES
      case x: ScParameter if x.isAnonymousParameter => DefaultHighlighter.ANONYMOUS_PARAMETER
      case _: ScParameter => DefaultHighlighter.PARAMETER
      case x@(_: ScFunctionDefinition | _: ScFunctionDeclaration | _: ScMacroDefinition) =>
        if (isHighlightableScalaTestKeyword(x.asInstanceOf[ScFunction])) {
          DefaultHighlighter.SCALATEST_KEYWORD
        } else {
          val fun = x.asInstanceOf[ScFunction]
          val clazz = fun.containingClass
          clazz match {
            case o: ScObject if o.syntheticMethods.contains(fun) =>
              DefaultHighlighter.OBJECT_METHOD_CALL
            case _ =>
              DefaultLanguageHighlighterColors.IDENTIFIER
          }
          getParentByStub(x) match {
            case _: ScTemplateBody | _: ScEarlyDefinitions =>
              getParentByStub(getParentByStub(getParentByStub(x))) match {
                case _: ScClass | _: ScTrait =>
                  DefaultHighlighter.METHOD_CALL
                case _: ScObject =>
                  DefaultHighlighter.OBJECT_METHOD_CALL
                case _ => DefaultLanguageHighlighterColors.IDENTIFIER
              }
            case _ =>
              DefaultHighlighter.LOCAL_METHOD_CALL
          }
        }
      case x: PsiMethod =>
        if (x.getModifierList != null && x.getModifierList.hasModifierProperty("static")) {
          DefaultHighlighter.OBJECT_METHOD_CALL
        } else {
          DefaultHighlighter.METHOD_CALL
        }
      case _ => DefaultLanguageHighlighterColors.IDENTIFIER
    }
    createInfoAnnotation(refElement.nameId, attributes)
  }

  private def annotateCollectionType(refElement: ScReference)(implicit holder: ScalaAnnotationHolder): Unit = {
    def annotateCollectionByType(resolvedType: ScType): Unit = {
      val resolvedTypeName = resolvedType.presentableText(TypePresentationContext.emptyContext)
      if (ScalaNamesUtil.isOperatorName(
        resolvedTypeName.substring(0, resolvedTypeName.segmentLength(_ != '.')))) return

      val scalaProjectSettings: ScalaProjectSettings = ScalaProjectSettings.getInstance(refElement.getProject)

      scalaProjectSettings.getCollectionTypeHighlightingLevel match {
        case ScalaCollectionHighlightingLevel.None                           => return
        case ScalaCollectionHighlightingLevel.OnlyNonQualified =>
          refElement.qualifier match {
            case None =>
            case _ => return
          }
        case ScalaCollectionHighlightingLevel.All =>
      }

      Stats.trigger(FeatureKey.collectionPackHighlighting)

      def conformsByNames(tp: ScType, qn: List[String]): Boolean =
        qn.flatMap {
          refElement.elementScope.getCachedClass(_)
        }.map {
          ScalaType.designator
        }.exists {
          tp.conforms
        }

      def simpleAnnotate(@Nls annotationText: String, annotationAttributes: TextAttributesKey): Unit = {
        if (SCALA_FACTORY_METHODS_NAMES.contains(refElement.nameId.getText)) {
          return
        }
        createInfoAnnotation(refElement.nameId, annotationAttributes, annotationText)
      }

      val text = resolvedType.canonicalText
      if (text == null) return

      if (text.startsWith(SCALA_COLLECTION_IMMUTABLE_BASE) || SCALA_PREDEF_IMMUTABLE_BASES.contains(text)) {
        simpleAnnotate(ScalaBundle.message("scala.immutable.collection"), DefaultHighlighter.IMMUTABLE_COLLECTION)
      } else if (text.startsWith(SCALA_COLLECTION_MUTABLE_BASE)) {
        simpleAnnotate(ScalaBundle.message("scala.mutable.collection"), DefaultHighlighter.MUTABLE_COLLECTION)
      } else if (conformsByNames(resolvedType, JAVA_COLLECTIONS_BASES)) {
        simpleAnnotate(ScalaBundle.message("java.collection"), DefaultHighlighter.JAVA_COLLECTION)
      } else if (resolvedType.canonicalText.startsWith(SCALA_COLLECTION_GENERIC_BASE) && refElement.isInstanceOf[ScReferenceExpression]) {
        refElement.asInstanceOf[ScReferenceExpression].`type`().foreach {
          case FunctionType(returnType, _) => Option(returnType).foreach(a =>
            if (a.canonicalText.startsWith(SCALA_COLLECTION_MUTABLE_BASE)) {
              simpleAnnotate(ScalaBundle.message("scala.mutable.collection"), DefaultHighlighter.MUTABLE_COLLECTION)
            } else if (a.canonicalText.startsWith(SCALA_COLLECTION_IMMUTABLE_BASE)) {
              simpleAnnotate(ScalaBundle.message("scala.immutable.collection"), DefaultHighlighter.IMMUTABLE_COLLECTION)
            })
          case _ =>
        }
      }
    }

    def annotateCollection(resolvedClazz: PsiClass): Unit = {
      annotateCollectionByType(ScalaType.designator(resolvedClazz))
    }

    val resolvedElement = refElement.resolve()

    resolvedElement match {
      case c: PsiClass if refElement.parentOfType(classOf[ScImportExpr]).isEmpty =>
        annotateCollection(c)
      case x: ScTypeAlias =>
        x.getOriginalElement match {
          case originalElement: ScTypeAliasDefinition =>
            originalElement.aliasedType.foreach(annotateCollectionByType)
          case _ =>
        }
      case x: ScBindingPattern =>
        x.nameContext match {
          case _: ScValue | _: ScVariable =>
            Option(x.containingClass).foreach(a => if (SCALA_PREDEFINED_OBJECTS.contains(a.qualifiedName)) {
              x.`type`().foreach(annotateCollectionByType)
            })
          case _: ScCaseClause =>
            DefaultHighlighter.PATTERN
          case _: ScGenerator | _: ScForBinding =>
            DefaultHighlighter.GENERATOR
          case _ =>
        }
      case x@(_: ScFunctionDefinition | _: ScFunctionDeclaration | _: ScMacroDefinition) =>
        if (SCALA_FACTORY_METHODS_NAMES.contains(x.asInstanceOf[PsiMethod].getName) || x.asInstanceOf[PsiMethod].isConstructor) {
          x.parentOfType(classOf[PsiClass]).foreach(annotateCollection)
        }
      case x: PsiMethod =>
        if (x.isConstructor) {
          x.parentOfType(classOf[PsiClass]).foreach(annotateCollection)
        }
      case _ =>
    }

  }

  def highlightElement(element: PsiElement)(implicit holder: ScalaAnnotationHolder): Unit = {
    element match {
      case r: ScReference => highlightReferenceElement(r)
      case x: ScAnnotation => visitAnnotation(x)
      case x: ScParameter => visitParameter(x)
      case x: ScCaseClause => visitCaseClause(x)
      case x: ScGenerator => visitGenerator(x)
      case x: ScForBinding => visitForBinding(x)
      case x: ScTypeAlias => visitTypeAlias(x)
      case _ =>
        import ScalaTokenTypes.SOFT_KEYWORDS
        val elementType = element.getNode.getElementType
        if (SOFT_KEYWORDS.contains(elementType)) {
          // TODO: investigate ways to highlight soft keywords in another way
          holder
            .newSilentAnnotation(HighlightSeverity.INFORMATION)
            .textAttributes(DefaultHighlighter.KEYWORD)
            .create()
          createInfoAnnotation(element, DefaultHighlighter.KEYWORD)
        } else if (elementType == ScalaTokenTypes.tIDENTIFIER) {
          getParentByStub(element) match {
            case _: ScNameValuePair =>
              createInfoAnnotation(element, DefaultHighlighter.ANNOTATION_ATTRIBUTE)
            case _: ScTypeParam =>
              createInfoAnnotation(element, DefaultHighlighter.TYPEPARAM)
            case clazz: ScClass =>
              val attributes =
                if (clazz.getModifierList.isAbstract) DefaultHighlighter.ABSTRACT_CLASS
                else DefaultHighlighter.CLASS
              createInfoAnnotation(element, attributes)
            case _: ScObject =>
              createInfoAnnotation(element, DefaultHighlighter.OBJECT)
            case _: ScTrait =>
              createInfoAnnotation(element, DefaultHighlighter.TRAIT)
            case x: ScBindingPattern =>
              x.nameContext match {
                case r@(_: ScValue | _: ScVariable) =>
                  getParentByStub(r) match {
                    case _: ScTemplateBody | _: ScEarlyDefinitions =>
                      val attributes = r match {
                        case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                          DefaultHighlighter.LAZY
                        case _: ScValue => DefaultHighlighter.VALUES
                        case _: ScVariable => DefaultHighlighter.VARIABLES
                        case _ => DefaultLanguageHighlighterColors.IDENTIFIER
                      }
                      createInfoAnnotation(element, attributes)
                    case _ =>
                      val attributes = r match {
                        case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                          DefaultHighlighter.LOCAL_LAZY
                        case _: ScValue => DefaultHighlighter.LOCAL_VALUES
                        case _: ScVariable => DefaultHighlighter.LOCAL_VARIABLES
                        case _ => DefaultLanguageHighlighterColors.IDENTIFIER
                      }
                      createInfoAnnotation(element, attributes)
                  }
                case _: ScCaseClause =>
                  createInfoAnnotation(element, DefaultHighlighter.PATTERN)
                case _: ScGenerator | _: ScForBinding =>
                  createInfoAnnotation(element, DefaultHighlighter.GENERATOR)
                case _ =>
              }
            case _: ScFunctionDefinition | _: ScFunctionDeclaration =>
              createInfoAnnotation(element, DefaultHighlighter.METHOD_DECLARATION)
            case _ =>
          }
        }
    }
  }

  private def visitAnnotation(annotation: ScAnnotation)(implicit holder: ScalaAnnotationHolder): Unit = {
    createInfoAnnotation(annotation.getFirstChild, DefaultHighlighter.ANNOTATION)
    createInfoAnnotation(annotation.annotationExpr.constructorInvocation.typeElement, DefaultHighlighter.ANNOTATION)
  }

  private def visitTypeAlias(typeAlias: ScTypeAlias)(implicit holder: ScalaAnnotationHolder): Unit = {
    createInfoAnnotation(typeAlias.nameId, DefaultHighlighter.TYPE_ALIAS)
  }

  private def visitParameter(param: ScParameter)(implicit holder: ScalaAnnotationHolder): Unit = {
    val nameId = param.nameId

    //in scala 3 parameters may be anonymous and we create synthetic name id for them
    if (!nameId.isPhysical)
      return

    val attributesKey =
      if (param.isAnonymousParameter) DefaultHighlighter.ANONYMOUS_PARAMETER
      else DefaultHighlighter.PARAMETER
    createInfoAnnotation(nameId, attributesKey)

  }

  private def visitPattern(pattern: ScPattern, attribute: TextAttributesKey)(implicit holder: ScalaAnnotationHolder): Unit = {
    for (binding <- pattern.bindings if !binding.isWildcard) {
      createInfoAnnotation(binding.nameId, attribute)
    }
  }

  private def visitCaseClause(clause: ScCaseClause)(implicit holder: ScalaAnnotationHolder): Unit = {
    clause.pattern match {
      case Some(x) => visitPattern(x, DefaultHighlighter.PATTERN)
      case None =>
    }
  }

  private def visitGenerator(generator: ScGenerator)(implicit holder: ScalaAnnotationHolder): Unit = {
    visitPattern(generator.pattern, DefaultHighlighter.GENERATOR)
  }

  private def visitForBinding(forBinding: ScForBinding)(implicit holder: ScalaAnnotationHolder): Unit = {
    visitPattern(forBinding.pattern, DefaultHighlighter.GENERATOR)
  }

  private def referenceIsToCompanionObjectOfClass(r: ScReference): Boolean = {
    Option(r.getContext) exists {
      case _: ScMethodCall | _: ScReferenceExpression => true // These references to 'Foo' should be 'object' references: case class Foo(a: Int); Foo(1); Foo.apply(1).
      case _ => false
    }
  }

  private def createInfoAnnotation(psiElement: PsiElement, attributes: TextAttributesKey, message: String = null)(implicit holder: ScalaAnnotationHolder): Unit = {
    val builder =
      if (message == null) holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      else holder.newAnnotation(HighlightSeverity.INFORMATION, message)
    builder
      .range(psiElement)
      .textAttributes(attributes)
      .create()
  }

  private def isHighlightableScalaTestKeyword(m: ScMember): Boolean = {
    m.containingClass != null &&
      ScalaTestHighlighterUtil.isHighlightableScalaTestKeyword(
        m.containingClass.qualifiedName,
        m.names.headOption.orNull,
        m.getProject
      )
  }
}
