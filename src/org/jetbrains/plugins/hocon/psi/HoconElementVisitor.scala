package org.jetbrains.plugins.hocon.psi

import com.intellij.psi.PsiElementVisitor

class HoconElementVisitor extends PsiElementVisitor {
  def visitHoconFile(element: HoconPsiFile) = visitFile(element)

  def visitHoconElement(element: HoconPsiElement) = visitElement(element)

  def visitHValue(element: HValue) = visitHoconElement(element)

  def visitHLiteral(element: HLiteral) = visitHValue(element)

  def visitHNull(element: HNull) = visitHLiteral(element)

  def visitHBoolean(element: HBoolean) = visitHLiteral(element)

  def visitHNumber(element: HNumber) = visitHLiteral(element)

  def visitHString(element: HString) = visitHLiteral(element)

  def visitHObject(element: HObject) = visitHValue(element)

  def visitHArray(element: HArray) = visitHValue(element)

  def visitHSubstitution(element: HSubstitution) = visitHValue(element)

  def visitHConcatenation(element: HConcatenation) = visitHValue(element)

  def visitHObjectEntry(element: HObjectEntry) = visitHoconElement(element)

  def visitHObjectField(element: HObjectField) = visitHObjectEntry(element)

  def visitHInclude(element: HInclude) = visitHObjectEntry(element)

  def visitHObjectEntries(element: HObjectEntries) = visitHoconElement(element)

  def visitHBareObjectField(element: HBareObjectField) = visitHoconElement(element)

  def visitHIncluded(element: HIncluded) = visitHoconElement(element)

  def visitHKey(element: HKey) = visitHoconElement(element)

  def visitHPath(element: HPath) = visitHoconElement(element)

  def visitHUnquotedString(element: HUnquotedString) = visitHoconElement(element)
}
