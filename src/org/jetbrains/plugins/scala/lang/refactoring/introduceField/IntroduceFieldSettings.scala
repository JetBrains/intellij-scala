package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * Nikolay.Tropin
 * 7/16/13
 */
class IntroduceFieldSettings[T <: PsiElement](ifc: IntroduceFieldContext[T]) {
  private val scalaSettings = ScalaApplicationSettings.getInstance()
  private[this] var isVar = scalaSettings.INTRODUCE_FIELD_IS_VAR
  private[this] var replAll = scalaSettings.INTRODUCE_FIELD_REPLACE_ALL
  private[this] var visLevel = scalaSettings.INTRODUCE_FIELD_VISIBILITY
  private[this] var explType = scalaSettings.INTRODUCE_FIELD_EXPLICIT_TYPE
  private[this] var initInDecl = scalaSettings.INTRODUCE_FIELD_INITIALIZE_IN_DECLARATION
  private[this] var initInDeclEn: Boolean = true
  private[this] var initLocEn: Boolean = true

  var replaceAllChbEnabled: Boolean = ifc.occurrences.length > 1
  var defineVarChbEnabled: Boolean = true
  var explicitTypeChbEnabled: Boolean = true

  val canBeInitInDeclaration: Boolean = ifc.canBeInitInDecl
  val canBeInitLocalIfReplaceAll: Boolean = ifc.canBeInitLocally(replaceAll = true)
  val canBeInitLocalOneOccurrence: Boolean = ifc.canBeInitLocally(replaceAll = false)
  val canBeInitLocally: Boolean = canBeInitLocalIfReplaceAll || canBeInitLocalOneOccurrence

  import scala.collection.JavaConversions._

  var name: String = ifc.possibleNames.head
  var scType: ScType = ifc.types(0)
  def setName(s: String) {name = s}
  def setType(t: ScType) {scType = t}

  if (!canBeInitLocally) {
    initLocallyEnabled = false
    initInDeclaration = true
  }

  if (!canBeInitInDeclaration) {
    initInDeclarationEnabled = false
    initInDeclaration = false
    if (!canBeInitLocalIfReplaceAll) {
      replaceAllChbEnabled = false
      replaceAll = false
    }
  }

  if (initInDeclarationEnabled && initLocallyEnabled) initInDeclaration = scalaSettings.INTRODUCE_FIELD_INITIALIZE_IN_DECLARATION

  def defineVar: Boolean = isVar
  def defineVar_=(value: Boolean) {isVar = value}
  def setDefineVar(value: Boolean) {defineVar = value}

  def replaceAll: Boolean = replAll
  def replaceAll_=(value: Boolean) {
    replAll = value
    initLocallyEnabled = replaceAll && canBeInitLocalIfReplaceAll || !replaceAll && canBeInitLocalOneOccurrence
  }
  def setReplaceAll(value: Boolean) {replaceAll = value}

  def visibilityLevel: String = visLevel
  def visibilityLevel_=(value: String) {visLevel = value}
  def setVisibilityLevel(value: String) {visibilityLevel = value}

  def explicitType: Boolean = explType
  def explicitType_=(value: Boolean) {explType = value}
  def setExplicitType(value: Boolean) {explicitType = value}


  def initInDeclaration: Boolean = initInDecl
  def initInDeclaration_=(value: Boolean) {
    if (value && initInDeclarationEnabled || !value && initLocallyEnabled) {
      initInDecl = value
      if (!initInDecl) {
        defineVar = true
        explicitType = true
      }
      defineVarChbEnabled = value
      explicitTypeChbEnabled = value
    }
  }
  def setInitInDeclaration(value: Boolean) {initInDeclaration = value}

  def initInDeclarationEnabled: Boolean = initInDeclEn
  def initInDeclarationEnabled_=(value: Boolean) {
    initInDeclEn = value
    if (!initInDeclarationEnabled) {
      initInDeclaration = false
      if (!canBeInitLocalIfReplaceAll) {
        replaceAll = false
        replaceAllChbEnabled = false
      }
    }
  }

  def initLocallyEnabled: Boolean = initLocEn
  def initLocallyEnabled_=(value: Boolean) {
    initLocEn = value
    if (!initLocallyEnabled) {
      initInDeclaration = true
    }
  }
}
