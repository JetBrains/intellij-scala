package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

object OccurrenceData {

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
    scopeItem match {
      case simpleScope: SimpleScopeItem =>
        new OccurrenceData(
          typeElement,
          simpleScope.usualOccurrences,
          isReplaceAllUsual,
          simpleScope.occurrencesInCompanion,
          isReplaceOccurrenceInCompanionObject,
          simpleScope.occurrencesFromInheritors,
          isReplaceOccurrenceInInheritors
        )
      case packageScope: PackageScopeItem =>
        new OccurrenceData(
          typeElement,
          packageScope.occurrences,
          isReplaceAllUsual,
          Array[ScTypeElement](),
          isReplaceOccurrenceInCompanionObject,
          Array[ScTypeElement](),
          isReplaceOccurrenceInInheritors
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