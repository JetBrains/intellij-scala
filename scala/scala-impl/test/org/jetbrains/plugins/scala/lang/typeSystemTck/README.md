# IntelliJ PSI engine for scala-type-system-tck

This package runs the [scala-type-system-tck](https://github.com/retronym/scala-type-system-tck)
corpus against the IntelliJ Scala plugin's PSI type system, comparing conformance
and base type sequences against the scalac-generated goldens.

The corpus is vendored under `testdata/typeSystemTck/corpus` (copied from the
upstream repo, where it is authored and the goldens are regenerated). Override the
location with `-Dscala.tck.corpus=...` or the `SCALA_TCK_CORPUS` env var.

It is the "system under test" counterpart to that repo's `ScalacEngine` (the
oracle). See the corpus repo's `SPEC.md` for the type-system spec and `§4` for the
canonical rendering normal form that makes goldens comparable across engines.

## How it works

`TckCorpus` loads each corpus entry and builds one synthetic compilation unit:
the `source.scala` preamble plus a `type __q_<name> = <expr>` alias per query.
Anchored queries (`this.type`, self-type, `this.Member`) are spliced at their
`/*ANCHOR id*/` marker so they resolve in the right context — exactly as the
scalac engine does. `TypeSystemTckTest` configures that text into a light PSI
fixture, reads each alias's `aliasedType` (an `ScType`), and runs:

- `a.conforms(b)` — checked against the corpus's human ground truth (hard).
- `a.equiv(b)` — type equivalence (`=:=`), checked against ground truth (hard).
- `BaseTypes.get(tp)` — rendered and compared **as a set** against the golden.

### Equivalence (`=:=`) — strictly more discriminating than conformance

The `equivalence` dimension mirrors `conformance`: each entry's `equivalence`
queries (`{lhs, rhs, expect}`) are run through IntelliJ's `ScType.equiv` and
checked against the human ground truth (which scalac's `=:=` agrees with by
construction — the oracle regenerates the golden `equivalence` array). It is a hard
assertion, like conformance.

`=:=` is the right guard for the SCL-21947 fixes, which were all about *equivalence*
of singleton / path-dependent types (`ScThisType.equivInner`, override-aware
`designatorSingletonType`, `ConstraintSystem` equiv). It is strictly more
discriminating than `<:<`: a row can pass `<:<` while `=:=` is wrong. The corpus
exercises exactly these discriminating cases, e.g. (all green on this branch):

- `12 DogSingleton =:= Dog` → **false** (singleton narrower; `<:<` holds one way).
- `12 BoxedT =:= Dog` → **true** (both `<:<` directions hold).
- `14 O1Inner =:= Outer#Inner` → **false** (`<:<` holds upward only).
- `16/17 ImplThis =:= Api` / `CompThis =:= Outer` → **false** (self-type `<:<`).
- `19 ChainedType =:= DirectType` → **true** (val-path collapse through refinement).
- `20 ChainedBlock =:= Tree` → **false** (`Block` ≠ `Tree`, though `<:<` holds).

