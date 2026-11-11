package io.bullet.borer.derivation

trait DerivationApi {
  inline def deriveEncoder[T]: io.bullet.borer.Encoder[T]

  inline def deriveAllEncoders[T]: io.bullet.borer.Encoder[T]

  inline def deriveDecoder[T]: io.bullet.borer.Decoder[T]

  inline def deriveAllDecoders[T]: io.bullet.borer.Decoder[T]

  inline def deriveCodec[T]: io.bullet.borer.Codec[T]

  inline def deriveAllCodecs[T]: io.bullet.borer.Codec[T]

  extension (c: io.bullet.borer.Encoder.type)
    inline def derived[A]: io.bullet.borer.Encoder[A] = ???

  extension (c: io.bullet.borer.Decoder.type)
    inline def derived[A]: io.bullet.borer.Decoder[A] = ???

  extension (c: io.bullet.borer.Codec.type)
    inline def derived[A]: io.bullet.borer.Codec[A] = ???

  extension (c: io.bullet.borer.Encoder.All.type)
    inline def derived[A]: io.bullet.borer.Encoder.All[A] = ???

  extension (c: io.bullet.borer.Decoder.All.type)
    inline def derived[A]: io.bullet.borer.Decoder.All[A] = ???

  extension (c: io.bullet.borer.Codec.All.type)
    inline def derived[A]: io.bullet.borer.Codec.All[A] = ???
}