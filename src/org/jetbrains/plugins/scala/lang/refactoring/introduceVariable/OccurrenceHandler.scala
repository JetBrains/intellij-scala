package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement


/**
*  Created by Kate Ustyuzhanina
*  on 8/18/15
*/
object OccurrenceHandler {
  def apply(typeElement: ScTypeElement,
            usualOccurrence: Array[ScTypeElement], isReplaceAllUsual: Boolean): OccurrenceHandler = {
    new OccurrenceHandler(typeElement, usualOccurrence, isReplaceAllUsual,
      Array[ScTypeElement](), false, Array[ScTypeElement](), false)
  }

  def apply(typeElement: ScTypeElement, isReplaceAllUsual: Boolean, isReplaceOccurrenceIncompanionObject: Boolean,
            isReplaceOccurrenceInInheritors: Boolean, scopeItem: ScopeItem): OccurrenceHandler  = {
    new OccurrenceHandler(typeElement, scopeItem.usualOccurrences, isReplaceAllUsual, scopeItem.occurrencesInCompanion,
      isReplaceOccurrenceIncompanionObject, scopeItem.occurrencesFromInheretors, isReplaceOccurrenceInInheritors)
  }
}

class OccurrenceHandler(typeElement: ScTypeElement, usualOccurrence: Array[ScTypeElement], isReplaceAllUsual: Boolean,
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