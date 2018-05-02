package org.jetbrains.plugins.scala.lang
package completion

import java.{util => ju}

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
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
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.{JavaConverters, mutable}

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

  private[this] def expectedTypes(position: PsiElement): Option[(ScNewTemplateDefinition, Array[ScType])] =
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

  def getLookupElementFromClass(expectedTypes: Array[ScType],
                                clazz: PsiClass,
                                renamesMap: RenamesMap): ScalaLookupItem = {
    implicit val context: ProjectContext = clazz

    val (designatorType, parameters) = classComponents(clazz)
    val maybeParameter = parameters match {
      case Array(head) => Some(head)
      case _ => None
    }

    val (actualType, hasSubstitutionProblem) = findAppropriateType(expectedTypes: _*)(designatorType, maybeParameter) match {
      case Some((scType, flag)) => (scType, flag)
      case _ => (fromParameters(designatorType, maybeParameter), parameters.nonEmpty)
    }

    lookupElement(clazz, renamesMap)(actualType, hasSubstitutionProblem = hasSubstitutionProblem)
  }

  private case class LookupElementProps(clazz: PsiClass,
                                        `type`: ScType,
                                        substitutor: ScSubstitutor,
                                        hasSubstitutionProblem: Boolean) {

    def createLookupElement(renamesMap: RenamesMap): ScalaLookupItem =
      lookupElement(clazz, renamesMap)(`type`, substitutor, hasSubstitutionProblem)
  }

  def lookupsAfterNew(position: PsiElement): ju.List[ScalaLookupItem] = expectedTypes(position) match {
    case Some((definition, types)) =>
      implicit val project: Project = definition.getProject

      val propses = types.toSeq.flatMap(collectProps).filter {
        case LookupElementProps(clazz, _, _, _) => ResolveUtils.isAccessible(clazz, definition, forCompletion = true)
      }

      val items = if (propses.nonEmpty) {
        val renamesMap = createRenamesMap(position)
        propses.map(_.createLookupElement(renamesMap))
      } else Seq.empty

      import JavaConverters._
      items.asJava
    case _ => ju.Collections.emptyList()
  }

  private[this] def collectProps(`type`: ScType)
                                (implicit project: Project): Seq[LookupElementProps] = {
    val inheritors = `type`.extractClass.toSeq.flatMap { clazz =>
      // this change is important for Scala Worksheet/Script classes. Will not find inheritors, due to file copy.
      val searchScope = clazz.getUseScope match {
        case _: LocalSearchScope => GlobalSearchScope.allScope(project)
        case useScope => useScope
      }

      import JavaConverters._
      ClassInheritorsSearch.search(clazz, searchScope, true).asScala
    }.filter { clazz =>
      clazz.name match {
        case null | "" => false
        case _ => true
      }
    }

    val substitutedInheritors = for {
      clazz <- inheritors
      (designatorType, parameters) = classComponents(clazz)
      (actualType, hasSubstitutionProblem) <- findAppropriateType(`type`)(designatorType, parameters)
    } yield (actualType, hasSubstitutionProblem)

    val addedClasses = mutable.HashSet.empty[String]
    for {
      (actualType, hasSubstitutionProblem) <- (`type`, false) +: substitutedInheritors
      (extractedClass, extractedSubstitutor) <- extractValidClass(actualType)
      if addedClasses.add(extractedClass.qualifiedName)
    } yield LookupElementProps(extractedClass, actualType, extractedSubstitutor, hasSubstitutionProblem)
  }

  private[this] def lookupElement(clazz: PsiClass, renamesMap: RenamesMap)
                                 (`type`: ScType,
                                  substitutor: ScSubstitutor = ScSubstitutor.empty,
                                  hasSubstitutionProblem: Boolean = false): ScalaLookupItem = {
    val name = clazz.name
    val isRenamed = renamesMap.get(name).collect {
      case (`clazz`, s) => s
    }

    val isInterface = clazz match {
      case _: ScTrait => true
      case _ => clazz.isInterface || clazz.hasModifierPropertyScala("abstract")
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
    result.typeParametersProblem = hasSubstitutionProblem
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

  private[this] def extractValidClass(`type`: ScType): Option[(PsiClass, ScSubstitutor)] = {
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

    `type`.extractClassType.flatMap {
      case (_: ScObject, _) => None
      case (clazz: PsiClass, substitutor: ScSubstitutor) if isValid(clazz) => Some((clazz, substitutor))
      case _ => None
    }
  }

  private[this] def classComponents(clazz: PsiClass): (ScDesignatorType, Array[PsiTypeParameter]) =
    (ScDesignatorType(clazz), clazz.getTypeParameters)

  private[this] def findAppropriateType(types: ScType*)
                                       (designatorType: ScDesignatorType, parameters: Traversable[PsiTypeParameter])
                                       (implicit context: ProjectContext): Option[(ScType, Boolean)] = {
    if (types.isEmpty) return None

    val undefinedTypes = parameters.map(UndefinedType(_))
    val predefinedType = fromParametersTypes(designatorType, undefinedTypes)

    val iterator = types.iterator
    while (iterator.hasNext) {
      predefinedType.conforms(iterator.next(), ScUndefinedSubstitutor()) match {
        case (true, undefinedSubstitutor) =>
          undefinedSubstitutor.getSubstitutor match {
            case Some(substitutor) =>
              def hasSubstitutionProblem: UndefinedType => Boolean =
                substitutor.subst(_).isInstanceOf[UndefinedType]

              val valueType = fromParameters(designatorType, parameters)
              return Some(substitutor.subst(valueType), undefinedTypes.exists(hasSubstitutionProblem))
            case _ =>
          }
        case _ =>
      }
    }

    None
  }

  private[this] def fromParameters(designatorType: ScDesignatorType, parameters: Traversable[PsiTypeParameter]): ValueType =
    fromParametersTypes(designatorType, parameters.map(TypeParameterType(_)))

  private[this] def fromParametersTypes(designatorType: ScDesignatorType, types: Traversable[ScType]): ValueType =
    if (types.isEmpty) designatorType else ScParameterizedType(designatorType, types.toSeq)
}
