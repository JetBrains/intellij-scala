package org.jetbrains.plugins.scala
package lang

import scala.collection.immutable
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokenType
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken

import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettingsToken
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier._

/**
 * @author Roman Shein
 * Date: 09.07.13
 */
package object rearranger {

  val SCALA_GETTERS_AND_SETTERS_ID = "Keep_scala-style_getters_and_setters_together"
  val SCALA_GETTERS_AND_SETTERS: ArrangementSettingsToken =
    new StdArrangementSettingsToken(SCALA_GETTERS_AND_SETTERS_ID, StdArrangementTokenType.GROUPING) //TODO: use name from bundle
  val JAVA_GETTERS_AND_SETTERS_ID = "Keep_java-style_getters_and_setters_together"
  val JAVA_GETTERS_AND_SETTERS: ArrangementSettingsToken =
    new StdArrangementSettingsToken(JAVA_GETTERS_AND_SETTERS_ID, StdArrangementTokenType.GROUPING) //TODO: use name from bundle
  val SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS_ID = "Split into unarrangeable blocks by expressions"
  val SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS: ArrangementSettingsToken =
    new StdArrangementSettingsToken(SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS_ID, StdArrangementTokenType.GROUPING) //TODO: use name from bundle

  val scalaGroupingRules = immutable.HashMap(SCALA_GETTERS_AND_SETTERS_ID -> SCALA_GETTERS_AND_SETTERS,
    JAVA_GETTERS_AND_SETTERS_ID -> JAVA_GETTERS_AND_SETTERS,
    SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS_ID -> SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS)

  //access modifiers
  val scalaAccessModifiers = immutable.HashMap(PRIVATE.getId -> PRIVATE, PUBLIC.getId -> PUBLIC,
    PROTECTED.getId -> PROTECTED)

  //other modifiers
  val SEALED_ID = "SEALED"
  val SEALED: ArrangementSettingsToken =
    new StdArrangementSettingsToken(SEALED_ID, StdArrangementTokenType.MODIFIER)
  val IMPLICIT_ID = "IMPLICIT"
  val IMPLICIT: ArrangementSettingsToken =
    new StdArrangementSettingsToken(IMPLICIT_ID, StdArrangementTokenType.MODIFIER)
  val CASE_ID = "CASE"
  val CASE: ArrangementSettingsToken =
    new StdArrangementSettingsToken(CASE_ID, StdArrangementTokenType.MODIFIER)
  val OVERRIDE_ID = "OVERRIDE"
  val OVERRIDE: ArrangementSettingsToken =
    new StdArrangementSettingsToken(OVERRIDE_ID, StdArrangementTokenType.MODIFIER)
  val LAZY_ID = "LAZY"
  val LAZY: ArrangementSettingsToken =
    new StdArrangementSettingsToken(LAZY_ID, StdArrangementTokenType.MODIFIER)
  val scalaOtherModifiers = immutable.HashMap(SEALED_ID -> SEALED, IMPLICIT_ID -> IMPLICIT,
    ABSTRACT.getId -> ABSTRACT, CASE_ID -> CASE, FINAL.getId -> FINAL, OVERRIDE_ID -> OVERRIDE, LAZY_ID -> LAZY)

  //types
  val TYPE_ID = "TYPE"
  val TYPE: ArrangementSettingsToken =
    new StdArrangementSettingsToken(TYPE_ID, StdArrangementTokenType.ENTRY_TYPE)
  val FUNCTION_ID = "FUNCTION"
  val FUNCTION: ArrangementSettingsToken =
    new StdArrangementSettingsToken(FUNCTION_ID, StdArrangementTokenType.ENTRY_TYPE)
  val VAL_ID = "VAL"
  val VAL: ArrangementSettingsToken = new StdArrangementSettingsToken(VAL_ID, StdArrangementTokenType.ENTRY_TYPE)
  val MACRO_ID = "MACRO"
  val MACRO: ArrangementSettingsToken =
    new StdArrangementSettingsToken(MACRO_ID, StdArrangementTokenType.ENTRY_TYPE)
  val OBJECT_ID = "OBJECT"
  val OBJECT: ArrangementSettingsToken =
    new StdArrangementSettingsToken(OBJECT_ID, StdArrangementTokenType.ENTRY_TYPE)
  //this is a special token that is not used in arrangement GUI and always has canBeMatched = false
  val UNSEPARABLE_RANGE_ID = "UNSEPARABLE_RANGE"
  val UNSEPARABLE_RANGE: ArrangementSettingsToken = new StdArrangementSettingsToken(UNSEPARABLE_RANGE_ID, StdArrangementTokenType.ENTRY_TYPE)

  //maps and sets of tokens
  val scalaTypes = immutable.HashMap(TYPE_ID -> TYPE, FUNCTION_ID -> FUNCTION, CLASS.getId -> CLASS,
    VAL_ID -> VAL, VAR.getId -> VAR, TRAIT.getId -> TRAIT, MACRO_ID -> MACRO, CONSTRUCTOR.getId -> CONSTRUCTOR,
    OBJECT_ID -> OBJECT)
  val scalaTypesValues = scalaTypes.toSet.map((x: tokensType) => x._2)

  val scalaAccessModifiersValues = scalaAccessModifiers.toSet.map((x: tokensType) => x._2)

  val scalaModifiers = scalaAccessModifiersValues ++ scalaOtherModifiers.toSet.map((x: tokensType) => x._2)

  val scalaArrangementTokensByName = scalaAccessModifiers ++ scalaOtherModifiers ++ scalaTypes ++ scalaGroupingRules

  private type tokensType = Pair[String, ArrangementSettingsToken]

  val scalaArrangementSettingsTokens = scalaModifiers ++ scalaTypesValues

  val supportedOrders = immutable.HashSet(StdArrangementTokens.Order.BY_NAME, StdArrangementTokens.Order.KEEP)

  val commonModifiers = scalaAccessModifiersValues + FINAL //TODO: determine if final is common

  val tokensForType = immutable.HashMap(TYPE -> (commonModifiers + OVERRIDE), FUNCTION -> (commonModifiers +
          OVERRIDE + IMPLICIT), CLASS -> (commonModifiers + ABSTRACT + SEALED), TRAIT -> (commonModifiers +
          ABSTRACT + SEALED), VAL -> (commonModifiers + OVERRIDE + LAZY + ABSTRACT),
          VAR -> (commonModifiers + OVERRIDE), MACRO -> (commonModifiers + OVERRIDE),
          CONSTRUCTOR -> scalaAccessModifiersValues, OBJECT -> commonModifiers)

  def getTokenByName(name: String) = {
    scalaArrangementTokensByName.get(name.toUpperCase)
  }

}
