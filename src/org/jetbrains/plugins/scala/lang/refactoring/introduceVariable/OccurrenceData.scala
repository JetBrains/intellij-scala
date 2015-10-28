package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement


/**
*  Created by Kate Ustyuzhanina
*  on 8/18/15
*/
object OccurrenceData {
  def apply(typeElement: ScTypeElement,
            usualOccurrence: Array[ScTypeElement], isReplaceAllUsual: Boolean): OccurrenceData = {
    new OccurrenceData(typeElement, usualOccurrence, isReplaceAllUsual,
      Array[ScTypeElement](), false, Array[ScTypeElement](), false)
  }

  def apply(typeElement: ScTypeElement, isReplaceAllUsual: Boolean, isReplaceOccurrenceIncompanionObject: Boolean,
            isReplaceOccurrenceInInheritors: Boolean, scopeItem: ScopeItem): OccurrenceData  = {
    scopeItem match {
      case simpleScope: SimpleScopeItem =>
        new OccurrenceData(typeElement, simpleScope.usualOccurrences, isReplaceAllUsual, simpleScope.occurrencesInCompanion,
          isReplaceOccurrenceIncompanionObject, simpleScope.occurrencesFromInheretors, isReplaceOccurrenceInInheritors)
      case packageScope: PackageScopeItem =>
        new OccurrenceData(typeElement, packageScope.occurrences, isReplaceAllUsual, Array[ScTypeElement](),
          isReplaceOccurrenceIncompanionObject, Array[ScTypeElement](), isReplaceOccurrenceInInheritors)
    }
  }
}

class OccurrenceData(typeElement: ScTypeElement, usualOccurrence: Array[ScTypeElement], isReplaceAllUsual: Boolean,
                     companiomObjOccurrence: Array[ScTypeElement], isReplaceInCompanion: Boolean,
                     extendedClassOccurrence: Array[ScTypeElement], isReplaceInExtendedClasses: Boolean) {
  def getUsualOccurrences = {
    if (isReplaceAllUsual) {
      usualOccurrence
    } else {
      Array(typeElement)
    }
  }

  def getCompanionObjOccurrences = getOccurrences(companiomObjOccurrence, isReplaceInCompanion)

  def getExtendedOccurrences = getOccurrences(extendedClassOccurrence, isReplaceInExtendedClasses)

  def getOccurrencesCount = getAllOccurrences.length

  def getAllOccurrences = getUsualOccurrences ++ getCompanionObjOccurrences ++ getExtendedOccurrences

  private def getOccurrences(occ: Array[ScTypeElement], needAll: Boolean): Array[ScTypeElement] = {
    if (needAll) {
      occ
    } else {
      Array[ScTypeElement]()
    }
  }
}