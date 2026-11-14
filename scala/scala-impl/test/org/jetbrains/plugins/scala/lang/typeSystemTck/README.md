# IntelliJ PSI engine for scala-type-system-tck

This package runs the [scala-type-system-tck](https://github.com/retronym/scala-type-system-tck) corpus against the IntelliJ Scala plugin's PSI type system and compares the answers with scalac's goldens.

The corpus is vendored under `testdata/typeSystemTck/corpus` (copied from the upstream repo, where it is authored and the goldens are regenerated). Override the location with `-Dscala.tck.corpus=...` or the `SCALA_TCK_CORPUS` env var.

It is the "system under test" counterpart to that repo's `ScalacEngine` (the oracle). See the corpus repo's `SPEC.md` for the type-system spec and `§4` for the canonical rendering normal form that makes goldens comparable across engines.

## How it works

`TckCorpus` loads each corpus entry and builds one synthetic compilation unit: the `source.scala` preamble plus a `type __q_<name> = <expr>` alias per query. Anchored queries (`this.type`, self-type, `this.Member`) are spliced at their `/*ANCHOR id*/` marker so they resolve in the right context, as the scalac engine does. `TypeSystemTckTest` configures that text into a light PSI fixture on the Scala 2.13 library, reads each alias's `aliasedType` (an `ScType`), and compares these dimensions:

- conformance: `a.conforms(b)`, against the corpus's ground truth.
- equivalence: `a.equiv(b)`, against the corpus's ground truth.
- baseTypeSeq: `BaseTypes.get(tp)`, rendered and compared as a set.
- baseClasses: `MixinNodes.linearization`, with order.
- termType: the inferred type of a spliced `val __t_<name> = <expr>`.
- baseType: `BaseTypes.baseType(tp, clazz)`.

## Strictness and known differences

The test is strict by default: any difference from scalac fails it, except those listed in `TypeSystemTckTest.Deferred`. That registry is a two-way pin. A new difference fails the test, and so does a listed difference that stops diverging, so that it gets removed from the list. It is the record of what currently differs and why; this file doesn't repeat it.

Conformance and equivalence have no deferrals, and baseType's registry is empty. All three are always hard. `-Dscala.tck.lenient=true` makes the other three dimensions report only, while iterating locally.

### Equivalence

`=:=` is more discriminating than `<:<`: a row can pass `<:<` while `=:=` is wrong. The corpus exercises such cases, for example:

- `12 DogSingleton =:= Dog` is false (the singleton is narrower; `<:<` holds one way).
- `12 BoxedT =:= Dog` is true (`<:<` holds both ways).
- `14 O1Inner =:= Outer#Inner` is false (`<:<` holds upward only).
- `19 ChainedType =:= DirectType` is true (val-path collapse through a refinement).

Entry `28-cake-this-equivalence-soundness` pins two equivalences that look plausible for override matching in the cake and that scalac rejects: `Types.this =:= SymbolTable.this` when self types tie the classes together, and `Global.this =:= p` for any stable path `p` whose declared class is `Global`.

### baseClasses

The linearization is compared with order, after normalizing the conventions that differ: scalac's `<refinement>` head and trailing `scala.Any`, and `Outer#Inner` vs `Outer.Inner` spelling. The type is widened first, so singleton and literal types resolve to their class. `baseTypeSeq` order is a symbol-id sort and so insensitive to mixin order; `baseClasses` carries the mixin order.

### termType

This exercises member resolution, `asSeenFrom` and `ThisTypeSubstitution`. Path prefixes matter here, so the comparison does not collapse `#` and `.` (`Impl.this.Tree` is not `Api#Tree`).

### baseTypeSeq

Two differences from scalac's `baseTypeSeq` are reconciled before comparing:

1. **Order.** The comparison checks membership only.
2. **Reflexivity and Any.** scalac's `baseTypeSeq` is reflexive (`bt0 = T`) and ends with `scala.Any`; `BaseTypes.get` yields only proper, class-extractable base types. So the golden's head and `scala.Any` are dropped first. For a singleton the head is kept, since the widened type is one of its base types, as in scalac.

## Running

The plugin must be packaged first, then the test is run by FQN without a project prefix:

```
sbt
> packageArtifact            # once per session / after main-source changes
> testOnly org.jetbrains.plugins.scala.lang.typeSystemTck.TypeSystemTckTest
```

## Remaining work

- The renderer is `canonicalText` plus the SPEC §4 forms PSI's presentation doesn't produce: dealiased types, top-level existentials as `Q forSome { type _1 >: L <: U }`, and `X.this.p` compared equal to `X.p` (an object's own `this`). Not yet rendered: a refinement's `this.type`, existentials nested below the top level, and `FunctionN`/`TupleN` without sugar. No current comparison depends on these.
- The corpus covers Scala 2.13 only.
