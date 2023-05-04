package org.jetbrains.plugins.scala.highlighter

import com.intellij.lang.annotation.{AnnotationHolder, Annotator, HighlightSeverity}
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.annotationHolder.ScalaAnnotationHolderAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, ScalaType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
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

  def highlightReferenceElement(refElement: ScReference)(implicit holder: ScalaAnnotationHolder): Unit = {
    annotateCollectionType(refElement)

    if (
      !refElement.parentOfType(classOf[ScConstructorInvocation], strict = false).exists(_.getParent.is[ScAnnotationExpr])
    ) {
      val attributes = textAttributesKey(refElement)
      createInfoAnnotation(refElement.nameId, attributes)
    }
  }

  private def textAttributesKey(refElement: ScReference): TextAttributesKey =
    ScalaColorsSchemeUtils.textAttributesKey(
      refElement.resolve(),
      Some(refElement),
      refElement.projectContext.stdTypes.QualNameToType
    )

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

  def highlightElement(element: PsiElement)(implicit holder: ScalaAnnotationHolder): Unit =
    element match {
      case r: ScReference  => highlightReferenceElement(r)
      case x: ScAnnotation => visitAnnotation(x)
      case x: ScParameter  => visitParameter(x)
      case x: ScCaseClause => visitCaseClause(x)
      case _ =>
        ScalaColorsSchemeUtils.highlightElement(element) match {
          case (_, true) =>
            // TODO: investigate ways to highlight soft keywords in another way
            holder
              .newSilentAnnotation(HighlightSeverity.INFORMATION)
              .textAttributes(DefaultHighlighter.KEYWORD)
              .create()
            createInfoAnnotation(element, DefaultHighlighter.KEYWORD)
          case (Some(key), _) =>
            createInfoAnnotation(element, key)
          case _ =>
        }
    }

  private def visitAnnotation(annotation: ScAnnotation)(implicit holder: ScalaAnnotationHolder): Unit = {
    createInfoAnnotation(annotation.getFirstChild, DefaultHighlighter.ANNOTATION)
    createInfoAnnotation(annotation.annotationExpr.constructorInvocation.typeElement, DefaultHighlighter.ANNOTATION)
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

  private def createInfoAnnotation(psiElement: PsiElement, attributes: TextAttributesKey, message: String = null)
                                  (implicit holder: ScalaAnnotationHolder): Unit = {
    val builder =
      if (message == null)
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      else
        holder.newAnnotation(HighlightSeverity.INFORMATION, message)
    builder
      .range(psiElement)
      .textAttributes(attributes)
      .create()
  }
}
