package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaConstructorInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.{PresentationExt, ScalaLookupItem}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

import scala.collection.mutable
import scala.jdk.CollectionConverters._

final class ScalaAfterNewCompletionContributor extends ScalaCompletionContributor {

  import ScalaAfterNewCompletionContributor._

  extend(
    CompletionType.SMART,
    afterNewKeywordPattern,
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  result: CompletionResultSet): Unit = {
        val place = positionFromParameters(parameters)
        val (definition, types) = expectedTypes(place)

        val propses = for {
          expectedType <- types
          prop <- collectProps(expectedType) {
            isAccessible(_)(place)
          }(definition.getProject)
        } yield prop

        if (propses.nonEmpty) {
          val renamesMap = createRenamesMap(place)

          for {
            prop <- propses
            lookupItem = prop.createLookupElement(renamesMap)
          } result.addElement(lookupItem)
        }
      }
    }
  )

}

object ScalaAfterNewCompletionContributor {

  def expectedTypeAfterNew(place: PsiElement, context: ProcessingContext): Option[PropsConstructor] =
  // todo: probably we need to remove all abstracts here according to variance
    if (afterNewKeywordPattern.accepts(place, context))
      Some {
        val (_, types) = expectedTypes(place)
        (clazz: PsiClass) => {
          val (actualType, hasSubstitutionProblem) = appropriateType(clazz, types)
          LookupElementProps(actualType, hasSubstitutionProblem, clazz)
        }
      }
    else
      None

  private def expectedTypes(place: PsiElement): (ScNewTemplateDefinition, Seq[ScType]) = {
    val definition = getContextOfType(place, classOf[ScNewTemplateDefinition])
    (definition, definition.expectedTypes().map {
      case ScAbstractType(_, _, upper) => upper
      case tp => tp
    })
  }

  private[completion] type RenamesMap = Map[String, (PsiNamedElement, String)]
  private[completion] type PropsConstructor = PsiClass => LookupElementProps

  private[completion] def createRenamesMap(element: PsiElement): RenamesMap =
    getContextOfType(element, false, classOf[ScReference]) match {
      case null =>
        Map.empty
      case ref =>
        ref.getVariants.flatMap {
          case ScalaLookupItem(item, element) =>
            item.isRenamed.map { name =>
              element.name -> (element -> name)
            }
          case _ => None
        }.toMap
    }

  private[this] def appropriateType(clazz: PsiClass, types: Seq[ScType]): (ScType, Boolean) = {
    val (designatorType, parameters) = classComponents(clazz)
    val maybeParameter = parameters match {
      case Seq(head) => Some(head)
      case _ => None
    }

    findAppropriateType(types, designatorType, maybeParameter).getOrElse {
      (fromParameters(designatorType, maybeParameter), parameters.nonEmpty)
    }
  }

  private[completion] final case class LookupElementProps(`type`: ScType,
                                                          hasSubstitutionProblem: Boolean,
                                                          `class`: PsiClass,
                                                          substitutor: ScSubstitutor = ScSubstitutor.empty) {

    def createLookupElement(renamesMap: RenamesMap): LookupElement = {
      val isRenamed = for {
        (`class`, name) <- renamesMap.get(`class`.name)
      } yield name
      createLookupElement(isRenamed)
    }

    def createLookupElement(isRenamed: Option[String]): LookupElement = {
      val name = `class`.name
      val renamedPrefix = isRenamed.fold("")(_ + " <= ")

      val isInterface = `class`.isInterface || `class`.hasAbstractModifier
      val tailText = if (isInterface) " {...}" else ""

      val typeParametersEvaluator: (ScType => String) => String = `type` match {
        case ParameterizedType(_, types) => types.map(_).commaSeparated(Model.SquareBrackets)
        case _ => Function.const("")
      }

      val renderer = new LookupElementRenderer[LookupElement] {

        override def renderElement(ignore: LookupElement,
                                   presentation: LookupElementPresentation): Unit = {
          presentation.appendGrayedTailText(tailText)
          presentation.appendGrayedTailText(" ")
          presentation.appendGrayedTailText(`class`.getPresentation.getLocationString)

          presentation.setIcon(`class`)
          presentation.setStrikeout(`class`)

          val parametersText = typeParametersEvaluator(substitutor.andThen(_.presentableText(`class`)))
          presentation.setItemText(renamedPrefix + name + parametersText)
        }
      }

      val insertHandler = new ScalaConstructorInsertHandler(
        typeParametersEvaluator,
        hasSubstitutionProblem,
        isInterface,
        isRenamed.isDefined,
        ScalaCodeStyleSettings.getInstance(`class`.getProject).hasImportWithPrefix(`class`.qualifiedName)
      )

      val policy = {
        import AutoCompletionPolicy._
        if (isUnitTestMode) ALWAYS_AUTOCOMPLETE
        else if (isInterface) NEVER_AUTOCOMPLETE
        else SETTINGS_DEPENDENT
      }

      LookupElementBuilder
        .createWithSmartPointer(isRenamed.getOrElse(name), `class`)
        .withRenderer(renderer)
        .withInsertHandler(insertHandler)
        .withAutoCompletionPolicy(policy)
    }
  }

