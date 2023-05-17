package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInspection.util.InspectionMessage
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
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, ScalaType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.ScalaCollectionHighlightingLevel
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
 * See also `org.jetbrains.plugins.scala.codeInsight.daemon.ScalaRainbowVisitor`
 */
final class ScalaColorSchemeAnnotator extends Annotator {
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
  private val SCALA_PREDEFINED_OBJECTS = Set("scala", "scala.Predef")
  private val SCALA_PREDEF_IMMUTABLE_BASES = Set(
    "_root_.scala.PredefMap",
    "_root_.scala.PredefSet",
    "scalaList",
    "scalaNil",
    "scalaStream",
    "scalaVector",
    "scalaSeq"
  )

  private def highlightReferenceElement(refElement: ScReference)(implicit holder: ScalaAnnotationHolder): Unit = {
    val multiResolveResult = refElement.multiResolveScala(false)
    lazy val resolvedElement =
      if (multiResolveResult.length == 1) multiResolveResult.head.getActualElement
      else null

    annotateCollectionType(refElement, resolvedElement)

    val someCondition = refElement.parentOfType(classOf[ScConstructorInvocation], strict = false).exists(_.getParent.is[ScAnnotationExpr])
    if (!someCondition) {
      val attributes = textAttributesKey(refElement, resolvedElement)
      createInfoAnnotation(refElement.nameId, attributes)
    }
  }

  private def textAttributesKey(refElement: ScReference, resolvedElement: PsiNamedElement): TextAttributesKey =
    ScalaColorsSchemeUtils.textAttributesKey(
      resolvedElement,
      Some(refElement),
      refElement.projectContext.stdTypes.QualNameToType
    )

  //See tests in [[org.jetbrains.plugins.scala.annotator.CollectionByTypeAnnotatorTestBase]]
  private def annotateCollectionType(
    refElement: ScReference,
    resolvedElement: => PsiElement
  )(implicit holder: ScalaAnnotationHolder): Unit = {
    def annotateCollectionByType(resolvedType: ScType): Unit = {
      val resolvedTypeName = resolvedType.presentableText(TypePresentationContext.emptyContext)

      val isOperator = ScalaNamesUtil.isOperatorName(resolvedTypeName.substring(0, resolvedTypeName.segmentLength(_ != '.')))
      if (isOperator)
        return

      val scalaProjectSettings: ScalaProjectSettings = ScalaProjectSettings.getInstance(refElement.getProject)

      val scalaCollectionTypeHighlightingLevel = scalaProjectSettings.getCollectionTypeHighlightingLevel
      scalaCollectionTypeHighlightingLevel match {
        case ScalaCollectionHighlightingLevel.None =>
          return
        case ScalaCollectionHighlightingLevel.OnlyNonQualified =>
          refElement.qualifier match {
            case None =>
            case _ =>
              return
          }
        case ScalaCollectionHighlightingLevel.All =>
      }

      Stats.trigger(FeatureKey.collectionPackHighlighting)

      def conformsByNames(tp: ScType, fqns: List[String]): Boolean = {
        val cachedClasses = fqns.flatMap(refElement.elementScope.getCachedClass)
        val types = cachedClasses.map(ScalaType.designator)
        val typesConforming = types.exists(tp.conforms)
        typesConforming
      }

      def simpleAnnotate(@Nls annotationText: String, annotationAttributes: TextAttributesKey): Unit = {
        if (SCALA_FACTORY_METHODS_NAMES.contains(refElement.nameId.getText)) {
          return
        }
        createInfoAnnotation(refElement.nameId, annotationAttributes, annotationText)
      }

      val typeText = resolvedType.canonicalText
      if (typeText == null) return

      if (typeText.startsWith(SCALA_COLLECTION_IMMUTABLE_BASE) || SCALA_PREDEF_IMMUTABLE_BASES.contains(typeText)) {
        simpleAnnotate(ScalaBundle.message("scala.immutable.collection"), DefaultHighlighter.IMMUTABLE_COLLECTION)
      }
      else if (typeText.startsWith(SCALA_COLLECTION_MUTABLE_BASE)) {
        simpleAnnotate(ScalaBundle.message("scala.mutable.collection"), DefaultHighlighter.MUTABLE_COLLECTION)
      }
      else if (conformsByNames(resolvedType, JAVA_COLLECTIONS_BASES)) {
        simpleAnnotate(ScalaBundle.message("java.collection"), DefaultHighlighter.JAVA_COLLECTION)
      }
    }

    resolvedElement match {
      case c: PsiClass =>
        val isInImport = refElement.parentOfType(classOf[ScImportExpr]).nonEmpty
        if (isInImport) {
          //skip, do not annotate collection references inside import
        } else {
          annotateCollectionByType(ScalaType.designator(c))
        }
      case alias: ScTypeAlias =>
        alias.getOriginalElement match {
          case originalElement: ScTypeAliasDefinition =>
            originalElement.aliasedType.foreach(annotateCollectionByType)
          case _ =>
        }
      case x: ScBindingPattern =>
        x.nameContext match {
          case _: ScValueOrVariable =>
            //when we use `Map()`, `List` actually is referenced to `immutable.Map` in `scala.Predef`, so we need to dereference it
            //NOTE: it's automatically done during resolve if
            //org.jetbrains.plugins.scala.settings.ScalaProjectSettings.getAliasSemantics ise set to EXPORT (by default)
            Option(x.containingClass).foreach { c =>
              if (SCALA_PREDEFINED_OBJECTS.contains(c.qualifiedName)) {
                x.`type`().foreach(annotateCollectionByType)
              }
            }
          case _ =>
        }
      case _ =>
    }
  }

  def highlightElement(element: PsiElement)(implicit holder: ScalaAnnotationHolder): Unit =
    element match {
      case r: ScReference  => highlightReferenceElement(r)
      case x: ScAnnotation => visitAnnotation(x)
      case x: ScParameter  => visitParameter(x)
      case x: ScTypeAlias  => visitTypeAlias(x)
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

  private def createInfoAnnotation(
    psiElement: PsiElement,
    attributes: TextAttributesKey,
    @InspectionMessage message: String = null
  )(implicit holder: ScalaAnnotationHolder): Unit = {
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
