package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

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

  var name: String = ifc.possibleNames.iterator().next()
  var scType: ScType = ifc.types(0)
  def setName(s: String): Unit = {name = s}
  def setType(t: ScType): Unit = {scType = t}

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
  def defineVar_=(value: Boolean): Unit = {isVar = value}
  def setDefineVar(value: Boolean): Unit = {defineVar = value}

  def replaceAll: Boolean = replAll
  def replaceAll_=(value: Boolean): Unit = {
    replAll = value
    initLocallyEnabled = replaceAll && canBeInitLocalIfReplaceAll || !replaceAll && canBeInitLocalOneOccurrence
  }
  def setReplaceAll(value: Boolean): Unit = {replaceAll = value}

  def visibilityLevel: String = visLevel
  def visibilityLevel_=(value: String): Unit = {visLevel = value}
  def setVisibilityLevel(value: String): Unit = {visibilityLevel = value}

  def explicitType: Boolean = explType
  def explicitType_=(value: Boolean): Unit = {explType = value}
  def setExplicitType(value: Boolean): Unit = {explicitType = value}


  def initInDeclaration: Boolean = initInDecl
  def initInDeclaration_=(value: Boolean): Unit = {
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
  def setInitInDeclaration(value: Boolean): Unit = {initInDeclaration = value}

  def initInDeclarationEnabled: Boolean = initInDeclEn
  def initInDeclarationEnabled_=(value: Boolean): Unit = {
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
  def initLocallyEnabled_=(value: Boolean): Unit = {
    initLocEn = value
    if (!initLocallyEnabled) {
      initInDeclaration = true
    }
  }
}
