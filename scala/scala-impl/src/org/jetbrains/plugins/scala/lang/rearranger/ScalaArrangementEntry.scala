package org.jetbrains.plugins.scala.lang.rearranger

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.arrangement._
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken
import org.jetbrains.plugins.scala.util.HashBuilder._

import java.util

class ScalaArrangementEntry(parent: ArrangementEntry,
                            startOffset: Int,
                            endOffset: Int,
                            entryType: ArrangementSettingsToken,
                            name: String,
                            canBeMatched: Boolean,
                            val innerEntryType: Option[ArrangementSettingsToken])
  extends DefaultArrangementEntry(parent, startOffset, endOffset, canBeMatched)
    with TypeAwareArrangementEntry
    with NameAwareArrangementEntry
    with ModifierAwareArrangementEntry {

  val modifiers = new util.HashSet[ArrangementSettingsToken]

  def this( parent: ArrangementEntry, range: TextRange, entryType: ArrangementSettingsToken, name: String,
            canBeMatched: Boolean, innerEntryType: Option[ArrangementSettingsToken] = None) =
    this(parent, range.getStartOffset, range.getEndOffset, entryType, name, canBeMatched, innerEntryType)

  override def getName: String = name

  override def getModifiers: util.Set[ArrangementSettingsToken] = modifiers

  override def getTypes: util.Set[ArrangementSettingsToken] = {
    val res = new util.HashSet[ArrangementSettingsToken]()
    res.add(entryType)
    res
  }

  def getType: ArrangementSettingsToken = entryType

  def addModifier(mod: ArrangementSettingsToken): Boolean = modifiers.add(mod)

  override def toString = s"[$startOffset, $endOffset)" //text range represented by this entry

  override def hashCode: Int = startOffset #+ endOffset

  override def equals(o: Any): Boolean = o match {
    case other: ScalaArrangementEntry => other.getStartOffset == startOffset && other.getEndOffset == endOffset &&
      other.getType == entryType && other.getParent == parent
    case _                            => false
  }

  def spansTextRange(range: TextRange): Boolean =
    range.getStartOffset == getStartOffset && range.getEndOffset == getEndOffset
}
