package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionResultSet}
import com.intellij.codeInsight.lookup.{AutoCompletionPolicy, LookupElement, LookupElementPresentation, LookupElementRenderer}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.patterns.ElementPattern
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.{ProcessingContext, Processor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaConstructorInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScExtendsBlock}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.isAccessible
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

/**
  * @author Alefas
  * @since 27.03.12
  */
object ScalaAfterNewCompletionUtil {

  lazy val afterNewPattern: ElementPattern[PsiElement] = ScalaSmartCompletionContributor.superParentsPattern(classOf[ScStableCodeReferenceElement],
    classOf[ScSimpleTypeElement], classOf[ScConstructor], classOf[ScClassParents], classOf[ScExtendsBlock], classOf[ScNewTemplateDefinition])

  def expectedTypesAfterNew(position: PsiElement)
                           (implicit context: ProcessingContext): Option[Array[ScType]] =
  // todo: probably we need to remove all abstracts here according to variance
    if (afterNewPattern.accepts(position, context)) expectedTypes(position).map(_._2)
    else None

  def isAfterNew(position: PsiElement, location: CompletionLocation): Boolean =
    afterNewPattern.accepts(position, location.getProcessingContext)

  def expectedTypes(position: PsiElement): Option[(ScNewTemplateDefinition, Array[ScType])] =
    PsiTreeUtil.getContextOfType(position, classOf[ScNewTemplateDefinition]) match {
      case null => None
      case context =>
        val types = context.expectedTypes().map {
            case ScAbstractType(_, _, upper) => upper
            case tp => tp
          }
        Some(context, types)
    }

  type RenamesMap = Map[String, (PsiNamedElement, String)]

  def createRenamesMap(element: PsiElement): RenamesMap = {
    ScalaPsiUtil.getContextOfType(element, false, classOf[ScReferenceElement]) match {
      case ref: PsiReference =>
        ref.getVariants.flatMap {
          case item: ScalaLookupItem => createRenamePair(item)
          case _ => None
        }.toMap
      case _ => Map.empty
    }
  }

  def createRenamePair(item: ScalaLookupItem): Option[(String, (PsiNamedElement, String))] =
    item.isRenamed.map { name =>
      val element = item.element
      element.name -> (element, name)
    }

  def getLookupElementFromClass(expectedTypes: Array[ScType], clazz: PsiClass,
                                renamesMap: RenamesMap): LookupElement = {
    implicit val ctx: ProjectContext = clazz

    val parameters = clazz.getTypeParameters

    val designatorType = ScDesignatorType(clazz)
    val (predefinedType, noUndefType) = parameters match {
      case Array(head) => (ScParameterizedType(designatorType, Seq(UndefinedType(head))), ScParameterizedType(designatorType, Seq(TypeParameterType(head))))
      case _ => (designatorType, designatorType)
    }

    val iterator = expectedTypes.iterator
    while (iterator.hasNext) {
      val typez = iterator.next()
      val conformance = predefinedType.conforms(typez, ScUndefinedSubstitutor())
      if (conformance._1) {
        conformance._2.getSubstitutor match {
          case Some(subst) =>
            val lookupElement = getLookupElementFromTypeAndClass(clazz, renamesMap, subst.subst(noUndefType))

            parameters.map { p =>
              subst.subst(UndefinedType(p))
            }.foreach {
              case UndefinedType(_, _) =>
                lookupElement.typeParametersProblem = true
              case _ =>
            }

            return lookupElement
          case _ =>
        }
      }
    }
    val lookupElement = getLookupElementFromTypeAndClass(clazz, renamesMap, noUndefType)
    if (parameters.nonEmpty) {
      lookupElement.typeParametersProblem = true
    }
    lookupElement
  }