  private def collectProps(`type`: ScType)
                          (isAccessible: PsiClass => Boolean)
                          (implicit project: Project): Seq[LookupElementProps] = {
    val inheritors = `type`.extractClass.toSeq
      .flatMap(findInheritors)
      .filter { clazz =>
        clazz.name match {
          case null | "" => false
          case _ => true
        }
      }

    val substitutedInheritors = for {
      clazz <- inheritors
      (designatorType, parameters) = classComponents(clazz)
      (actualType, hasSubstitutionProblem) <- findAppropriateType(Seq(`type`), designatorType, parameters)
    } yield (actualType, hasSubstitutionProblem)

    val addedClasses = mutable.HashSet.empty[String]
    for {
      (actualType, hasSubstitutionProblem) <- (`type`, false) +: substitutedInheritors
      (extractedClass, extractedSubstitutor) <- extractValidClass(actualType)
      if addedClasses.add(extractedClass.qualifiedName) && isAccessible(extractedClass)
    } yield LookupElementProps(actualType, hasSubstitutionProblem, extractedClass, extractedSubstitutor)
  }

  private[this] def findInheritors(clazz: PsiClass)
                                  (implicit project: Project) = {
    // this change is important for Scala Worksheet/Script classes. Will not find inheritors, due to file copy.
    val searchScope = clazz.getUseScope match {
      case _: LocalSearchScope => GlobalSearchScope.allScope(project)
      case useScope => useScope
    }

    ClassInheritorsSearch.search(clazz, searchScope, true).asScala
  }

  private[this] def extractValidClass(`type`: ScType): Option[(PsiClass, ScSubstitutor)] = {
    val names = Set("scala.Boolean",
      "scala.Byte", "scala.Short", "scala.Int", "scala.Long",
      "scala.Float", "scala.Double",
      "scala.AnyVal", "scala.Char", "scala.Unit", "scala.Any")

    // filter base types (it's important for scala 2.9)
    // todo: filter inner classes smarter (how? don't forget deep inner classes)
    def isInvalid(clazz: PsiClass) =
      clazz.isInstanceOf[ScObject] || names.contains(clazz.qualifiedName) || (clazz.containingClass match {
        case null => false
        case _: ScObject => clazz.hasModifierPropertyScala("static")
        case _ => true
      })

    `type`.extractClassType.filterNot {
      case (clazz, _) => isInvalid(clazz)
    }
  }

  private[this] def classComponents(clazz: PsiClass): (ScDesignatorType, Seq[PsiTypeParameter]) =
    (ScDesignatorType(clazz), clazz.getTypeParameters.toSeq)

  private[this] def findAppropriateType(types: Seq[ScType],
                                        designatorType: ScDesignatorType,
                                        parameters: Iterable[PsiTypeParameter]): Option[(ScType, Boolean)] = {
    if (types.isEmpty) return None

    val undefinedTypes = parameters.map(UndefinedType(_))
    val predefinedType = fromParametersTypes(designatorType, undefinedTypes)

    for (t <- types) {
      predefinedType.conformanceSubstitutor(t) match {
        case Some(substitutor) =>
          val valueType = fromParameters(designatorType, parameters)
          return Some(substitutor(valueType), undefinedTypes.map(substitutor).exists(_.isInstanceOf[UndefinedType]))
        case _ =>
      }
    }

    None
  }

  private[this] def fromParameters(designatorType: ScDesignatorType, parameters: Iterable[PsiTypeParameter]): ValueType =
    fromParametersTypes(designatorType, parameters.map(TypeParameterType(_)))

  private[this] def fromParametersTypes(designatorType: ScDesignatorType, types: Iterable[ScType]): ValueType =
    if (types.isEmpty) designatorType else ScParameterizedType(designatorType, types.toSeq)
}
