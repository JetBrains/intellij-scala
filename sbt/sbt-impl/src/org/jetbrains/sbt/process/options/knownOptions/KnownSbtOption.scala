package org.jetbrains.sbt.process.options.knownOptions

/**
 * Metadata for one supported sbt option
 *
 * @see [[SbtProcessOptionsResolver]] for the full option pipeline
 * @param spelling   main normalized spelling accepted for this option
 * @param argMapping maps the parsed value, if any, and canonical project path into final process arguments
 * @param aliases    extra spellings for the same option; mainly exists for the short `-d` alias of `--debug`
 */
private[options] final case class KnownSbtOption(
  spelling: KnownSbtOption.Spelling,
  argMapping: KnownSbtOptionArgMapping,
  aliases: Seq[KnownSbtOption.Spelling] = Seq.empty
) {
  /** Main spelling followed by aliases, in diagnostic order. */
  def allSpellings: Seq[KnownSbtOption.Spelling] =
    spelling +: aliases
}

/**
 * Metadata model for recognized option spellings and their value shapes.
 */
private[options] object KnownSbtOption {
  /**
   * A concrete accepted option spelling after text normalization.
   *
   * @param text      normalized spelling matched by the parser, e.g. `-sbt-dir`, `-color=`, or `-debug`
   * @param valueForm whether this spelling is a flag, separate-value option, or inline-value option
   * @param helperMsg suggestion/diagnostic text shown to users for this spelling
   */
  final case class Spelling(
    text: String,
    valueForm: Form,
    helperMsg: String
  )

  /** Value shape required by a spelling. */
  enum Form {
    /** Flag option, e.g. `-timings`. */
    case NoValue

    /** Option with a value in the next token, e.g. `-sbt-dir /tmp/sbt`. */
    case SeparateValue

    /** Option with a value attached to the spelling, e.g. `-color=always`. */
    case InlineValue
  }
}
