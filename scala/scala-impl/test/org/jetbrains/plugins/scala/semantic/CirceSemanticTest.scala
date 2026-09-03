package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.scalaVersion
import org.junit.Test

class CirceSemanticTest extends SemanticTestBase("io.circe" %% "circe-core" % "0.14.15", "io.circe" %% "circe-generic" % "0.14.15", "io.circe" %% "circe-parser" % "0.14.15")("io.circe") {
//  @Test def single(): Unit = doTest("")

  @Test def test(): Unit = doTest("""
    //io.circe.ACursor
    //io.circe.BiggerDecimalJsonNumber
    //io.circe.Codec
    //io.circe.CodecDerivation
    //io.circe.CodecDerivationRelaxed
    //io.circe.CollectionDecoders
    io.circe.CompatBuilder
    //io.circe.CursorOp
    //io.circe.Decoder
    //io.circe.DecoderDerivation
    //io.circe.DecodingFailure
    //io.circe.Derivation
    //io.circe.DerivedDecoder
    //io.circe.DerivedEncoder
    //io.circe.DerivedInstance
    //io.circe.Encoder
    //io.circe.EncoderDerivation
    //io.circe.EncoderDerivationRelaxed
    //io.circe.EnumerationCodecs
    //io.circe.EnumerationDecoders
    io.circe.EnumerationEncoders
    //io.circe.Error
    //io.circe.Errors
    io.circe.FailedCursor
    //io.circe.HCursor
    //io.circe.Json
    //io.circe.JsonBigDecimal
    io.circe.JsonBiggerDecimal
    io.circe.JsonDecimal
    //io.circe.JsonDouble
    //io.circe.JsonFloat
    //io.circe.JsonLong
    //io.circe.JsonNumber
    //io.circe.JsonObject
    //io.circe.KeyDecoder
    io.circe.KeyEncoder
    //io.circe.LiteralDecoders
    //io.circe.LiteralEncoders
    io.circe.LowPriorityCollectionDecoders
    //io.circe.LowPriorityDecoders
    //io.circe.LowPriorityEncoders
    //io.circe.MapDecoder
    //io.circe.MidPriorityEncoders
    //io.circe.NonEmptySeqDecoder
    //io.circe.Parser
    //io.circe.ParsingFailure
    //io.circe.PathToRoot
    //io.circe.Printer
    io.circe.ProductCodecs
    io.circe.ProductDecoders
    io.circe.ProductEncoders
    //io.circe.ProductTypedCodecs
    //io.circe.ProductTypedEncoders
    //io.circe.SeqDecoder
    //io.circe.TupleDecoders
    //io.circe.TupleEncoders
    //io.circe.`export`.Exported
    //io.circe.cursor.ArrayCursor
    io.circe.cursor.ObjectCursor
    io.circe.cursor.TopCursor
    //io.circe.derivation.Configuration
    //io.circe.derivation.ConfiguredCodec
    //io.circe.derivation.ConfiguredDecoder
    //io.circe.derivation.ConfiguredEncoder
    //io.circe.derivation.ConfiguredEnumCodec
    //io.circe.derivation.ConfiguredEnumDecoder
    //io.circe.derivation.ConfiguredEnumEncoder
    //io.circe.derivation.DecoderDeriveSum
    //io.circe.derivation.DecoderNotDeriveSum
    //io.circe.derivation.Default
    //io.circe.derivation.EncoderDeriveSum
    //io.circe.derivation.EncoderNotDeriveSum
    //io.circe.derivation.Inliner
    io.circe.derivation.SingletonCase
    io.circe.derivation.SumOrProduct
    //io.circe.derivation.SummonSingleton
    //io.circe.derivation.constString
    //io.circe.derivation.renaming
    io.circe.disjunctionCodecs
    //io.circe.generic.AutoDerivation
    io.circe.generic.auto
    //io.circe.generic.semiauto
    //io.circe.jawn.CirceSupportParser
    //io.circe.jawn.JawnParser
    //io.circe.jawn.JawnParserPlatform
    //io.circe.numbers.BiggerDecimal
    //io.circe.numbers.SigAndExp
  """)
}