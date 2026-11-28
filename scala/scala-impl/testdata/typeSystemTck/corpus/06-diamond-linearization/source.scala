// Concept: class linearization via diamond inheritance, and `with`-order
// sensitivity. NOTE the key distinction this entry pins down (verified against
// scalac):
//   - `baseClasses` (the linearization L(C)) IS mixin-order sensitive:
//       D  (B with C) -> D,  C, B, A, Object, Any
//       D2 (C with B) -> D2, B, C, A, Object, Any
//   - `baseTypeSeq` is ordered by a symbol-id total order consistent with
//     subtyping, NOT the linearization, so D and D2 have the SAME baseTypeSeq
//     order (D/D2, B, C, A, Object, Any).
// The goldens below capture baseTypeSeq. Capturing the linearization-order
// divergence (the real residual-ordering surface) needs a `baseClasses` query —
// see PLAN.md / SPEC §2.
trait A
trait B extends A
trait C extends A
trait D extends B with C
trait D2 extends C with B
