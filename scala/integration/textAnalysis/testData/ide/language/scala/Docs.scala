/**
 * A group of *members*.
 * <p>
 * This class has no useful logic; it's just a documentation example.
 *
 * This is thought to be a great news, e.g. a new invention.
 *
 * A code example:
 * <code>
 *   val item = env.generateData(Generator.sampledFrom(sys.currentItems), "working on %s item");
 * </code>
 *
 * @constructor Creates an empty group. It's a <b>react</b> method.
 * @param name The name of the group. And another sentence.
 * @param T the type of member in this group. And another sentence.
 *
 */
class ExampleClassWithNoTypos[T](private var name: String) {

  /**
   * Adds a `member` to this group.
   *
   * @param cancellable Whether the progress can be <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">cancelled</STYLE_SUGGESTION>.
   * @param member member to add
   * @return the new size of the group. And another sentence.
   */
  def goodFunction(cancellable: Boolean, member: T): Int = {
    1 // no error comment
  }

  /**
   * Accepts files for which vcs operations are temporarily blocked.
   * @return the project instance.
   */
  val some1: Any = 42

  /** Currently active change list. */
  class ActiveChangeList {}

  /**
   * The finally block always executes when the try block exits.
   */
  def finallyBlockTest(): Unit = {}
}

/**
 * It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend there
 *
 * </unopenedTag>
 *
 * @constructor Creates an empty group. It's a <GRAMMAR_ERROR descr="A_GOOGLE">react</GRAMMAR_ERROR> method.
 * @param name the <GRAMMAR_ERROR descr="COMMA_WHICH">name which</GRAMMAR_ERROR> group
 * @param T the <GRAMMAR_ERROR descr="KIND_OF_A">type of a</GRAMMAR_ERROR> <TYPO descr="Typo: In word 'membr'">membr</TYPO> in this group.
 */
class ExampleClassWithTypos[T](name: String) {

  /**
   * There can be many mistakes here. It <GRAMMAR_ERROR descr="IT_VBZ">add</GRAMMAR_ERROR><br>
   *
   * <b>It <GRAMMAR_ERROR descr="IT_VBZ">add</GRAMMAR_ERROR></b>
   */
  private var myName = name

  /**
   * It <GRAMMAR_ERROR descr="IT_VBZ">add</GRAMMAR_ERROR> a [member] to this <TYPO descr="Typo: In word 'grooup'">grooup</TYPO>.
   * <GRAMMAR_ERROR descr="UPPERCASE_SENTENCE_START">second</GRAMMAR_ERROR> sentence.
   *
   * @param member member to add. And another sentence.
   * @return the new size of <GRAMMAR_ERROR descr="DT_DT">a the</GRAMMAR_ERROR> group. <GRAMMAR_ERROR descr="UPPERCASE_SENTENCE_START">and</GRAMMAR_ERROR> another sentence.
   */
  def badFunction(member: T): Int = {
    1 // It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> <TYPO descr="Typo: In word 'eror'">eror</TYPO> in the comment
  }
}

/**
 * В коробке лежало <GRAMMAR_ERROR descr="Sklonenije_NUM_NN">пять карандаша</GRAMMAR_ERROR>.
 * А <GRAMMAR_ERROR descr="grammar_vse_li_noun">все ли ошибка</GRAMMAR_ERROR> найдены?
 * Это случилось <GRAMMAR_ERROR descr="INVALID_DATE">31 ноября</GRAMMAR_ERROR> 2014 г.
 * За весь вечер она <GRAMMAR_ERROR descr="ne_proronila_ni">не проронила и слово</GRAMMAR_ERROR>.
 * Собрание состоится в <GRAMMAR_ERROR descr="Grazie.RuleEngine.Ru.Spelling.MULTI_WORD">конференц зале</GRAMMAR_ERROR>.
 * <GRAMMAR_ERROR descr="WORD_REPEAT_RULE">Он он</GRAMMAR_ERROR> ошибка.
 */
// noinspection SqlDialectInspection das daert geschätzt fünf Mannstunden! А еще в коробке лежало пять карандаша
class ForMultiLanguageSupport {
  // er überprüfte die Rechnungen noch <TYPO descr="Typo: In word 'einal'">einal</TYPO>, um <GRAMMAR_ERROR descr="COMPOUND_INFINITIV_RULE">sicher zu gehen</GRAMMAR_ERROR>.
  // das ist <GRAMMAR_ERROR descr="FUEHR_FUER">führ</GRAMMAR_ERROR> Dich!
  // das <TYPO descr="Typo: In word 'daert'">daert</TYPO> geschätzt fünf <STYLE_SUGGESTION descr="MANNSTUNDE">Mannstunden</STYLE_SUGGESTION>.

  /**
   * @throws Exception wenn ein Fehler auftritt
   */
  @throws[Exception]
  def main() {
    throw new Exception("Hello World");
  }
}
