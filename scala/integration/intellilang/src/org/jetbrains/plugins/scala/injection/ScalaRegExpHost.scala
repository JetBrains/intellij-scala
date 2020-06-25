package org.jetbrains.plugins.scala.injection

import org.intellij.lang.regexp.psi.{RegExpChar, RegExpGroup, RegExpNamedGroupRef}
import org.intellij.lang.regexp.{DefaultRegExpPropertiesProvider, RegExpLanguageHost}

/** @see [[org.intellij.lang.regexp.RegExpLanguageHosts]]  */
class ScalaRegExpHost extends RegExpLanguageHost {

  private val defaultProvider = DefaultRegExpPropertiesProvider.getInstance()

  override def characterNeedsEscaping(c: Char): Boolean = false

  override def supportsPerl5EmbeddedComments(): Boolean = false

  override def supportsPossessiveQuantifiers(): Boolean = true

  override def supportsPythonConditionalRefs(): Boolean = false

  /** TODO: take into account JDK version if it is java.util.regex.Pattern.compile(...), see [[com.intellij.psi.impl.JavaRegExpHost]] */
  override def supportsNamedGroupSyntax(group: RegExpGroup): Boolean = group.getType == RegExpGroup.Type.NAMED_GROUP
  override def supportsNamedGroupRefSyntax(ref: RegExpNamedGroupRef): Boolean = ref.isNamedGroupRef

  override def supportsExtendedHexCharacter(regExpChar: RegExpChar): Boolean = false

  override def isValidCategory(category: String): Boolean = defaultProvider.isValidCategory(category)

  override def getAllKnownProperties: Array[Array[String]] = defaultProvider.getAllKnownProperties

  override def getPropertyDescription(name: String): String = defaultProvider.getPropertyDescription(name)

  override def getKnownCharacterClasses: Array[Array[String]] = defaultProvider.getKnownCharacterClasses
}