// Concept: when does lub keep a type member that every operand defines? scalac's
// lub is lubRefined: after the least common base class it may add a refinement
// member for each name the operands share (Types.lubsym). Here it keeps
// `type Pos = String` when both operands alias Pos to String, but drops the member
// (plain `Attachments`) when both alias it to the abstract `P`, and when they alias
// it differently. Shape from scala/scala reflect/macros/Attachments.scala, where
// `if (...) new SingleAttachment[Pos] else new NonemptyAttachments[Pos]` is checked
// against an expected `Attachments { type Pos = self.Pos }`: typedIf then takes the
// expected type rather than the lub, so each branch must conform to the refinement.
abstract class Attachments { type Pos >: Null }
final class SingleAttachment[P >: Null] extends Attachments { type Pos = P }
final class NonemptyAttachments[P >: Null] extends Attachments { type Pos = P }
trait Use[P >: Null] {
  val c: Boolean
  /*ANCHOR inUse*/
}