  private def getLookupElementFromTypeAndClass(clazz: PsiClass,
                                               renamesMap: RenamesMap,
                                               `type`: ScType,
                                               substitutor: ScSubstitutor = ScSubstitutor.empty) = {
    val name = clazz.name
    val isRenamed = renamesMap.get(name).collect {
      case (`clazz`, s) => s
    }

    val isInterface = clazz match {
      case _: ScTrait => true
      case _ => clazz.isInterface || clazz.hasModifierPropertyScala("abstract")
      case _ => false
    }

    val typeParameters = `type` match {
      case ParameterizedType(_, types) => types
      case _ => Seq.empty
    }

    val renderer = new LookupElementRenderer[LookupElement] {

      def renderElement(ignore: LookupElement, presentation: LookupElementPresentation) {
        val tailText = if (isInterface) "{...} " else ""
        presentation.setTailText(" " + tailText + clazz.getPresentation.getLocationString, true)
        presentation.setIcon(clazz.getIcon(0))

        val isDeprecated = clazz match {
          case owner: PsiDocCommentOwner => owner.isDeprecated
          case _ => false
        }
        presentation.setStrikeout(isDeprecated)

        val nameText = isRenamed match {
          case Some(nameShadow) => s"$nameShadow <= $name"
          case _ => name
        }
        val parametersText = typeParameters match {
          case Seq() => ""
          case seq => seq.map(substitutor.subst)
            .map(_.presentableText)
            .mkString("[", ", ", "]")

        }
        presentation.setItemText(nameText + parametersText)
      }
    }

    val result = new ScalaLookupItem(clazz, isRenamed.getOrElse(name)) {

      override def renderElement(presentation: LookupElementPresentation): Unit =
        renderer.renderElement(this, presentation)
    }
    result.isRenamed = isRenamed
    result.typeParameters = typeParameters
    result.prefixCompletion = ScalaCodeStyleSettings.getInstance(clazz.getProject)
      .hasImportWithPrefix(clazz.qualifiedName)
    result.setInsertHandler(new ScalaConstructorInsertHandler)

    val maybePolicy = {
      import AutoCompletionPolicy._
      if (ApplicationManager.getApplication.isUnitTestMode) Some(ALWAYS_AUTOCOMPLETE)
      else if (isInterface) Some(NEVER_AUTOCOMPLETE)
      else None
    }
    maybePolicy.foreach(result.setAutoCompletionPolicy)

    result
  }

  def convertTypeToLookupElement(tp: ScType, place: PsiElement,
                                 addedClasses: mutable.HashSet[String],
                                 renamesMap: RenamesMap): Option[ScalaLookupItem] = {
    val names = Set("scala.Boolean",
      "scala.Byte", "scala.Short", "scala.Int", "scala.Long",
      "scala.Float", "scala.Double",
      "scala.AnyVal", "scala.Char", "scala.Unit", "scala.Any")

    // filter base types (it's important for scala 2.9)
    // todo: filter inner classes smarter (how? don't forget deep inner classes)
    def isValid(clazz: PsiClass) = !names.contains(clazz.qualifiedName) && (clazz.containingClass match {
      case null => true
      case _: ScObject => !clazz.hasModifierPropertyScala("static")
      case _ => false
    })

    tp.extractClassType.flatMap {
      case (_: ScObject, _) => None
      case (clazz: PsiClass, substitutor: ScSubstitutor)
        if isValid(clazz) && isAccessible(clazz, place, forCompletion = true) =>
        if (addedClasses.add(clazz.qualifiedName)) Some(getLookupElementFromTypeAndClass(clazz, renamesMap, tp, substitutor))
        else None
      case _ => None
    }
  }

  def collectInheritorsForType(typez: ScType, place: PsiElement, addedClasses: mutable.HashSet[String],
                               result: CompletionResultSet,
                               renamesMap: RenamesMap) {
    implicit val project = place.getProject

    typez.extractClass match {
      case Some(clazz) =>
        //this change is important for Scala Worksheet/Script classes. Will not find inheritors, due to file copy.
        val searchScope =
          if (clazz.getUseScope.isInstanceOf[LocalSearchScope]) GlobalSearchScope.allScope(project)
          else clazz.getUseScope
        ClassInheritorsSearch.search(clazz, searchScope, true).forEach(new Processor[PsiClass] {
          def process(clazz: PsiClass): Boolean = {
            if (clazz.name == null || clazz.name == "") return true
            val undefines = clazz.getTypeParameters.map(UndefinedType(_))
            val predefinedType =
              if (clazz.getTypeParameters.nonEmpty) {
                ScParameterizedType(ScDesignatorType(clazz), undefines)
              }
              else ScDesignatorType(clazz)
            val noUndefType =
              if (clazz.getTypeParameters.nonEmpty) {
                ScParameterizedType(ScDesignatorType(clazz), clazz.getTypeParameters.map(TypeParameterType(_)))
              }
              else ScDesignatorType(clazz)
            val conformance = predefinedType.conforms(typez, ScUndefinedSubstitutor())
            if (!conformance._1) return true
            conformance._2.getSubstitutor match {
              case Some(undefSubst) =>
                convertTypeToLookupElement(undefSubst.subst(noUndefType), place, addedClasses, renamesMap).foreach { lookupElement =>
                  lookupElement.typeParametersProblem = undefines.map(undefSubst.subst)
                    .exists(_.isInstanceOf[UndefinedType])

                  result.addElement(lookupElement)
                }
              case _ =>
            }
            true
          }
        })
      case _ =>
    }
  }
}
