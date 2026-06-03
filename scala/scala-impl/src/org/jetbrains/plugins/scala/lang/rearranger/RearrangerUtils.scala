package org.jetbrains.plugins.scala.lang.rearranger

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType._
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier._
import com.intellij.psi.codeStyle.arrangement.std.{ArrangementSettingsToken, StdArrangementSettingsToken, StdArrangementTokenType, StdArrangementTokens}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.ScalaBundle

import scala.collection.immutable.{HashMap, HashSet, ListSet}

//noinspection TypeAnnotation,SameParameterValue
//TODO: use names from bundle
object RearrangerUtils {

  private def token(@NonNls id: String, name: String, tokenType: StdArrangementTokenType): StdArrangementSettingsToken =
    StdArrangementSettingsToken.token(id, name, tokenType)

  private def tokenById(@NonNls id: String, tokenType: StdArrangementTokenType): StdArrangementSettingsToken =
    StdArrangementSettingsToken.token(id, StringUtil.toLowerCase(id).replace("_", " "), tokenType)


  val SCALA_GETTERS_AND_SETTERS: ArrangementSettingsToken = token(
    "SCALA_KEEP_SCALA_GETTERS_SETTERS_TOGETHER",
    ScalaBundle.message("rearranger.panel.keep.scala.style.getters.and.setters.together"),
    StdArrangementTokenType.GROUPING
  )

  val JAVA_GETTERS_AND_SETTERS: ArrangementSettingsToken = token(
    "SCALA_KEEP_JAVA_GETTERS_SETTERS_TOGETHER",
    ScalaBundle.message("rearranger.panel.keep.java.style.getters.and.setters.together"),
    StdArrangementTokenType.GROUPING
  )

  val SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_EXPRESSIONS: ArrangementSettingsToken = token(
    "SCALA_SPLIT_BY_EXPRESSIONS",
    ScalaBundle.message("rearranger.panel.split.into.unarrangeable.blocks.by.expressions"),
    StdArrangementTokenType.GROUPING
  )

  val SPLIT_INTO_UNARRANGEABLE_BLOCKS_BY_IMPLICITS: ArrangementSettingsToken = token(
    "SCALA_SPLIT_BY_IMPLICITS",
    ScalaBundle.message("rearranger.panel.split.into.unarrangeable.blocks.by.implicits"),
    StdArrangementTokenType.GROUPING
  )

  //access modifiers
  @NonNls val SEALED  : ArrangementSettingsToken = token("SCALA_SEALED", "sealed", StdArrangementTokenType.MODIFIER)
  @NonNls val IMPLICIT: ArrangementSettingsToken = token("SCALA_IMPLICIT", "implicit", StdArrangementTokenType.MODIFIER)
  @NonNls val CASE    : ArrangementSettingsToken = token("SCALA_CASE", "case", StdArrangementTokenType.MODIFIER)
  @NonNls val OVERRIDE: ArrangementSettingsToken = token("SCALA_OVERRIDE", "override", StdArrangementTokenType.MODIFIER)
  @NonNls val LAZY    : ArrangementSettingsToken = token("SCALA_LAZY", "lazy", StdArrangementTokenType.MODIFIER)

  @NonNls val TYPE             : ArrangementSettingsToken = token("SCALA_TYPE", "type", StdArrangementTokenType.ENTRY_TYPE)
  @NonNls val FUNCTION         : ArrangementSettingsToken = token("SCALA_FUNCTION", "function", StdArrangementTokenType.ENTRY_TYPE)
  @NonNls val VAL              : ArrangementSettingsToken = token("SCALA_VAL", "val", StdArrangementTokenType.ENTRY_TYPE)
  @NonNls val MACRO            : ArrangementSettingsToken = token("SCALA_MACRO", "macro", StdArrangementTokenType.ENTRY_TYPE)
  @NonNls val OBJECT           : ArrangementSettingsToken = token("SCALA_OBJECT", "object", StdArrangementTokenType.ENTRY_TYPE)
  val UNSEPARABLE_RANGE: ArrangementSettingsToken = tokenById("SCALA_UNSEPARABLE_RANGE", StdArrangementTokenType.ENTRY_TYPE)

  //maps and sets of tokens
  val scalaTypesValues = HashSet(TYPE, FUNCTION, CLASS, VAL, VAR, TRAIT, MACRO, CONSTRUCTOR, OBJECT)
  private val scalaTypesById = scalaTypesValues.map(t => t.getId -> t).toMap

  val supportedOrders = HashSet(StdArrangementTokens.Order.BY_NAME, StdArrangementTokens.Order.KEEP)

  val scalaAccessModifiers  = ListSet(PUBLIC, PROTECTED, PRIVATE)
  private val scalaAccessModifiersByName = scalaAccessModifiers.groupBySingle(_.getRepresentationValue)
  private val scalaAccessModifiersById = scalaAccessModifiers.groupBySingle(_.getId)

  private val scalaOtherModifiers = ListSet(SEALED, IMPLICIT, ABSTRACT, CASE, FINAL, OVERRIDE, LAZY)
  private val scalaOtherModifiersByName = scalaOtherModifiers.groupBySingle(_.getRepresentationValue)
  private val scalaOtherModifiersById = scalaOtherModifiers.groupBySingle(_.getId)

  val scalaModifiers = scalaAccessModifiers ++ scalaOtherModifiers
  val commonModifiers = scalaAccessModifiers + FINAL //TODO: determine if final is common

  private val scalaModifiersByName = scalaAccessModifiersByName ++ scalaOtherModifiersByName
  private val scalaTokensById = scalaAccessModifiersById ++ scalaOtherModifiersById ++ scalaTypesById

  def getModifierByName(modifierName: String): Option[ArrangementSettingsToken] = scalaModifiersByName.get(modifierName)
  def getTokenById(modifierId: String): Option[ArrangementSettingsToken] = scalaTokensById.get(modifierId)

  val tokensForType = HashMap(
    TYPE        -> (commonModifiers + OVERRIDE),
    FUNCTION    -> (commonModifiers + OVERRIDE + IMPLICIT),
    CLASS       -> (commonModifiers + ABSTRACT + SEALED),
    TRAIT       -> (commonModifiers + ABSTRACT + SEALED),
    VAL         -> (commonModifiers + OVERRIDE + LAZY + ABSTRACT),
    VAR         -> (commonModifiers + OVERRIDE),
    MACRO       -> (commonModifiers + OVERRIDE),
    CONSTRUCTOR -> scalaAccessModifiers,
    OBJECT      -> commonModifiers
  )

  private implicit class CollectionExt[A](private val collection: Iterable[A]) extends AnyVal {

    def groupBySingle[T](f: A => T): Map[T, A] = collection.map(x => f(x) -> x).toMap
  }
}
