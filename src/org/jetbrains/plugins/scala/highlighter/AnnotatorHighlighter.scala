package org.jetbrains.plugins.scala
package highlighter

import _root_.org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner}
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi._
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager.ClassCategory
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, StdType, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, ScalaType}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScalaStubBasedElementImpl}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.07.2008
 */

object AnnotatorHighlighter {
  private val JAVA_COLLECTIONS_BASES = List("java.util.Map", "java.util.Collection")
  private val SCALA_FACTORY_METHODS_NAMES = Set("make", "apply")
  private val SCALA_COLLECTION_MUTABLE_BASE = "_root_.scala.collection.mutable."
  private val SCALA_COLLECTION_IMMUTABLE_BASE = "_root_.scala.collection.immutable."
  private val SCALA_COLLECTION_GENERIC_BASE = "_root_.scala.collection.generic."
  private val SCALA_PREDEFINED_OBJECTS = Set("scala", "scala.Predef")
  private val SCALA_PREDEF_IMMUTABLE_BASES = Set("_root_.scala.PredefMap", "_root_.scala.PredefSet", "scalaList",
    "scalaNil", "scalaStream", "scalaVector", "scalaSeq")


  private def getParentStub(el: StubBasedPsiElement[_ <: StubElement[_ <: PsiElement]]): PsiElement = {
    val stub: StubElement[_ <: PsiElement] = el.getStub
    if (stub != null) {
      stub.getParentStub.getPsi
    } else el.getParent
  }

  private def getParentByStub(x: PsiElement): PsiElement = {
    x match {
      case el: ScalaStubBasedElementImpl[_] => getParentStub(el)
      case _ => x.getContext
    }
  }

