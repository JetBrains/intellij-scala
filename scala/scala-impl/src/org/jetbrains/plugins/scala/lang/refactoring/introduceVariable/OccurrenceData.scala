package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

object OccurrenceData {
  case class ReplaceOptions(
    replaceAllOccurrences: Boolean,
    replaceOccurrencesInCompanionObjects: Boolean,
    replaceOccurrencesInInheritors: Boolean,
  )

  object ReplaceOptions {
    val DefaultInTests: ReplaceOptions = ReplaceOptions(
      replaceAllOccurrences = false,
      replaceOccurrencesInCompanionObjects = false,
      replaceOccurrencesInInheritors = false
    )
  }

  def apply(
    typeElement: ScTypeElement,
    usualOccurrence: Array[ScTypeElement],
    isReplaceAllUsual: Boolean
  ): OccurrenceData = {
    new OccurrenceData(
      typeElement,
      usualOccurrence,
      isReplaceAllUsual,
      Array[ScTypeElement](),
      isReplaceInCompanion = false,
      Array[ScTypeElement](),
      isReplaceInExtendedClasses = false
    )
  }

  def apply(
    typeElement: ScTypeElement,
    isReplaceAllUsual: Boolean,
    isReplaceOccurrenceInCompanionObject: Boolean,
    isReplaceOccurrenceInInheritors: Boolean,
    scopeItem: ScopeItem
  ): OccurrenceData = {
    val options = ReplaceOptions(
      replaceAllOccurrences = isReplaceAllUsual,
      replaceOccurrencesInCompanionObjects = isReplaceOccurrenceInCompanionObject,
      replaceOccurrencesInInheritors = isReplaceOccurrenceInInheritors
    )
    apply(typeElement, options, scopeItem)
  }

  def apply(
    typeElement: ScTypeElement,
    options: ReplaceOptions,
    scopeItem: ScopeItem
  ): OccurrenceData = {
    scopeItem match {
      case simpleScope: SimpleScopeItem =>
        new OccurrenceData(
          typeElement,
          usualOccurrence = simpleScope.usualOccurrences,
          isReplaceAllUsual = options.replaceAllOccurrences,
          companionObjOccurrence = simpleScope.occurrencesInCompanion,
          isReplaceInCompanion = options.replaceOccurrencesInCompanionObjects,
          extendedClassOccurrence = simpleScope.occurrencesFromInheritors,
          isReplaceInExtendedClasses = options.replaceOccurrencesInInheritors
        )
      case packageScope: PackageScopeItem =>
        new OccurrenceData(
          typeElement,
          usualOccurrence = packageScope.occurrences,
          isReplaceAllUsual = options.replaceAllOccurrences,
          companionObjOccurrence = Array[ScTypeElement](),
          isReplaceInCompanion = options.replaceOccurrencesInCompanionObjects,
          extendedClassOccurrence = Array[ScTypeElement](),
          isReplaceInExtendedClasses = options.replaceOccurrencesInInheritors
        )
    }
  }
}

class OccurrenceData(
  typeElement: ScTypeElement,
  usualOccurrence: Array[ScTypeElement],
  isReplaceAllUsual: Boolean,
  companionObjOccurrence: Array[ScTypeElement],
  isReplaceInCompanion: Boolean,
  extendedClassOccurrence: Array[ScTypeElement],
  isReplaceInExtendedClasses: Boolean
) {
  def getUsualOccurrences: Array[ScTypeElement] = {
    if (isReplaceAllUsual) {
      usualOccurrence
    } else {
      Array(typeElement)
    }
  }

  def getCompanionObjOccurrences: Array[ScTypeElement] = getOccurrences(companionObjOccurrence, isReplaceInCompanion)

  def getExtendedOccurrences: Array[ScTypeElement] = getOccurrences(extendedClassOccurrence, isReplaceInExtendedClasses)

  def getAllOccurrences: Array[ScTypeElement] = getUsualOccurrences ++ getCompanionObjOccurrences ++ getExtendedOccurrences

  private def getOccurrences(occ: Array[ScTypeElement], needAll: Boolean): Array[ScTypeElement] = {
    if (needAll) {
      occ
    } else {
      Array[ScTypeElement]()
    }
  }
}