**Known divergence (not in the corpus).** A literal cake `this`-pair —
`SymbolTable.this.T` vs `Types.this.T` with `trait Types { self: SymbolTable => }`
and `class SymbolTable extends Types` — is **not** equal under scalac (it only
canonicalizes cake this-types within one instance, via asSeenFrom / override
matching), yet IntelliJ returns `true` for both `<:<` and `=:=`. The SCL-21947 cake
handling (`ScThisType.equivInner`'s self-type tie + the conformance side) is an
*over-approximation* on directly-written this-types. It is correct where it matters
(override matching — guarded by `OverrideHighlightingTest.testSCL21947Cake`), so this
shape is intentionally **not** committed as a corpus entry (it would be a hard red);
it is recorded here as the one place the equivalence dimension found IntelliJ more
lenient than scalac.

### baseClasses (linearization) — checked ORDER-sensitively

In addition to `baseTypeSeq`, the TCK compares `baseClasses` — the
**linearization** — against scalac's, **order included**. On the IntelliJ side
this is `MixinNodes.linearization(clazz | compound)` (an ordered `Seq[ScType]`);
the type is `widen`ed first (so singleton/literal types resolve to their class),
and comparison normalizes the conventions that differ (scalac's `<refinement>`
head and trailing `scala.Any`, and `Outer#Inner` vs `Outer.Inner` spelling).

This is the real residual-ordering surface: `baseTypeSeq` order is a symbol-id
sort (mixin-INsensitive), whereas `baseClasses` carries the mixin order.

**Result:** IntelliJ's linearization order **matches scalac across all 16 entries**
— including the diamond (`D` = `B with C` → `D,C,B,A` vs `D2` = `C with B` →
`D2,B,C,A`), variance, higher-kinded, F-bounds, and the same-symbol merge — with
**one** exception: `04 AnimalBoxThis` (`AnimalBox.this.type`), where the
linearization drops the self-type's `Animal`. So the residual "order differs from
scalac" concern is **not** a linearization-order bug; it is (a) `BaseTypes.get`
being unordered, and (b) base-types/linearization handling around `ScThisType`
(the SCL-21947 / PR #663 area). `-Dscala.tck.strictBc=true` hardens this check.

### Term probes (memberType / asSeenFrom / ThisTypeSubstitution)

A corpus entry may declare `termTypes`: each splices `val __t_<name> = <expr>`
(optionally at an `/*ANCHOR*/`) and the TCK reads its **inferred type**, comparing
to scalac's. This exercises member resolution, `asSeenFrom`, and
`ThisTypeSubstitution` — the path `BaseTypes.iterator` feeds (`ScalaConformance:77`,
`ThisTypeSubstitution:39`, `TypeDefinitionMembers:567`). Path prefixes matter here,
so the comparison does **not** collapse `#`↔`.` (`Impl.this.Tree` ≠ `Api#Tree`).
`-Dscala.tck.strictTt=true` hardens it.

**Result (this branch):** `0` termType divergences, including the SCL-21947 shapes:
- `16` member on a top-level self-type trait (`clazz.containingClass == null`, plain
  `doUpdateThisType` branch) → `Impl.this.Tree`. ✓
- `17` member in a NESTED class whose result type refers to the outer `this`
  (`clazz.containingClass != null`, reaches the `BaseTypes.iterator(target).find(...)`
  walk at `ThisTypeSubstitution:39`) → `Comp.this.Tree`. ✓

So `ThisTypeSubstitution`'s `seenFromClass` walk is effective on this branch. The
`BaseTypes` change in PR #663 (`getTypeWithProjections()` → `clazz.type()`, the
this-projection prefix `containingClassType` needs) is what un-thwarts it. The
*separate* `ScThisType` self-type omission remains (the one `baseClasses` diff,
`04 AnimalBoxThis`) but is latent under member resolution / conformance, masked by
their own self-type fallbacks.

### Why baseTypeSeq is compared as a set of *proper supers*

Two differences from scalac's `baseTypeSeq` must be reconciled:

1. **Order.** `BaseTypes.get` returns `res.values.toList` off a `HashMap` — it does
   **not** preserve linearization order, so we can only check membership, not
   order. Checking order against scalac needs an order-preserving base-type API;
   that gap is the residual issue tracked for SCL-21585 / SCL-21947.
2. **Reflexivity / Any.** scalac's `baseTypeSeq` is reflexive (`bt0 = T`) and ends
   with `scala.Any`; `BaseTypes.get` yields only proper, class-extractable base
   types. So we compare against the golden's **proper supers** = the golden minus
   its head (the type itself) and minus `scala.Any`.

With that alignment the substitution results agree with scalac across the corpus
(see findings below).

### Current findings (branch `scala-typesystem-tck`)

Conformance: **0** divergences across all 6 entries (including the SCL-21585 /
SCL-21947 shapes). baseTypeSeq proper-supers: **2** divergences, both about
base-type *enumeration* (not conformance):

- `04 AnimalBoxThis` — `BaseTypes.get(AnimalBox.this.type)` returns only
  `java.lang.Object`; it does not surface the self-type's contributed bases
  (`Animal`, `AnimalBox`), even though `AnimalBox.this.type <:< Animal` holds.
  This is base-types handling around `ScThisType` — the area PR #663 targets.
- `05 Combined` — for `(B { type T = Repro }) with M_A[M { type A = B }]`,
  `BaseTypes.get` keeps `B, Base, Object` but drops the intermediate refinement
  base `B { type T = Repro }`.

These two are the concrete work items the TCK currently isolates.

## Running a single test (sbt)

Per the repo README, the plugin must be packaged once, then run by FQN without a
project prefix:

```
sbt
> packageArtifact            # once per session / after main-source changes
> testOnly org.jetbrains.plugins.scala.lang.typeSystemTck.TypeSystemTckTest
```

Useful flags (JVM system properties), e.g. via `-D` in the sbt invocation:

- `-Dscala.tck.corpus=/path/to/corpus` — override the corpus location
  (default: vendored `testdata/typeSystemTck/corpus`).
- `-Dscala.tck.strictBts=true` — also fail the test on baseTypeSeq membership
  diffs (default: report only; conformance always hard).

To iterate on just the test sources without re-packaging:

```
sbt "scala-impl/Test/compile"
```

## Status / next steps

- Scaffold: conformance is a hard assertion; baseTypeSeq is a reported set diff.
- TODO: order-preserving base-type comparison once an ordered API exists.
- TODO: tighten the renderer (currently a normalization of `canonicalText`) to
  fully match SPEC §4 for refinements / existentials.
- Corpus is vendored from https://github.com/retronym/scala-type-system-tck;
  refresh by re-copying `corpus/` after regenerating its goldens.