  def highlightReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder)
                               (implicit typeSystem: TypeSystem = refElement.typeSystem) {

    def annotateCollectionByType(resolvedType: ScType) {
      if (ScalaNamesUtil.isOperatorName(
        resolvedType.presentableText.substring(0, resolvedType.presentableText.prefixLength(_ != '.')))) return

      val scalaProjectSettings: ScalaProjectSettings = ScalaProjectSettings.getInstance(refElement.getProject)

      scalaProjectSettings.getCollectionTypeHighlightingLevel match {
        case ScalaProjectSettings.COLLECTION_TYPE_HIGHLIGHTING_NONE => return
        case ScalaProjectSettings.COLLECTION_TYPE_HIGHLIGHTING_NOT_QUALIFIED =>
          refElement.qualifier match {
            case None =>
            case _ => return
          }
        case ScalaProjectSettings.COLLECTION_TYPE_HIGHLIGHTING_ALL =>
      }

      UsageTrigger.trigger("scala.collection.pack.highlighting")

      def conformsByNames(tp: ScType, qn: List[String]): Boolean = {
        val manager = ScalaPsiManager.instance(refElement.getProject)
        val resolveScope = refElement.getResolveScope
        qn.flatMap { name =>
          manager.getCachedClass(name, resolveScope, ClassCategory.TYPE)
        }.map(ScalaType.designator)
          .exists(tp.conforms)
      }

      def simpleAnnotate(annotationText: String, annotationAttributes: TextAttributesKey) {
        if (SCALA_FACTORY_METHODS_NAMES.contains(refElement.nameId.getText)) {
          return
        }
        val annotation = holder.createInfoAnnotation(refElement.nameId, annotationText)
        annotation.setTextAttributes(annotationAttributes)
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
        refElement.asInstanceOf[ScReferenceExpression].getType(TypingContext.empty).foreach {
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

    def annotateCollection(resolvedClazz: PsiClass) {
      annotateCollectionByType(ScalaType.designator(resolvedClazz))
    }

    def isHighlightableScalaTestKeyword(fun: ScMember): Boolean = {
      fun.getContainingClass != null &&
        ScalaTestHighlighterUtil.isHighlightableScalaTestKeyword(fun.getContainingClass.getQualifiedName, fun match {
          case p: ScPatternDefinition => p.bindings.headOption.map(_.getName).orNull
          case _ => fun.getName
        }, fun.getProject)
    }

    val c = ScalaPsiUtil.getParentOfType(refElement, classOf[ScConstructor])

    if (c != null && c.getParent.isInstanceOf[ScAnnotationExpr]) return

    val resolvedElement = refElement.resolve()
    if (PsiTreeUtil.getParentOfType(refElement, classOf[ScImportExpr]) == null && resolvedElement.isInstanceOf[PsiClass]) {
      annotateCollection(resolvedElement.asInstanceOf[PsiClass])
    }

    val annotation = holder.createInfoAnnotation(refElement.nameId, null)
     resolvedElement match {
       case c: PsiClass if StdType.QualNameToType.contains(c.qualifiedName) => //this is td, it's important!
        annotation.setTextAttributes(DefaultHighlighter.PREDEF)
      case x: ScClass if x.getModifierList.has(ScalaTokenTypes.kABSTRACT) =>
        annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
      case _: ScTypeParam =>
        annotation.setTextAttributes(DefaultHighlighter.TYPEPARAM)
      case x: ScTypeAlias =>
        x.getOriginalElement match {
          case originalElement: ScTypeAliasDefinition =>
            originalElement.aliasedType.foreach(annotateCollectionByType)
          case _ =>
        }
        annotation.setTextAttributes(DefaultHighlighter.TYPE_ALIAS)
      case _: ScClass if referenceIsToCompanionObjectOfClass(refElement) =>
        annotation.setTextAttributes(DefaultHighlighter.OBJECT)
      case _: ScClass =>
        annotation.setTextAttributes(DefaultHighlighter.CLASS)
      case _: ScObject =>
        annotation.setTextAttributes(DefaultHighlighter.OBJECT)
      case _: ScTrait =>
        annotation.setTextAttributes(DefaultHighlighter.TRAIT)
      case x: PsiClass if x.isInterface =>
        annotation.setTextAttributes(DefaultHighlighter.TRAIT)
      case x: PsiClass if x.getModifierList != null && x.getModifierList.hasModifierProperty("abstract") =>
        annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
      case _: PsiClass if refElement.isInstanceOf[ScStableCodeReferenceElement] =>
        annotation.setTextAttributes(DefaultHighlighter.CLASS)
      case _: PsiClass if refElement.isInstanceOf[ScReferenceExpression] =>
        annotation.setTextAttributes(DefaultHighlighter.OBJECT)
      case x: ScBindingPattern =>
        val parent = x.nameContext
        parent match {
          case r@(_: ScValue | _: ScVariable) =>
            Option(x.containingClass).foreach(a => if (SCALA_PREDEFINED_OBJECTS.contains(a.qualifiedName)) {
              x.getType(TypingContext.empty).foreach(annotateCollectionByType)
            })

            getParentByStub(parent) match {
              case _: ScTemplateBody | _: ScEarlyDefinitions =>
                r match {
                  case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                    annotation.setTextAttributes(DefaultHighlighter.LAZY)
                  case v: ScValue if isHighlightableScalaTestKeyword(v) =>
                    annotation.setTextAttributes(DefaultHighlighter.SCALATEST_KEYWORD)
                  case _: ScValue => annotation.setTextAttributes(DefaultHighlighter.VALUES)
                  case _: ScVariable => annotation.setTextAttributes(DefaultHighlighter.VARIABLES)
                  case _ =>
                }
              case _ =>
                r match {
                  case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                    annotation.setTextAttributes(DefaultHighlighter.LOCAL_LAZY)
                  case _: ScValue => annotation.setTextAttributes(DefaultHighlighter.LOCAL_VALUES)
                  case _: ScVariable => annotation.setTextAttributes(DefaultHighlighter.LOCAL_VARIABLES)
                  case _ =>
                }
            }
          case _: ScCaseClause =>
            annotation.setTextAttributes(DefaultHighlighter.PATTERN)
          case _: ScGenerator | _: ScEnumerator =>
            annotation.setTextAttributes(DefaultHighlighter.GENERATOR)
          case _ =>
        }
      case x: PsiField =>
        if (!x.hasModifierProperty("final")) annotation.setTextAttributes(DefaultHighlighter.VARIABLES)
        else annotation.setTextAttributes(DefaultHighlighter.VALUES)
      case x: ScParameter if x.isAnonymousParameter => annotation.setTextAttributes(DefaultHighlighter.ANONYMOUS_PARAMETER)
      case _: ScParameter => annotation.setTextAttributes(DefaultHighlighter.PARAMETER)
      case x@(_: ScFunctionDefinition | _: ScFunctionDeclaration | _: ScMacroDefinition) =>
        if (SCALA_FACTORY_METHODS_NAMES.contains(x.asInstanceOf[PsiMethod].getName) || x.asInstanceOf[PsiMethod].isConstructor) {
          val clazz = PsiTreeUtil.getParentOfType(x, classOf[PsiClass])
          if (clazz != null) {
            annotateCollection(clazz)
          }
        }
        if (isHighlightableScalaTestKeyword(x.asInstanceOf[ScFunction])) {
          annotation.setTextAttributes(DefaultHighlighter.SCALATEST_KEYWORD)
        } else {
          val fun = x.asInstanceOf[ScFunction]
          val clazz = fun.containingClass
          clazz match {
            case o: ScObject if o.allSynthetics.contains(fun) =>
              annotation.setTextAttributes(DefaultHighlighter.OBJECT_METHOD_CALL)
              return
            case _ =>
          }
          getParentByStub(x) match {
            case _: ScTemplateBody | _: ScEarlyDefinitions =>
              getParentByStub(getParentByStub(getParentByStub(x))) match {
                case _: ScClass | _: ScTrait =>
                  annotation.setTextAttributes(DefaultHighlighter.METHOD_CALL)
                case _: ScObject =>
                  annotation.setTextAttributes(DefaultHighlighter.OBJECT_METHOD_CALL)
                case _ =>
              }
            case _ =>
              annotation.setTextAttributes(DefaultHighlighter.LOCAL_METHOD_CALL)
          }
        }
      case x: PsiMethod =>
        if (x.isConstructor) {
          val clazz: PsiClass = PsiTreeUtil.getParentOfType(x, classOf[PsiClass])
          if (clazz != null) annotateCollection(clazz)
        }
        if (x.getModifierList != null && x.getModifierList.hasModifierProperty("static")) {
          annotation.setTextAttributes(DefaultHighlighter.OBJECT_METHOD_CALL)
        } else {
          annotation.setTextAttributes(DefaultHighlighter.METHOD_CALL)
        }
      case _ => //println("" + x + " " + x.getText)
    }
  }

  def highlightElement(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case x: ScAnnotation => visitAnnotation(x, holder)
      case x: ScParameter => visitParameter(x, holder)
      case x: ScCaseClause => visitCaseClause(x, holder)
      case x: ScGenerator => visitGenerator(x, holder)
      case x: ScEnumerator => visitEnumerator(x, holder)
      case x: ScTypeAlias => visitTypeAlias(x, holder)
      case _ if element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER =>
        getParentByStub(element) match {
          case _: ScNameValuePair =>
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.ANNOTATION_ATTRIBUTE)
          case _: ScTypeParam =>
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.TYPEPARAM)
          case clazz: ScClass =>
            if (clazz.getModifierList.has(ScalaTokenTypes.kABSTRACT)) {
              val annotation = holder.createInfoAnnotation(clazz.nameId, null)
              annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
            } else {
              val annotation = holder.createInfoAnnotation(clazz.nameId, null)
              annotation.setTextAttributes(DefaultHighlighter.CLASS)
            }
          case _: ScObject =>
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.OBJECT)
          case _: ScTrait =>
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.TRAIT)
          case x: ScBindingPattern =>
            x.nameContext match {
              case r@(_: ScValue | _: ScVariable) =>
                getParentByStub(r) match {
                  case _: ScTemplateBody | _: ScEarlyDefinitions =>
                    val annotation = holder.createInfoAnnotation(element, null)
                    r match {
                      case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                        annotation.setTextAttributes(DefaultHighlighter.LAZY)
                      case _: ScValue => annotation.setTextAttributes(DefaultHighlighter.VALUES)
                      case _: ScVariable => annotation.setTextAttributes(DefaultHighlighter.VARIABLES)
                      case _ =>
                    }
                  case _ =>
                    val annotation = holder.createInfoAnnotation(element, null)
                    r match {
                      case mod: ScModifierListOwner if mod.hasModifierProperty("lazy") =>
                        annotation.setTextAttributes(DefaultHighlighter.LOCAL_LAZY)
                      case _: ScValue => annotation.setTextAttributes(DefaultHighlighter.LOCAL_VALUES)
                      case _: ScVariable => annotation.setTextAttributes(DefaultHighlighter.LOCAL_VARIABLES)
                      case _ =>
                    }
                }
              case _: ScCaseClause =>
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.PATTERN)
              case _: ScGenerator | _: ScEnumerator =>
                val annotation = holder.createInfoAnnotation(element, null)
                annotation.setTextAttributes(DefaultHighlighter.GENERATOR)
              case _ =>
            }
          case _: ScFunctionDefinition | _: ScFunctionDeclaration =>
            val annotation = holder.createInfoAnnotation(element, null)
            annotation.setTextAttributes(DefaultHighlighter.METHOD_DECLARATION)
          case _ =>
        }
      case _ =>
    }
  }

  private def visitAnnotation(annotation: ScAnnotation, holder: AnnotationHolder): Unit = {
    val annotation1 = holder.createInfoAnnotation(annotation.getFirstChild, null)
    annotation1.setTextAttributes(DefaultHighlighter.ANNOTATION)
    val element = annotation.annotationExpr.constr.typeElement
    val annotation2 = holder.createInfoAnnotation(element, null)
    annotation2.setTextAttributes(DefaultHighlighter.ANNOTATION)
  }

  private def visitTypeAlias(typeAlias: ScTypeAlias, holder: AnnotationHolder): Unit = {
    val annotation = holder.createInfoAnnotation(typeAlias.nameId, null)
    annotation.setTextAttributes(DefaultHighlighter.TYPE_ALIAS)
  }

  private def visitClass(clazz: ScClass, holder: AnnotationHolder): Unit = {
    if (clazz.getModifierList.has(ScalaTokenTypes.kABSTRACT)) {
      val annotation = holder.createInfoAnnotation(clazz.nameId, null)
      annotation.setTextAttributes(DefaultHighlighter.ABSTRACT_CLASS)
    } else {
      val annotation = holder.createInfoAnnotation(clazz.nameId, null)
      annotation.setTextAttributes(DefaultHighlighter.CLASS)
    }
  }

  private def visitParameter(param: ScParameter, holder: AnnotationHolder): Unit = {
    val annotation = holder.createInfoAnnotation(param.nameId, null)
    val attributesKey =
      if (param.isAnonymousParameter) DefaultHighlighter.ANONYMOUS_PARAMETER
      else DefaultHighlighter.PARAMETER
    annotation.setTextAttributes(attributesKey)
  }

  private def visitPattern(pattern: ScPattern, holder: AnnotationHolder, attribute: TextAttributesKey): Unit = {
    for (binding <- pattern.bindings if !binding.isWildcard) {
      val annotation = holder.createInfoAnnotation(binding.nameId, null)
      annotation.setTextAttributes(attribute)
    }
  }

  private def visitCaseClause(clause: ScCaseClause, holder: AnnotationHolder): Unit = {
    clause.pattern match {
      case Some(x) => visitPattern(x, holder, DefaultHighlighter.PATTERN)
      case None =>
    }
  }

  private def visitGenerator(generator: ScGenerator, holder: AnnotationHolder): Unit = {
    visitPattern(generator.pattern, holder, DefaultHighlighter.GENERATOR)
  }

  private def visitEnumerator(enumerator: ScEnumerator, holder: AnnotationHolder): Unit = {
    visitPattern(enumerator.pattern, holder, DefaultHighlighter.GENERATOR)
  }
  
  private def referenceIsToCompanionObjectOfClass(r: ScReferenceElement): Boolean = {
    Option(r.getContext) exists {
      case _: ScMethodCall | _: ScReferenceExpression => true // These references to 'Foo' should be 'object' references: case class Foo(a: Int); Foo(1); Foo.apply(1).
      case _ => false
    }
  }
}
