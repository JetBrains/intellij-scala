package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaConstructorInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.{ScalaLookupItem, T}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

import scala.collection.{JavaConverters, mutable}

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

  def createRenamesMap(element: PsiElement): RenamesMap =
    getContextOfType(element, false, classOf[ScReference]) match {
      case null => Map.empty
      case ref =>
        ref.getVariants.flatMap {
          case item: ScalaLookupItem => createRenamePair(item)
          case _ => None
        }.toMap
    }

  def createRenamePair(item: ScalaLookupItem): Option[(String, (PsiNamedElement, String))] =
    item.isRenamed.map { name =>
      val element = item.element
      element.name -> (element, name)
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

  private[completion] case class LookupElementProps(`type`: ScType,
                                                    hasSubstitutionProblem: Boolean,
                                                    clazz: PsiClass,
                                                    substitutor: ScSubstitutor = ScSubstitutor.empty) {

    def createLookupElement(renamesMap: RenamesMap): ScalaLookupItem = {
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

          presentation.setIcon(clazz)
          presentation.setStrikeout(clazz)

          val nameText = isRenamed match {
            case Some(newName) => s"$newName <= $name"
            case _ => name
          }
          val parametersText = typeParameters match {
            case Seq() => ""
            case seq => seq.map(substitutor)
              .map(_.presentableText(clazz))
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

    import JavaConverters._
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
    (ScDesignatorType(clazz), clazz.getTypeParameters)

  private[this] def findAppropriateType(types: Seq[ScType],
                                        designatorType: ScDesignatorType,
                                        parameters: Traversable[PsiTypeParameter]): Option[(ScType, Boolean)] = {
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

  private[this] def fromParameters(designatorType: ScDesignatorType, parameters: Traversable[PsiTypeParameter]): ValueType =
    fromParametersTypes(designatorType, parameters.map(TypeParameterType(_)))

  private[this] def fromParametersTypes(designatorType: ScDesignatorType, types: Traversable[ScType]): ValueType =
    if (types.isEmpty) designatorType else ScParameterizedType(designatorType, types.toSeq)
}
