package org.jetbrains.plugins.scala.lang.exprTree
package symbols

import org.jetbrains.plugins.scala.lang.exprTree.{AccessModifier, SymbolKind, TreeContext, ValueSymbol}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.util.EnumSet.EnumSet

final class ValueSymbolFromPsi(override val kind: SymbolKind.Value,
                               override val name: Option[String],
                               override val accessModifier: AccessModifier,
                               override val modifier: EnumSet[ScalaModifier],
                               initialExprTree: ExprTree) extends ValueSymbol {


  override def inferType(context: TreeContext): TypeResult = ???
}

object ValueSymbolFromPsi {
  def apply(patternDef: ScPatternDefinition): ValueSymbolFromPsi = {
    patternDef.pList.patterns.head match {
      case pat: ScReferencePattern =>
        val modifierList = patternDef.getModifierList
        new ValueSymbolFromPsi(SymbolKind.Val, Some(pat.name), AccessModifier.fromPsi(modifierList), modifierList.modifiers)
    }
  }
}