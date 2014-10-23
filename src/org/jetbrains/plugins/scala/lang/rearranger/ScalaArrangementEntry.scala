package org.jetbrains.plugins.scala
package lang.rearranger

import java.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.arrangement._
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken

/**
 * @author Roman.Shein
 * Date: 08.07.13
 */
class ScalaArrangementEntry(parent: ArrangementEntry, startOffset: Int, endOffset: Int,
        entryType: ArrangementSettingsToken, name: String, canBeMatched: Boolean)
        extends DefaultArrangementEntry(parent, startOffset, endOffset, canBeMatched) with TypeAwareArrangementEntry
        with NameAwareArrangementEntry with ModifierAwareArrangementEntry {

  val modifiers = new util.HashSet[ArrangementSettingsToken]

  def this( parent: ArrangementEntry, range: TextRange, entryType: ArrangementSettingsToken, name: String, canBeMatched: Boolean) =
  this(parent, range.getStartOffset, range.getEndOffset, entryType, name, canBeMatched)

  override def getName: String = name

  override def getModifiers: util.Set[ArrangementSettingsToken] = modifiers

  override def getTypes: util.Set[ArrangementSettingsToken] = {
    val res = new util.HashSet[ArrangementSettingsToken]()
    res.add(entryType)
    res
  }

  def getType = entryType

  def addModifier(mod: ArrangementSettingsToken) = modifiers.add(mod)

  override def toString = s"[$startOffset, $endOffset)" //text range represented by this entry

  override def hashCode = startOffset + endOffset

  override def equals(o: Any) = o match {
    case other: ScalaArrangementEntry => other.getStartOffset == startOffset && other.getEndOffset == endOffset &&
            other.getType == entryType && other.getParent == parent
    case _                            => false
  }
}
