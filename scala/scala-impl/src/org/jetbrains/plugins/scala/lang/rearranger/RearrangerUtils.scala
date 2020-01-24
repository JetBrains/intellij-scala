package org.jetbrains.plugins.scala.lang.rearranger

import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier._
import com.intellij.psi.codeStyle.arrangement.std.{ArrangementSettingsToken, StdArrangementSettingsToken, StdArrangementTokenType, StdArrangementTokens}
import org.jetbrains.annotations.NonNls

import scala.collection.immutable.{HashMap, HashSet, ListMap}

//noinspection TypeAnnotation,SameParameterValue
//TODO: use names from bundle
object RearrangerUtils {

  private def token(@NonNls id: String, name: String, tokenType: StdArrangementTokenType): StdArrangementSettingsToken =
    StdArrangementSettingsToken.token(id, name, tokenType)

  private def tokenById(@NonNls id: String, tokenType: StdArrangementTokenType): StdArrangementSettingsToken =
    StdArrangementSettingsToken.tokenById(id, tokenType)


  val SCALA_GETTERS_AND_SETTERS: ArrangementSettingsToken = token(
    "SCALA_KEEP_SCALA_GETTERS_SETTERS_TOGETHER",
    "Keep scala-style getters and setters together",
    StdArrangementTokenType.GROUPING
  )

  val JAVA_GETTERS_AND_SETTERS: ArrangementSettingsToken = token(
    "SCALA_KEEP_JAVA_GETTERS_SETTERS_TOGETHER",
    "Keep java-style getters and setters together",
    StdArrangementTokenType.GROUPING
  )

  val SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS: ArrangementSettingsToken = token(
    "SCALA_SPLIT_BY_EXPRESSIONS",
    "Split into unarrangeable blocks by expressions",
    StdArrangementTokenType.GROUPING
  )

  val SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_IMPLICITS: ArrangementSettingsToken = token(
    "SCALA_SPLIT_BY_IMPLICITS",
    "Split into unarrangeable blocks by implicits",
    StdArrangementTokenType.GROUPING
  )

  //access modifiers
  private val scalaAccessModifiersByName = ListMap("public" -> PUBLIC, "protected" -> PROTECTED, "private" -> PRIVATE)
  private val scalaAccessModifiersById   = HashMap(PUBLIC.getId -> PUBLIC, PROTECTED.getId -> PROTECTED, PRIVATE.getId -> PRIVATE)

  val SEALED: ArrangementSettingsToken = token(
    "SCALA_SEALED",
    "sealed",
    StdArrangementTokenType.MODIFIER
  )
  val IMPLICIT: ArrangementSettingsToken = token(
    "SCALA_IMPLICIT",
    "implicit",
    StdArrangementTokenType.MODIFIER
  )
  val CASE: ArrangementSettingsToken = token(
    "SCALA_CASE",
    "case",
    StdArrangementTokenType.MODIFIER
  )
  val OVERRIDE: ArrangementSettingsToken = token(
    "SCALA_OVERRIDE",
    "override",
    StdArrangementTokenType.MODIFIER
  )
  val LAZY: ArrangementSettingsToken = token(
    "SCALA_LAZY",
    "lazy",
    StdArrangementTokenType.MODIFIER
  )
  private val scalaOtherModifiersByName = ListMap(
    "sealed" -> SEALED,
    "implicit" -> IMPLICIT,
    "abstract" -> ABSTRACT,
    "case" -> CASE,
    "final" -> FINAL,
    "override" -> OVERRIDE,
    "lazy" -> LAZY
  )

  private val scalaOtherModifiersById = HashMap(
    SEALED.getId -> SEALED,
    IMPLICIT.getId -> IMPLICIT,
    ABSTRACT.getId -> ABSTRACT,
    FINAL.getId -> FINAL,
    OVERRIDE.getId -> OVERRIDE,
    LAZY.getId -> LAZY
  )

  val TYPE: ArrangementSettingsToken = token(
    "SCALA_TYPE",
    "type",
    StdArrangementTokenType.ENTRY_TYPE
  )
  val FUNCTION: ArrangementSettingsToken = token(
    "SCALA_FUNCTION",
    "function",
    StdArrangementTokenType.ENTRY_TYPE
  )
  val VAL: ArrangementSettingsToken = token(
    "SCALA_VAL",
    "val",
    StdArrangementTokenType.ENTRY_TYPE
  )
  val MACRO: ArrangementSettingsToken = token(
    "SCALA_MACRO",
    "macro",
    StdArrangementTokenType.ENTRY_TYPE
  )
  val OBJECT: ArrangementSettingsToken = token(
    "SCALA_OBJECT",
    "object",
    StdArrangementTokenType.ENTRY_TYPE
  )
  val UNSEPARABLE_RANGE: ArrangementSettingsToken = tokenById(
    "SCALA_UNSEPARABLE_RANGE",
    StdArrangementTokenType.ENTRY_TYPE
  )

  //maps and sets of tokens
  val scalaTypesValues = HashSet(TYPE, FUNCTION, CLASS, VAL, VAR, TRAIT, MACRO, CONSTRUCTOR, OBJECT)
  private val scalaTypesById = scalaTypesValues.map(t => t.getId -> t).toMap

  val scalaAccessModifiersValues = scalaAccessModifiersByName.toSet.map((x: tokensType) => x._2)

  val scalaModifiers = scalaAccessModifiersValues ++ scalaOtherModifiersByName.toSet.map((x: tokensType) => x._2)

  private type tokensType = (String, ArrangementSettingsToken)

  val supportedOrders = HashSet(StdArrangementTokens.Order.BY_NAME, StdArrangementTokens.Order.KEEP)

  val commonModifiers = scalaAccessModifiersValues + FINAL //TODO: determine if final is common

  private val scalaModifiersByName: Map[String, ArrangementSettingsToken] =
    scalaAccessModifiersByName ++ scalaOtherModifiersByName

  def getModifierByName(modifierName: String): Option[ArrangementSettingsToken] =
    scalaModifiersByName.get(modifierName)

  private val scalaTokensById: Map[String, ArrangementSettingsToken] =
    scalaAccessModifiersById ++ scalaOtherModifiersById ++ scalaTypesById

  def getTokenById(modifierId: String): Option[ArrangementSettingsToken] =
    scalaTokensById.get(modifierId)

  val tokensForType = HashMap(
    TYPE -> (commonModifiers + OVERRIDE),
    FUNCTION -> (commonModifiers + OVERRIDE + IMPLICIT),
    CLASS -> (commonModifiers + ABSTRACT + SEALED),
    TRAIT -> (commonModifiers + ABSTRACT + SEALED),
    VAL -> (commonModifiers + OVERRIDE + LAZY + ABSTRACT),
    VAR -> (commonModifiers + OVERRIDE),
    MACRO -> (commonModifiers + OVERRIDE),
    CONSTRUCTOR -> scalaAccessModifiersValues,
    OBJECT -> commonModifiers
  )
}
