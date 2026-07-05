// Skeletor extraction (retronym/skeletor) from the compiled scala/scala classes, seeded at scala.tools.nsc.typechecker.Typers,
// scala.reflect.internal.Definitions#DefinitionsClass, scala.reflect.api.StandardDefinitions.
// Self-compile verified against the real (non-stubbed) classpath. Reproduces the exact
// real-world cake for `global.definitions.AnyTpe: global.Type` inside trait Typers
// (self: Analyzer =>) - the ascription line is inserted at the top of `trait Typers`
// below.
package scala.reflect {
  trait ClassManifestDeprecatedApis[T] extends scala.reflect.OptManifest[T] { self: scala.reflect.ClassManifest[T] =>

  }

  trait ClassTag[T] extends scala.reflect.ClassManifestDeprecatedApis[T] with scala.Equals with scala.Serializable

  trait OptManifest[+T] extends scala.Serializable

  object `package` {
    type ClassManifest[T] = scala.reflect.ClassTag[T]
  }

}
package scala.reflect.api {
  trait Annotations { self: scala.reflect.api.Universe =>
    type Annotation >: scala.Null <: Annotations.this.AnnotationApi
    abstract class AnnotationExtractor()
    trait AnnotationApi
    type JavaArgument >: scala.Null <: Annotations.this.JavaArgumentApi
    trait JavaArgumentApi
  }

  trait Constants { self: scala.reflect.api.Universe =>
    type Constant >: scala.Null <: Constants.this.ConstantApi
    abstract class ConstantExtractor()
    abstract class ConstantApi()
  }

  trait Exprs { self: scala.reflect.api.Universe =>

  }

  trait FlagSets { self: scala.reflect.api.Universe =>
    type FlagSet
    trait FlagOps extends scala.Any
    implicit def addFlagOps(left: FlagSets.this.FlagSet): FlagSets.this.FlagOps
    trait FlagValues
  }

  trait ImplicitTags { self: scala.reflect.api.Universe =>
    implicit val AnnotatedTypeTag: scala.reflect.ClassTag[ImplicitTags.this.AnnotatedType]
    implicit val BoundedWildcardTypeTag: scala.reflect.ClassTag[ImplicitTags.this.BoundedWildcardType]
    implicit val ClassInfoTypeTag: scala.reflect.ClassTag[ImplicitTags.this.ClassInfoType]
    implicit val CompoundTypeTag: scala.reflect.ClassTag[ImplicitTags.this.CompoundType]
    implicit val ConstantTypeTag: scala.reflect.ClassTag[ImplicitTags.this.ConstantType]
    implicit val ExistentialTypeTag: scala.reflect.ClassTag[ImplicitTags.this.ExistentialType]
    implicit val MethodTypeTag: scala.reflect.ClassTag[ImplicitTags.this.MethodType]
    implicit val NullaryMethodTypeTag: scala.reflect.ClassTag[ImplicitTags.this.NullaryMethodType]
    implicit val PolyTypeTag: scala.reflect.ClassTag[ImplicitTags.this.PolyType]
    implicit val RefinedTypeTag: scala.reflect.ClassTag[ImplicitTags.this.RefinedType]
    implicit val SingleTypeTag: scala.reflect.ClassTag[ImplicitTags.this.SingleType]
    implicit val SingletonTypeTag: scala.reflect.ClassTag[ImplicitTags.this.SingletonType]
    implicit val SuperTypeTag: scala.reflect.ClassTag[ImplicitTags.this.SuperType]
    implicit val ThisTypeTag: scala.reflect.ClassTag[ImplicitTags.this.ThisType]
    implicit val TypeBoundsTag: scala.reflect.ClassTag[ImplicitTags.this.TypeBounds]
    implicit val TypeRefTag: scala.reflect.ClassTag[ImplicitTags.this.TypeRef]
    implicit val TypeTagg: scala.reflect.ClassTag[ImplicitTags.this.Type]
    implicit val NameTag: scala.reflect.ClassTag[ImplicitTags.this.Name]
    implicit val TermNameTag: scala.reflect.ClassTag[ImplicitTags.this.TermName]
    implicit val TypeNameTag: scala.reflect.ClassTag[ImplicitTags.this.TypeName]
    implicit val ScopeTag: scala.reflect.ClassTag[ImplicitTags.this.Scope]
    implicit val MemberScopeTag: scala.reflect.ClassTag[ImplicitTags.this.MemberScope]
    implicit val AnnotationTag: scala.reflect.ClassTag[ImplicitTags.this.Annotation]
    implicit val JavaArgumentTag: scala.reflect.ClassTag[ImplicitTags.this.JavaArgument]
    implicit val TermSymbolTag: scala.reflect.ClassTag[ImplicitTags.this.TermSymbol]
    implicit val MethodSymbolTag: scala.reflect.ClassTag[ImplicitTags.this.MethodSymbol]
    implicit val SymbolTag: scala.reflect.ClassTag[ImplicitTags.this.Symbol]
    implicit val TypeSymbolTag: scala.reflect.ClassTag[ImplicitTags.this.TypeSymbol]
    implicit val ModuleSymbolTag: scala.reflect.ClassTag[ImplicitTags.this.ModuleSymbol]
    implicit val ClassSymbolTag: scala.reflect.ClassTag[ImplicitTags.this.ClassSymbol]
    implicit val PositionTag: scala.reflect.ClassTag[ImplicitTags.this.Position]
    implicit val ConstantTag: scala.reflect.ClassTag[ImplicitTags.this.Constant]
    implicit val FlagSetTag: scala.reflect.ClassTag[ImplicitTags.this.FlagSet]
    implicit val ModifiersTag: scala.reflect.ClassTag[ImplicitTags.this.Modifiers]
    implicit val AlternativeTag: scala.reflect.ClassTag[ImplicitTags.this.Alternative]
    implicit val AnnotatedTag: scala.reflect.ClassTag[ImplicitTags.this.Annotated]
    implicit val AppliedTypeTreeTag: scala.reflect.ClassTag[ImplicitTags.this.AppliedTypeTree]
    implicit val ApplyTag: scala.reflect.ClassTag[ImplicitTags.this.Apply]
    implicit val NamedArgTag: scala.reflect.ClassTag[ImplicitTags.this.NamedArg]
    implicit val AssignTag: scala.reflect.ClassTag[ImplicitTags.this.Assign]
    implicit val BindTag: scala.reflect.ClassTag[ImplicitTags.this.Bind]
    implicit val BlockTag: scala.reflect.ClassTag[ImplicitTags.this.Block]
    implicit val CaseDefTag: scala.reflect.ClassTag[ImplicitTags.this.CaseDef]
    implicit val ClassDefTag: scala.reflect.ClassTag[ImplicitTags.this.ClassDef]
    implicit val CompoundTypeTreeTag: scala.reflect.ClassTag[ImplicitTags.this.CompoundTypeTree]
    implicit val DefDefTag: scala.reflect.ClassTag[ImplicitTags.this.DefDef]
    implicit val DefTreeTag: scala.reflect.ClassTag[ImplicitTags.this.DefTree]
    implicit val ExistentialTypeTreeTag: scala.reflect.ClassTag[ImplicitTags.this.ExistentialTypeTree]
    implicit val FunctionTag: scala.reflect.ClassTag[ImplicitTags.this.Function]
    implicit val GenericApplyTag: scala.reflect.ClassTag[ImplicitTags.this.GenericApply]
    implicit val IdentTag: scala.reflect.ClassTag[ImplicitTags.this.Ident]
    implicit val IfTag: scala.reflect.ClassTag[ImplicitTags.this.If]
    implicit val ImplDefTag: scala.reflect.ClassTag[ImplicitTags.this.ImplDef]
    implicit val ImportSelectorTag: scala.reflect.ClassTag[ImplicitTags.this.ImportSelector]
    implicit val ImportTag: scala.reflect.ClassTag[ImplicitTags.this.Import]
    implicit val LabelDefTag: scala.reflect.ClassTag[ImplicitTags.this.LabelDef]
    implicit val LiteralTag: scala.reflect.ClassTag[ImplicitTags.this.Literal]
    implicit val MatchTag: scala.reflect.ClassTag[ImplicitTags.this.Match]
    implicit val MemberDefTag: scala.reflect.ClassTag[ImplicitTags.this.MemberDef]
    implicit val ModuleDefTag: scala.reflect.ClassTag[ImplicitTags.this.ModuleDef]
    implicit val NameTreeTag: scala.reflect.ClassTag[ImplicitTags.this.NameTree]
    implicit val NewTag: scala.reflect.ClassTag[ImplicitTags.this.New]
    implicit val PackageDefTag: scala.reflect.ClassTag[ImplicitTags.this.PackageDef]
    implicit val RefTreeTag: scala.reflect.ClassTag[ImplicitTags.this.RefTree]
    implicit val ReturnTag: scala.reflect.ClassTag[ImplicitTags.this.Return]
    implicit val SelectFromTypeTreeTag: scala.reflect.ClassTag[ImplicitTags.this.SelectFromTypeTree]
    implicit val SelectTag: scala.reflect.ClassTag[ImplicitTags.this.Select]
    implicit val SingletonTypeTreeTag: scala.reflect.ClassTag[ImplicitTags.this.SingletonTypeTree]
    implicit val StarTag: scala.reflect.ClassTag[ImplicitTags.this.Star]
    implicit val SuperTag: scala.reflect.ClassTag[ImplicitTags.this.Super]
    implicit val SymTreeTag: scala.reflect.ClassTag[ImplicitTags.this.SymTree]
    implicit val TemplateTag: scala.reflect.ClassTag[ImplicitTags.this.Template]
    implicit val TermTreeTag: scala.reflect.ClassTag[ImplicitTags.this.TermTree]
    implicit val ThisTag: scala.reflect.ClassTag[ImplicitTags.this.This]
    implicit val ThrowTag: scala.reflect.ClassTag[ImplicitTags.this.Throw]
    implicit val TreeTag: scala.reflect.ClassTag[ImplicitTags.this.Tree]
    implicit val TryTag: scala.reflect.ClassTag[ImplicitTags.this.Try]
    implicit val TypTreeTag: scala.reflect.ClassTag[ImplicitTags.this.TypTree]
    implicit val TypeApplyTag: scala.reflect.ClassTag[ImplicitTags.this.TypeApply]
    implicit val TypeBoundsTreeTag: scala.reflect.ClassTag[ImplicitTags.this.TypeBoundsTree]
    implicit val TypeDefTag: scala.reflect.ClassTag[ImplicitTags.this.TypeDef]
    implicit val TypeTreeTag: scala.reflect.ClassTag[ImplicitTags.this.TypeTree]
    implicit val TypedTag: scala.reflect.ClassTag[ImplicitTags.this.Typed]
    implicit val UnApplyTag: scala.reflect.ClassTag[ImplicitTags.this.UnApply]
    implicit val ValDefTag: scala.reflect.ClassTag[ImplicitTags.this.ValDef]
    implicit val ValOrDefDefTag: scala.reflect.ClassTag[ImplicitTags.this.ValOrDefDef]
    implicit val TreeCopierTag: scala.reflect.ClassTag[ImplicitTags.this.TreeCopier]
    implicit val RuntimeClassTag: scala.reflect.ClassTag[ImplicitTags.this.RuntimeClass]
    implicit val MirrorTag: scala.reflect.ClassTag[ImplicitTags.this.Mirror]
  }

  trait Internals { self: scala.reflect.api.Universe =>
    trait InternalApi
    trait ReificationSupportApi
    type ReferenceToBoxed >: scala.Null <: Internals.this.ReferenceToBoxedApi with Internals.this.TermTree
    abstract class ReferenceToBoxedExtractor()
    trait ReferenceToBoxedApi extends Internals.this.TermTreeApi { self: Internals.this.ReferenceToBoxed =>

    }
    implicit val ReferenceToBoxedTag: scala.reflect.ClassTag[Internals.this.ReferenceToBoxed]
    type FreeTermSymbol >: scala.Null <: Internals.this.FreeTermSymbolApi with Internals.this.TermSymbol
    trait FreeTermSymbolApi extends Internals.this.TermSymbolApi { self: Internals.this.FreeTermSymbol =>

    }
    implicit val FreeTermSymbolTag: scala.reflect.ClassTag[Internals.this.FreeTermSymbol]
    type FreeTypeSymbol >: scala.Null <: Internals.this.FreeTypeSymbolApi with Internals.this.TypeSymbol
    trait FreeTypeSymbolApi extends Internals.this.TypeSymbolApi { self: Internals.this.FreeTypeSymbol =>

    }
    implicit val FreeTypeSymbolTag: scala.reflect.ClassTag[Internals.this.FreeTypeSymbol]
    class CompatToken()
    trait CompatApi {
      implicit val token: Internals.this.CompatToken = ???
      class CompatibleBuildApi(api: Internals.this.ReificationSupportApi)
      class CompatibleTree(tree: Internals.this.Tree)
      class CompatibleSymbol(symbol: Internals.this.Symbol)
    }
  }

  trait Liftables { self: scala.reflect.api.Universe =>

  }

  abstract class Mirror[U <: scala.reflect.api.Universe with scala.Singleton]()

  trait Mirrors { self: scala.reflect.api.Universe =>
    type Mirror >: scala.Null <: scala.reflect.api.Mirror[Mirrors.this.type]
    type RuntimeClass >: scala.Null <: scala.AnyRef
  }

  trait Names {
    type Name >: scala.Null <: Names.this.NameApi
    type TypeName >: scala.Null <: Names.this.TypeNameApi with Names.this.Name
    trait TypeNameApi
    type TermName >: scala.Null <: Names.this.TermNameApi with Names.this.Name
    trait TermNameApi
    abstract class NameApi()
    abstract class TermNameExtractor()
    abstract class TypeNameExtractor()
  }

  trait Position extends scala.reflect.macros.Attachments

  trait Positions { self: scala.reflect.api.Universe =>
    type Position >: scala.Null <: scala.reflect.api.Position{type Pos = Positions.this.Position}
  }

  trait Printers { self: scala.reflect.api.Universe =>
    protected trait TreePrinter
    protected def treeToString(tree: Printers.this.Tree): scala.Predef.String = ???
  }

  trait Quasiquotes { self: scala.reflect.api.Universe =>
    class Quasiquote(ctx: scala.StringContext)
  }

  trait Scopes { self: scala.reflect.api.Universe =>
    type Scope >: scala.Null <: Scopes.this.ScopeApi
    trait ScopeApi extends scala.Iterable[Scopes.this.Symbol]
    type MemberScope >: scala.Null <: Scopes.this.MemberScopeApi with Scopes.this.Scope
    trait MemberScopeApi extends Scopes.this.ScopeApi
  }

  trait StandardDefinitions { self: scala.reflect.api.Universe =>
    val definitions: StandardDefinitions.this.DefinitionsApi
    trait DefinitionsApi extends StandardDefinitions.this.StandardTypes {
      abstract class VarArityClassApi() extends scala.Function1[scala.Int,StandardDefinitions.this.Symbol]
    }
    trait StandardTypes
  }

  trait StandardLiftables { self: scala.reflect.api.Universe =>

  }

  trait StandardNames { self: scala.reflect.api.Universe =>
    trait NamesApi
    trait TermNamesApi extends StandardNames.this.NamesApi {
      type NameType = StandardNames.this.TermName
    }
    trait TypeNamesApi extends StandardNames.this.NamesApi {
      type NameType = StandardNames.this.TypeName
    }
  }

  trait Symbols { self: scala.reflect.api.Universe =>
    type Symbol >: scala.Null <: Symbols.this.SymbolApi
    type TypeSymbol >: scala.Null <: Symbols.this.TypeSymbolApi with Symbols.this.Symbol
    type TermSymbol >: scala.Null <: Symbols.this.TermSymbolApi with Symbols.this.Symbol
    type MethodSymbol >: scala.Null <: Symbols.this.MethodSymbolApi with Symbols.this.TermSymbol
    type ModuleSymbol >: scala.Null <: Symbols.this.ModuleSymbolApi with Symbols.this.TermSymbol
    type ClassSymbol >: scala.Null <: Symbols.this.ClassSymbolApi with Symbols.this.TypeSymbol
    trait SymbolApi { self: Symbols.this.Symbol =>
      type NameType >: scala.Null <: Symbols.this.Name
    }
    trait TermSymbolApi extends Symbols.this.SymbolApi { self: Symbols.this.TermSymbol with Symbols.this.TermSymbolApi =>
      final type NameType = Symbols.this.TermName
      def isAccessor: scala.Boolean
      def isGetter: scala.Boolean
      def isSetter: scala.Boolean
      def isOverloaded: scala.Boolean
    }
    trait TypeSymbolApi extends Symbols.this.SymbolApi { self: Symbols.this.TypeSymbol =>
      final type NameType = Symbols.this.TypeName
      def isContravariant: scala.Boolean
      def isCovariant: scala.Boolean
      def isAliasType: scala.Boolean
      def isAbstractType: scala.Boolean
      def typeParams: scala.List[Symbols.this.Symbol]
    }
    trait MethodSymbolApi extends Symbols.this.TermSymbolApi { self: Symbols.this.MethodSymbol =>
      def isVarargs: scala.Boolean
      def returnType: Symbols.this.Type
      def exceptions: scala.List[Symbols.this.Symbol]
    }
    trait ModuleSymbolApi extends Symbols.this.TermSymbolApi { self: Symbols.this.ModuleSymbol =>
      def moduleClass: Symbols.this.Symbol
    }
    trait ClassSymbolApi extends Symbols.this.TypeSymbolApi { self: Symbols.this.ClassSymbol =>
      def isPrimitive: scala.Boolean
      def isNumeric: scala.Boolean
      def isTrait: scala.Boolean
      def isAbstractClass: scala.Boolean
      def isCaseClass: scala.Boolean
      def primaryConstructor: Symbols.this.Symbol
    }
  }

  trait Trees { self: scala.reflect.api.Universe =>
    type Tree >: scala.Null <: Trees.this.TreeApi
    trait TreeApi extends scala.Product { self: Trees.this.Tree =>
      def orElse(alt: => Trees.this.Tree): Trees.this.Tree
      def foreach(f: scala.Function1[Trees.this.Tree,scala.Unit]): scala.Unit
      def withFilter(f: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.List[Trees.this.Tree]
      def filter(f: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.List[Trees.this.Tree]
      def collect[T](pf: scala.PartialFunction[Trees.this.Tree,T]): scala.List[T]
      def find(p: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.Option[Trees.this.Tree]
      def exists(p: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.Boolean
      def forAll(p: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.Boolean
      def equalsStructure(that: Trees.this.Tree): scala.Boolean
      def children: scala.List[Trees.this.Tree]
      def duplicate: TreeApi.this.type
    }
    type TermTree >: scala.Null <: Trees.this.TermTreeApi with Trees.this.Tree
    trait TermTreeApi extends Trees.this.TreeApi { self: Trees.this.TermTree =>

    }
    type TypTree >: scala.Null <: Trees.this.TypTreeApi with Trees.this.Tree
    trait TypTreeApi extends Trees.this.TreeApi { self: Trees.this.TypTree =>

    }
    type SymTree >: scala.Null <: Trees.this.SymTreeApi with Trees.this.Tree
    trait SymTreeApi extends Trees.this.TreeApi { self: Trees.this.SymTree =>
      def symbol: Trees.this.Symbol
    }
    type NameTree >: scala.Null <: Trees.this.NameTreeApi with Trees.this.Tree
    trait NameTreeApi extends Trees.this.TreeApi { self: Trees.this.NameTree =>

    }
    type RefTree >: scala.Null <: Trees.this.RefTreeApi with Trees.this.SymTree with Trees.this.NameTree
    trait RefTreeApi extends Trees.this.SymTreeApi with Trees.this.NameTreeApi { self: Trees.this.RefTree =>

    }
    abstract class RefTreeExtractor()
    type DefTree >: scala.Null <: Trees.this.DefTreeApi with Trees.this.SymTree with Trees.this.NameTree
    trait DefTreeApi extends Trees.this.SymTreeApi with Trees.this.NameTreeApi { self: Trees.this.DefTree =>

    }
    type MemberDef >: scala.Null <: Trees.this.MemberDefApi with Trees.this.DefTree
    trait MemberDefApi extends Trees.this.DefTreeApi { self: Trees.this.MemberDef =>

    }
    type PackageDef >: scala.Null <: Trees.this.PackageDefApi with Trees.this.MemberDef
    abstract class PackageDefExtractor()
    trait PackageDefApi extends Trees.this.MemberDefApi { self: Trees.this.PackageDef =>

    }
    type ImplDef >: scala.Null <: Trees.this.ImplDefApi with Trees.this.MemberDef
    trait ImplDefApi extends Trees.this.MemberDefApi { self: Trees.this.ImplDef =>

    }
    type ClassDef >: scala.Null <: Trees.this.ClassDefApi with Trees.this.ImplDef
    abstract class ClassDefExtractor()
    trait ClassDefApi extends Trees.this.ImplDefApi { self: Trees.this.ClassDef =>

    }
    type ModuleDef >: scala.Null <: Trees.this.ModuleDefApi with Trees.this.ImplDef
    abstract class ModuleDefExtractor()
    trait ModuleDefApi extends Trees.this.ImplDefApi { self: Trees.this.ModuleDef =>

    }
    type ValOrDefDef >: scala.Null <: Trees.this.ValOrDefDefApi with Trees.this.MemberDef
    trait ValOrDefDefApi extends Trees.this.MemberDefApi { self: Trees.this.ValOrDefDef =>

    }
    type ValDef >: scala.Null <: Trees.this.ValDefApi with Trees.this.ValOrDefDef
    abstract class ValDefExtractor()
    trait ValDefApi extends Trees.this.ValOrDefDefApi { self: Trees.this.ValDef =>

    }
    type DefDef >: scala.Null <: Trees.this.DefDefApi with Trees.this.ValOrDefDef
    abstract class DefDefExtractor()
    trait DefDefApi extends Trees.this.ValOrDefDefApi { self: Trees.this.DefDef =>

    }
    type TypeDef >: scala.Null <: Trees.this.TypeDefApi with Trees.this.MemberDef
    abstract class TypeDefExtractor()
    trait TypeDefApi extends Trees.this.MemberDefApi { self: Trees.this.TypeDef =>

    }
    type LabelDef >: scala.Null <: Trees.this.LabelDefApi with Trees.this.DefTree with Trees.this.TermTree
    abstract class LabelDefExtractor()
    trait LabelDefApi extends Trees.this.DefTreeApi with Trees.this.TermTreeApi { self: Trees.this.LabelDef =>

    }
    type ImportSelector >: scala.Null <: Trees.this.ImportSelectorApi
    abstract class ImportSelectorExtractor()
    trait ImportSelectorApi { self: Trees.this.ImportSelector =>

    }
    type Import >: scala.Null <: Trees.this.ImportApi with Trees.this.SymTree
    abstract class ImportExtractor()
    trait ImportApi extends Trees.this.SymTreeApi { self: Trees.this.Import =>

    }
    type Template >: scala.Null <: Trees.this.TemplateApi with Trees.this.SymTree
    abstract class TemplateExtractor()
    trait TemplateApi extends Trees.this.SymTreeApi { self: Trees.this.Template =>

    }
    type Block >: scala.Null <: Trees.this.BlockApi with Trees.this.TermTree
    abstract class BlockExtractor()
    trait BlockApi extends Trees.this.TermTreeApi { self: Trees.this.Block =>

    }
    type CaseDef >: scala.Null <: Trees.this.CaseDefApi with Trees.this.Tree
    abstract class CaseDefExtractor()
    trait CaseDefApi extends Trees.this.TreeApi { self: Trees.this.CaseDef =>

    }
    type Alternative >: scala.Null <: Trees.this.AlternativeApi with Trees.this.TermTree
    abstract class AlternativeExtractor()
    trait AlternativeApi extends Trees.this.TermTreeApi { self: Trees.this.Alternative =>

    }
    type Star >: scala.Null <: Trees.this.StarApi with Trees.this.TermTree
    abstract class StarExtractor()
    trait StarApi extends Trees.this.TermTreeApi { self: Trees.this.Star =>

    }
    type Bind >: scala.Null <: Trees.this.BindApi with Trees.this.DefTree
    abstract class BindExtractor()
    trait BindApi extends Trees.this.DefTreeApi { self: Trees.this.Bind =>

    }
    type UnApply >: scala.Null <: Trees.this.UnApplyApi with Trees.this.TermTree
    abstract class UnApplyExtractor()
    trait UnApplyApi extends Trees.this.TermTreeApi { self: Trees.this.UnApply =>

    }
    type Function >: scala.Null <: Trees.this.FunctionApi with Trees.this.TermTree with Trees.this.SymTree
    abstract class FunctionExtractor()
    trait FunctionApi extends Trees.this.TermTreeApi with Trees.this.SymTreeApi { self: Trees.this.Function =>

    }
    type Assign >: scala.Null <: Trees.this.AssignApi with Trees.this.TermTree
    abstract class AssignExtractor()
    trait AssignApi extends Trees.this.TermTreeApi { self: Trees.this.Assign =>

    }
    type NamedArg >: scala.Null <: Trees.this.NamedArgApi with Trees.this.TermTree
    abstract class NamedArgExtractor()
    trait NamedArgApi extends Trees.this.TermTreeApi { self: Trees.this.NamedArg =>

    }
    type If >: scala.Null <: Trees.this.IfApi with Trees.this.TermTree
    abstract class IfExtractor()
    trait IfApi extends Trees.this.TermTreeApi { self: Trees.this.If =>

    }
    type Match >: scala.Null <: Trees.this.MatchApi with Trees.this.TermTree
    abstract class MatchExtractor()
    trait MatchApi extends Trees.this.TermTreeApi { self: Trees.this.Match =>

    }
    type Return >: scala.Null <: Trees.this.ReturnApi with Trees.this.SymTree with Trees.this.TermTree
    abstract class ReturnExtractor()
    trait ReturnApi extends Trees.this.TermTreeApi { self: Trees.this.Return =>

    }
    type Try >: scala.Null <: Trees.this.TryApi with Trees.this.TermTree
    abstract class TryExtractor()
    trait TryApi extends Trees.this.TermTreeApi { self: Trees.this.Try =>

    }
    type Throw >: scala.Null <: Trees.this.ThrowApi with Trees.this.TermTree
    abstract class ThrowExtractor()
    trait ThrowApi extends Trees.this.TermTreeApi { self: Trees.this.Throw =>

    }
    type New >: scala.Null <: Trees.this.NewApi with Trees.this.TermTree
    abstract class NewExtractor()
    trait NewApi extends Trees.this.TermTreeApi { self: Trees.this.New =>

    }
    type Typed >: scala.Null <: Trees.this.TypedApi with Trees.this.TermTree
    abstract class TypedExtractor()
    trait TypedApi extends Trees.this.TermTreeApi { self: Trees.this.Typed =>

    }
    type GenericApply >: scala.Null <: Trees.this.GenericApplyApi with Trees.this.TermTree
    trait GenericApplyApi extends Trees.this.TermTreeApi { self: Trees.this.GenericApply =>

    }
    type TypeApply >: scala.Null <: Trees.this.TypeApplyApi with Trees.this.GenericApply
    abstract class TypeApplyExtractor()
    trait TypeApplyApi extends Trees.this.GenericApplyApi { self: Trees.this.TypeApply =>

    }
    type Apply >: scala.Null <: Trees.this.ApplyApi with Trees.this.GenericApply
    abstract class ApplyExtractor()
    trait ApplyApi extends Trees.this.GenericApplyApi { self: Trees.this.Apply =>

    }
    type Super >: scala.Null <: Trees.this.SuperApi with Trees.this.TermTree
    abstract class SuperExtractor()
    trait SuperApi extends Trees.this.TermTreeApi { self: Trees.this.Super =>

    }
    type This >: scala.Null <: Trees.this.ThisApi with Trees.this.TermTree with Trees.this.SymTree
    abstract class ThisExtractor()
    trait ThisApi extends Trees.this.TermTreeApi with Trees.this.SymTreeApi { self: Trees.this.This =>

    }
    type Select >: scala.Null <: Trees.this.SelectApi with Trees.this.RefTree
    abstract class SelectExtractor()
    trait SelectApi extends Trees.this.RefTreeApi { self: Trees.this.Select =>

    }
    type Ident >: scala.Null <: Trees.this.IdentApi with Trees.this.RefTree
    abstract class IdentExtractor()
    trait IdentApi extends Trees.this.RefTreeApi { self: Trees.this.Ident =>

    }
    type Literal >: scala.Null <: Trees.this.LiteralApi with Trees.this.TermTree
    abstract class LiteralExtractor()
    trait LiteralApi extends Trees.this.TermTreeApi { self: Trees.this.Literal =>

    }
    type Annotated >: scala.Null <: Trees.this.AnnotatedApi with Trees.this.Tree
    abstract class AnnotatedExtractor()
    trait AnnotatedApi extends Trees.this.TreeApi { self: Trees.this.Annotated =>

    }
    type SingletonTypeTree >: scala.Null <: Trees.this.SingletonTypeTreeApi with Trees.this.TypTree
    abstract class SingletonTypeTreeExtractor()
    trait SingletonTypeTreeApi extends Trees.this.TypTreeApi { self: Trees.this.SingletonTypeTree =>

    }
    type SelectFromTypeTree >: scala.Null <: Trees.this.SelectFromTypeTreeApi with Trees.this.TypTree with Trees.this.RefTree
    abstract class SelectFromTypeTreeExtractor()
    trait SelectFromTypeTreeApi extends Trees.this.TypTreeApi with Trees.this.RefTreeApi { self: Trees.this.SelectFromTypeTree =>

    }
    type CompoundTypeTree >: scala.Null <: Trees.this.CompoundTypeTreeApi with Trees.this.TypTree
    abstract class CompoundTypeTreeExtractor()
    trait CompoundTypeTreeApi extends Trees.this.TypTreeApi { self: Trees.this.CompoundTypeTree =>

    }
    type AppliedTypeTree >: scala.Null <: Trees.this.AppliedTypeTreeApi with Trees.this.TypTree
    abstract class AppliedTypeTreeExtractor()
    trait AppliedTypeTreeApi extends Trees.this.TypTreeApi { self: Trees.this.AppliedTypeTree =>

    }
    type TypeBoundsTree >: scala.Null <: Trees.this.TypeBoundsTreeApi with Trees.this.TypTree
    abstract class TypeBoundsTreeExtractor()
    trait TypeBoundsTreeApi extends Trees.this.TypTreeApi { self: Trees.this.TypeBoundsTree =>

    }
    type ExistentialTypeTree >: scala.Null <: Trees.this.ExistentialTypeTreeApi with Trees.this.TypTree
    abstract class ExistentialTypeTreeExtractor()
    trait ExistentialTypeTreeApi extends Trees.this.TypTreeApi { self: Trees.this.ExistentialTypeTree =>

    }
    type TypeTree >: scala.Null <: Trees.this.TypeTreeApi with Trees.this.TypTree
    abstract class TypeTreeExtractor()
    trait TypeTreeApi extends Trees.this.TypTreeApi { self: Trees.this.TypeTree =>

    }
    type TreeCopier >: scala.Null <: Trees.this.TreeCopierOps
    abstract class TreeCopierOps()
    type Modifiers >: scala.Null <: Trees.this.ModifiersApi
    abstract class ModifiersApi()
    abstract class ModifiersExtractor()
  }

  trait TypeTags { self: scala.reflect.api.Universe =>
    trait WeakTypeTag[T] extends scala.Equals with scala.Serializable
  }

  trait Types { self: scala.reflect.api.Universe =>
    type Type >: scala.Null <: Types.this.TypeApi
    abstract class TypeApi()
    type SingletonType >: scala.Null <: Types.this.SingletonTypeApi with Types.this.Type
    trait SingletonTypeApi
    type ThisType >: scala.Null <: Types.this.ThisTypeApi with Types.this.SingletonType
    abstract class ThisTypeExtractor()
    trait ThisTypeApi extends Types.this.TypeApi { self: Types.this.ThisType =>

    }
    type SingleType >: scala.Null <: Types.this.SingleTypeApi with Types.this.SingletonType
    abstract class SingleTypeExtractor()
    trait SingleTypeApi extends Types.this.TypeApi { self: Types.this.SingleType =>

    }
    type SuperType >: scala.Null <: Types.this.SuperTypeApi with Types.this.SingletonType
    abstract class SuperTypeExtractor()
    trait SuperTypeApi extends Types.this.TypeApi { self: Types.this.SuperType =>

    }
    type ConstantType >: scala.Null <: Types.this.ConstantTypeApi with Types.this.SingletonType
    abstract class ConstantTypeExtractor()
    trait ConstantTypeApi extends Types.this.TypeApi { self: Types.this.ConstantType =>

    }
    type TypeRef >: scala.Null <: Types.this.TypeRefApi with Types.this.Type
    abstract class TypeRefExtractor()
    trait TypeRefApi extends Types.this.TypeApi { self: Types.this.TypeRef =>

    }
    type CompoundType >: scala.Null <: Types.this.CompoundTypeApi with Types.this.Type
    trait CompoundTypeApi
    type RefinedType >: scala.Null <: Types.this.RefinedTypeApi with Types.this.CompoundType
    abstract class RefinedTypeExtractor()
    trait RefinedTypeApi extends Types.this.TypeApi { self: Types.this.RefinedType =>
      def parents: scala.List[Types.this.Type]
      def decls: Types.this.MemberScope
    }
    type ClassInfoType >: scala.Null <: Types.this.ClassInfoTypeApi with Types.this.CompoundType
    abstract class ClassInfoTypeExtractor()
    trait ClassInfoTypeApi extends Types.this.TypeApi { self: Types.this.ClassInfoType =>
      def parents: scala.List[Types.this.Type]
      def decls: Types.this.MemberScope
      def typeSymbol: Types.this.Symbol
    }
    type MethodType >: scala.Null <: Types.this.MethodTypeApi with Types.this.Type
    abstract class MethodTypeExtractor()
    trait MethodTypeApi extends Types.this.TypeApi { self: Types.this.MethodType =>
      def params: scala.List[Types.this.Symbol]
      def resultType: Types.this.Type
    }
    type NullaryMethodType >: scala.Null <: Types.this.NullaryMethodTypeApi with Types.this.Type
    abstract class NullaryMethodTypeExtractor()
    trait NullaryMethodTypeApi extends Types.this.TypeApi { self: Types.this.NullaryMethodType =>
      def resultType: Types.this.Type
    }
    type PolyType >: scala.Null <: Types.this.PolyTypeApi with Types.this.Type
    abstract class PolyTypeExtractor()
    trait PolyTypeApi extends Types.this.TypeApi { self: Types.this.PolyType =>
      def typeParams: scala.List[Types.this.Symbol]
      def resultType: Types.this.Type
    }
    type ExistentialType >: scala.Null <: Types.this.ExistentialTypeApi with Types.this.Type
    abstract class ExistentialTypeExtractor()
    trait ExistentialTypeApi extends Types.this.TypeApi { self: Types.this.ExistentialType =>
      def underlying: Types.this.Type
    }
    type AnnotatedType >: scala.Null <: Types.this.AnnotatedTypeApi with Types.this.Type
    abstract class AnnotatedTypeExtractor()
    trait AnnotatedTypeApi extends Types.this.TypeApi { self: Types.this.AnnotatedType =>
      def annotations: scala.List[Types.this.Annotation]
      def underlying: Types.this.Type
    }
    type TypeBounds >: scala.Null <: Types.this.TypeBoundsApi with Types.this.Type
    abstract class TypeBoundsExtractor()
    trait TypeBoundsApi extends Types.this.TypeApi { self: Types.this.TypeBounds =>

    }
    type BoundedWildcardType >: scala.Null <: Types.this.BoundedWildcardTypeApi with Types.this.Type
    abstract class BoundedWildcardTypeExtractor()
    trait BoundedWildcardTypeApi extends Types.this.TypeApi { self: Types.this.BoundedWildcardType =>
      def bounds: Types.this.TypeBounds
    }
  }

  abstract class Universe() extends scala.reflect.api.Symbols with scala.reflect.api.Types with scala.reflect.api.FlagSets with scala.reflect.api.Scopes with scala.reflect.api.Names with scala.reflect.api.Trees with scala.reflect.api.Constants with scala.reflect.api.Annotations with scala.reflect.api.Positions with scala.reflect.api.Exprs with scala.reflect.api.TypeTags with scala.reflect.api.ImplicitTags with scala.reflect.api.StandardDefinitions with scala.reflect.api.StandardNames with scala.reflect.api.StandardLiftables with scala.reflect.api.Mirrors with scala.reflect.api.Printers with scala.reflect.api.Liftables with scala.reflect.api.Quasiquotes with scala.reflect.api.Internals

}
package scala.reflect.internal {
  trait AnnotationCheckers { self: scala.reflect.internal.SymbolTable =>

  }

  trait AnnotationInfos extends scala.reflect.api.Annotations { self: scala.reflect.internal.SymbolTable =>
    trait Annotatable[Self]
    sealed abstract class ClassfileAnnotArg() extends scala.Product with AnnotationInfos.this.JavaArgumentApi
    type JavaArgument = AnnotationInfos.this.ClassfileAnnotArg
    implicit val JavaArgumentTag: scala.reflect.ClassTag[AnnotationInfos.this.ClassfileAnnotArg] = ???
    abstract class AnnotationInfo() extends AnnotationInfos.this.AnnotationApi
    type Annotation = AnnotationInfos.this.AnnotationInfo
    object Annotation extends AnnotationInfos.this.AnnotationExtractor() {
      def apply(tpe: AnnotationInfos.this.Type, scalaArgs: scala.List[AnnotationInfos.this.Tree], javaArgs: scala.collection.immutable.ListMap[AnnotationInfos.this.Name,AnnotationInfos.this.ClassfileAnnotArg]): AnnotationInfos.this.Annotation = ???
      def unapply(annotation: AnnotationInfos.this.Annotation): scala.Some[scala.Tuple3[AnnotationInfos.this.Type,scala.List[AnnotationInfos.this.Tree],scala.collection.immutable.ListMap[AnnotationInfos.this.Name,AnnotationInfos.this.ClassfileAnnotArg]]] = ???
    }
    implicit val AnnotationTag: scala.reflect.ClassTag[AnnotationInfos.this.AnnotationInfo] = ???
    protected[scala] def annotationToTree(ann: AnnotationInfos.this.Annotation): AnnotationInfos.this.Tree = ???
    protected[scala] def treeToAnnotation(tree: AnnotationInfos.this.Tree): AnnotationInfos.this.Annotation = ???
  }

  trait BaseTypeSeqs { self: scala.reflect.internal.SymbolTable =>

  }

  trait BaseTypeSeqsStats { self: scala.reflect.internal.BaseTypeSeqsStats with scala.reflect.internal.util.Statistics =>

  }

  trait CapturedVariables { self: scala.reflect.internal.SymbolTable =>

  }

  trait Constants extends scala.reflect.api.Constants { self: scala.reflect.internal.SymbolTable =>
    case class Constant(value: scala.Any) extends Constants.this.ConstantApi() with scala.Product with scala.Serializable {
      def tpe: Constants.this.Type = ???
      override def equals(other: scala.Any): scala.Boolean = ???
    }
    object Constant extends Constants.this.ConstantExtractor() with java.io.Serializable
    implicit val ConstantTag: scala.reflect.ClassTag[Constants.this.Constant] = ???
  }

  trait Definitions extends scala.reflect.api.StandardDefinitions { self: scala.reflect.internal.SymbolTable =>
    object definitions extends Definitions.this.DefinitionsClass()
    private type PolyMethodCreator = scala.Function1[scala.List[Definitions.this.Symbol],scala.Tuple2[scala.Option[scala.List[Definitions.this.Type]],Definitions.this.Type]]
    trait ValueClassDefinitions { self: Definitions.this.DefinitionsClass =>
      lazy val UnitClass: Definitions.this.ClassSymbol = ???
      lazy val ByteClass: Definitions.this.ClassSymbol = ???
      lazy val ShortClass: Definitions.this.ClassSymbol = ???
      lazy val CharClass: Definitions.this.ClassSymbol = ???
      lazy val IntClass: Definitions.this.ClassSymbol = ???
      lazy val LongClass: Definitions.this.ClassSymbol = ???
      lazy val FloatClass: Definitions.this.ClassSymbol = ???
      lazy val DoubleClass: Definitions.this.ClassSymbol = ???
      lazy val BooleanClass: Definitions.this.ClassSymbol = ???
      lazy val UnitTpe: Definitions.this.Type = ???
      lazy val ByteTpe: Definitions.this.Type = ???
      lazy val ShortTpe: Definitions.this.Type = ???
      lazy val CharTpe: Definitions.this.Type = ???
      lazy val IntTpe: Definitions.this.Type = ???
      lazy val LongTpe: Definitions.this.Type = ???
      lazy val FloatTpe: Definitions.this.Type = ???
      lazy val DoubleTpe: Definitions.this.Type = ???
      lazy val BooleanTpe: Definitions.this.Type = ???
      lazy val ScalaNumericValueClasses: scala.collection.immutable.List[Definitions.this.ClassSymbol] = ???
      def ScalaPrimitiveValueClasses: scala.List[Definitions.this.ClassSymbol] = ???
    }
    abstract class DefinitionsClass() extends Definitions.this.DefinitionsApi with Definitions.this.ValueClassDefinitions {
      private[this] var isInitialized: scala.Boolean = ???
      def isDefinitionsInitialized: scala.Boolean = ???
      lazy val JavaLangPackage: Definitions.this.ModuleSymbol = ???
      lazy val JavaLangPackageClass: Definitions.this.ClassSymbol = ???
      lazy val ScalaPackage: Definitions.this.ModuleSymbol = ???
      lazy val ScalaPackageClass: Definitions.this.ClassSymbol = ???
      lazy val ScalaPackageObject: Definitions.this.Symbol = ???
      lazy val RuntimePackage: Definitions.this.ModuleSymbol = ???
      lazy val RuntimePackageClass: Definitions.this.ClassSymbol = ???
      def javaTypeToValueClass(jtype: scala.Predef.Class[_ >: scala.Nothing <: scala.Any]): Definitions.this.Symbol = ???
      def valueClassToJavaType(sym: Definitions.this.Symbol): scala.Predef.Class[_ >: scala.Nothing <: scala.Any] = ???
      def fullyInitializeSymbol(sym: Definitions.this.Symbol): sym.type = ???
      def fullyInitializeType(tp: Definitions.this.Type): tp.type = ???
      def fullyInitializeScope(scope: Definitions.this.Scope): scope.type = ???
      def isUniversalMember(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isUnimportable(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isUnimportableUnlessRenamed(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isImportable(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isTrivialTopType(tp: Definitions.this.Type): scala.Boolean = ???
      def isUnitType(tp: Definitions.this.Type): scala.Boolean = ???
      private def fixupAsAnyTrait(tpe: Definitions.this.Type): Definitions.this.Type = ???
      lazy val AnyClass: Definitions.this.ClassSymbol = ???
      lazy val AnyRefClass: Definitions.this.AliasTypeSymbol = ???
      lazy val ObjectClass: Definitions.this.ClassSymbol = ???
      lazy val AnyRefTpe: Definitions.this.Type = ???
      lazy val AnyTpe: Definitions.this.Type = ???
      lazy val AnyValTpe: Definitions.this.Type = ???
      lazy val BoxedUnitTpe: Definitions.this.Type = ???
      lazy val NothingTpe: Definitions.this.Type = ???
      lazy val NullTpe: Definitions.this.Type = ???
      lazy val ObjectTpe: Definitions.this.Type = ???
      lazy val ObjectTpeJava: Definitions.this.ObjectTpeJavaRef = ???
      lazy val SerializableTpe: Definitions.this.Type = ???
      lazy val StringTpe: Definitions.this.Type = ???
      lazy val ThrowableTpe: Definitions.this.Type = ???
      lazy val ConstantTrue: Definitions.this.ConstantType = ???
      lazy val ConstantFalse: Definitions.this.ConstantType = ???
      lazy val ConstantNull: Definitions.this.ConstantType = ???
      lazy val AnyValClass: Definitions.this.ClassSymbol = ???
      def AnyVal_getClass: Definitions.this.TermSymbol = ???
      lazy val RuntimeNothingClass: Definitions.this.ClassSymbol = ???
      lazy val RuntimeNullClass: Definitions.this.ClassSymbol = ???
      sealed abstract class BottomClassSymbol(name: Definitions.this.TypeName, parent: Definitions.this.Symbol) extends Definitions.this.ClassSymbol(???, ???, ???)
      object NothingClass extends DefinitionsClass.this.BottomClassSymbol(???, ???)
      object NullClass extends DefinitionsClass.this.BottomClassSymbol(???, ???)
      lazy val ClassCastExceptionClass: Definitions.this.ClassSymbol = ???
      lazy val IndexOutOfBoundsExceptionClass: Definitions.this.ClassSymbol = ???
      lazy val InvocationTargetExceptionClass: Definitions.this.ClassSymbol = ???
      lazy val MatchErrorClass: Definitions.this.ClassSymbol = ???
      lazy val NonLocalReturnControlClass: Definitions.this.ClassSymbol = ???
      lazy val NullPointerExceptionClass: Definitions.this.ClassSymbol = ???
      lazy val ThrowableClass: Definitions.this.ClassSymbol = ???
      lazy val UninitializedErrorClass: Definitions.this.ClassSymbol = ???
      lazy val RuntimeExceptionClass: Definitions.this.ClassSymbol = ???
      lazy val IllegalArgExceptionClass: Definitions.this.ClassSymbol = ???
      lazy val UninitializedFieldConstructor: Definitions.this.Symbol = ???
      lazy val PartialFunctionClass: Definitions.this.ClassSymbol = ???
      lazy val AbstractPartialFunctionClass: Definitions.this.ClassSymbol = ???
      lazy val SymbolClass: Definitions.this.ClassSymbol = ???
      lazy val StringClass: Definitions.this.ClassSymbol = ???
      lazy val StringModule: Definitions.this.Symbol = ???
      lazy val ClassClass: Definitions.this.ClassSymbol = ???
      def Class_getMethod: Definitions.this.TermSymbol = ???
      lazy val DynamicClass: Definitions.this.ClassSymbol = ???
      lazy val UnqualifiedModules: scala.collection.immutable.List[Definitions.this.ModuleSymbol] = ???
      lazy val UnqualifiedOwners: scala.collection.immutable.Set[Definitions.this.Symbol] = ???
      lazy val PredefModule: Definitions.this.ModuleSymbol = ???
      def `Predef_???`: Definitions.this.TermSymbol = ???
      def Predef_locally: Definitions.this.TermSymbol = ???
      def isPredefMemberNamed(sym: Definitions.this.Symbol, name: Definitions.this.Name): scala.Boolean = ???
      def wrapVarargsArrayMethod(tp: Definitions.this.Type): Definitions.this.TermSymbol = ???
      lazy val SpecializableModule: Definitions.this.ModuleSymbol = ???
      lazy val ScalaRunTimeModule: Definitions.this.ModuleSymbol = ???
      lazy val MurmurHash3Module: Definitions.this.ModuleSymbol = ???
      lazy val SymbolModule: Definitions.this.ModuleSymbol = ???
      def Symbol_apply: Definitions.this.TermSymbol = ???
      lazy val ScalaNumberClass: Definitions.this.ClassSymbol = ???
      lazy val DelayedInitClass: Definitions.this.ClassSymbol = ???
      def delayedInitMethod: Definitions.this.TermSymbol = ???
      lazy val TypeConstraintClass: Definitions.this.ClassSymbol = ???
      lazy val SingletonClass: Definitions.this.ClassSymbol = ???
      lazy val ListOfSingletonClassTpe: scala.collection.immutable.List[Definitions.this.Type] = ???
      lazy val SerializableClass: Definitions.this.ClassSymbol = ???
      lazy val ComparableClass: Definitions.this.ClassSymbol = ???
      lazy val JavaCloneableClass: Definitions.this.ClassSymbol = ???
      lazy val JavaNumberClass: Definitions.this.ClassSymbol = ???
      lazy val JavaEnumClass: Definitions.this.ClassSymbol = ???
      lazy val JavaUtilMap: Definitions.this.ClassSymbol = ???
      lazy val JavaUtilHashMap: Definitions.this.ClassSymbol = ???
      lazy val JavaRecordClass: Definitions.this.Symbol = ???
      lazy val ByNameParamClass: Definitions.this.ClassSymbol = ???
      lazy val JavaRepeatedParamClass: Definitions.this.ClassSymbol = ???
      lazy val RepeatedParamClass: Definitions.this.ClassSymbol = ???
      def isByNameParamType(tp: Definitions.this.Type): scala.Boolean = ???
      def isScalaRepeatedParamType(tp: Definitions.this.Type): scala.Boolean = ???
      def isJavaRepeatedParamType(tp: Definitions.this.Type): scala.Boolean = ???
      def isRepeatedParamType(tp: Definitions.this.Type): scala.Boolean = ???
      def isRepeated(param: Definitions.this.Symbol): scala.Boolean = ???
      def isByName(param: Definitions.this.Symbol): scala.Boolean = ???
      def isCastSymbol(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isTypeTestSymbol(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isJavaVarArgsMethod(m: Definitions.this.Symbol): scala.Boolean = ???
      def isJavaVarArgs(params: scala.collection.Seq[Definitions.this.Symbol]): scala.Boolean = ???
      def isScalaVarArgs(params: scala.collection.Seq[Definitions.this.Symbol]): scala.Boolean = ???
      def isVarArgsList(params: scala.collection.Seq[Definitions.this.Symbol]): scala.Boolean = ???
      def isVarArgTypes(formals: scala.collection.Seq[Definitions.this.Type]): scala.Boolean = ???
      def firstParamType(tpe: Definitions.this.Type): Definitions.this.Type = ???
      def isImplicitParamss(paramss: scala.List[scala.List[Definitions.this.Symbol]]): scala.Boolean = ???
      final def hasRepeatedParam(tp: Definitions.this.Type): scala.Boolean = ???
      def dropByName(tp: Definitions.this.Type): Definitions.this.Type = ???
      def dropRepeated(tp: Definitions.this.Type): Definitions.this.Type = ???
      def repeatedToSingle(tp: Definitions.this.Type): Definitions.this.Type = ???
      def repeatedToSeq(tp: Definitions.this.Type): Definitions.this.Type = ???
      def seqToRepeated(tp: Definitions.this.Type): Definitions.this.Type = ???
      def isReferenceArray(tp: Definitions.this.Type): scala.Boolean = ???
      def isArrayOfSymbol(tp: Definitions.this.Type, elem: Definitions.this.Symbol): scala.Boolean = ???
      def elementType(container: Definitions.this.Symbol, tp: Definitions.this.Type): Definitions.this.Type = ???
      lazy val SubTypeClass: Definitions.this.ClassSymbol = ???
      lazy val SameTypeClass: Definitions.this.ClassSymbol = ???
      lazy val DummyImplicitClass: Definitions.this.ClassSymbol = ???
      lazy val ConsClass: Definitions.this.ClassSymbol = ???
      lazy val IteratorClass: Definitions.this.ClassSymbol = ???
      lazy val IterableClass: Definitions.this.ClassSymbol = ???
      lazy val ListClass: Definitions.this.ClassSymbol = ???
      def List_cons: Definitions.this.TermSymbol = ???
      lazy val SeqClass: Definitions.this.ClassSymbol = ???
      lazy val SeqFactoryClass: Definitions.this.ModuleSymbol = ???
      lazy val UnapplySeqWrapperClass: Definitions.this.TypeSymbol = ???
      lazy val JavaStringBuilderClass: Definitions.this.ClassSymbol = ???
      lazy val JavaStringBufferClass: Definitions.this.ClassSymbol = ???
      lazy val JavaCharSequenceClass: Definitions.this.ClassSymbol = ???
      def TraversableClass: Definitions.this.ClassSymbol = ???
      lazy val ListModule: Definitions.this.ModuleSymbol = ???
      def List_apply: Definitions.this.TermSymbol = ???
      lazy val ListModuleAlias: Definitions.this.TermSymbol = ???
      lazy val NilModule: Definitions.this.ModuleSymbol = ???
      lazy val NilModuleAlias: Definitions.this.TermSymbol = ???
      lazy val SeqModule: Definitions.this.ModuleSymbol = ???
      lazy val SeqModuleAlias: Definitions.this.TermSymbol = ???
      lazy val Collection_SeqModule: Definitions.this.ModuleSymbol = ???
      lazy val ArrayModule: Definitions.this.ModuleSymbol = ???
      lazy val ArrayModule_overloadedApply: Definitions.this.TermSymbol = ???
      def ArrayModule_genericApply: Definitions.this.Symbol = ???
      def ArrayModule_apply(tp: Definitions.this.Type): Definitions.this.Symbol = ???
      lazy val ArrayClass: Definitions.this.ClassSymbol = ???
      lazy val Array_apply: Definitions.this.TermSymbol = ???
      lazy val Array_update: Definitions.this.TermSymbol = ???
      lazy val Array_length: Definitions.this.TermSymbol = ???
      lazy val Array_clone: Definitions.this.TermSymbol = ???
      lazy val SoftReferenceClass: Definitions.this.ClassSymbol = ???
      lazy val MethodClass: Definitions.this.ClassSymbol = ???
      lazy val EmptyMethodCacheClass: Definitions.this.ClassSymbol = ???
      lazy val MethodCacheClass: Definitions.this.ClassSymbol = ???
      def methodCache_find: Definitions.this.TermSymbol = ???
      def methodCache_add: Definitions.this.TermSymbol = ???
      lazy val StructuralCallSite: Definitions.this.Symbol = ???
      def StructuralCallSite_bootstrap: Definitions.this.TermSymbol = ???
      lazy val StructuralCallSite_dummy: Definitions.this.MethodSymbol = ???
      def StructuralCallSite_find: Definitions.this.Symbol = ???
      def StructuralCallSite_add: Definitions.this.Symbol = ???
      def StructuralCallSite_getParameterTypes: Definitions.this.Symbol = ???
      lazy val SymbolLiteral: Definitions.this.Symbol = ???
      def SymbolLiteral_bootstrap: Definitions.this.Symbol = ???
      def SymbolLiteral_dummy: Definitions.this.MethodSymbol = ???
      lazy val ScalaXmlTopScope: Definitions.this.Symbol = ???
      lazy val ScalaXmlPackage: Definitions.this.Symbol = ???
      lazy val ReflectPackage: Definitions.this.ModuleSymbol = ???
      lazy val ReflectApiPackage: Definitions.this.Symbol = ???
      lazy val ReflectRuntimePackage: Definitions.this.Symbol = ???
      def ReflectRuntimeUniverse: Definitions.this.Symbol = ???
      def ReflectRuntimeCurrentMirror: Definitions.this.Symbol = ???
      lazy val UniverseClass: Definitions.this.Symbol = ???
      def UniverseInternal: Definitions.this.TermSymbol = ???
      lazy val PartialManifestModule: Definitions.this.ModuleSymbol = ???
      lazy val FullManifestClass: Definitions.this.ClassSymbol = ???
      lazy val FullManifestModule: Definitions.this.ModuleSymbol = ???
      lazy val OptManifestClass: Definitions.this.ClassSymbol = ???
      lazy val NoManifest: Definitions.this.ModuleSymbol = ???
      lazy val TreesClass: Definitions.this.Symbol = ???
      lazy val ExprsClass: Definitions.this.Symbol = ???
      def ExprClass: Definitions.this.Symbol = ???
      def ExprSplice: Definitions.this.Symbol = ???
      def ExprValue: Definitions.this.Symbol = ???
      lazy val ClassTagModule: Definitions.this.ModuleSymbol = ???
      lazy val ClassTagClass: Definitions.this.ClassSymbol = ???
      lazy val TypeTagsClass: Definitions.this.Symbol = ???
      lazy val ApiUniverseClass: Definitions.this.Symbol = ???
      lazy val ApiQuasiquotesClass: Definitions.this.Symbol = ???
      lazy val JavaUniverseClass: Definitions.this.Symbol = ???
      lazy val MirrorClass: Definitions.this.Symbol = ???
      lazy val TypeCreatorClass: Definitions.this.Symbol = ???
      lazy val TreeCreatorClass: Definitions.this.Symbol = ???
      lazy val BlackboxContextClass: Definitions.this.Symbol = ???
      lazy val WhiteboxContextClass: Definitions.this.Symbol = ???
      def MacroContextPrefix: Definitions.this.Symbol = ???
      def MacroContextPrefixType: Definitions.this.Symbol = ???
      def MacroContextUniverse: Definitions.this.Symbol = ???
      def MacroContextExprClass: Definitions.this.Symbol = ???
      def MacroContextWeakTypeTagClass: Definitions.this.Symbol = ???
      def MacroContextTreeType: Definitions.this.Symbol = ???
      lazy val MacroImplAnnotation: Definitions.this.ClassSymbol = ???
      lazy val MacroImplLocationAnnotation: Definitions.this.ClassSymbol = ???
      lazy val StringContextClass: Definitions.this.ClassSymbol = ???
      lazy val StringContextModule: Definitions.this.ModuleSymbol = ???
      lazy val ValueOfClass: Definitions.this.Symbol = ???
      lazy val QuasiquoteClass: Definitions.this.Symbol = ???
      lazy val QuasiquoteClass_api: Definitions.this.Symbol = ???
      lazy val QuasiquoteClass_api_apply: Definitions.this.Symbol{type TypeOfClonedSymbol >: Definitions.this.TermSymbol with Definitions.this.NoSymbol <: Definitions.this.Symbol{type TypeOfClonedSymbol >: Definitions.this.TermSymbol with Definitions.this.NoSymbol <: Definitions.this.Symbol; type NameType <: Definitions.this.TermName}; type NameType <: Definitions.this.TermName} = ???
      lazy val QuasiquoteClass_api_unapply: Definitions.this.Symbol{type TypeOfClonedSymbol >: Definitions.this.TermSymbol with Definitions.this.NoSymbol <: Definitions.this.Symbol{type TypeOfClonedSymbol >: Definitions.this.TermSymbol with Definitions.this.NoSymbol <: Definitions.this.Symbol; type NameType <: Definitions.this.TermName}; type NameType <: Definitions.this.TermName} = ???
      lazy val ScalaSignatureAnnotation: Definitions.this.ClassSymbol = ???
      lazy val ScalaLongSignatureAnnotation: Definitions.this.ClassSymbol = ???
      lazy val MethodHandleClass: Definitions.this.Symbol = ???
      lazy val VarHandleClass: Definitions.this.Symbol = ???
      lazy val OptionClass: Definitions.this.ClassSymbol = ???
      lazy val OptionModule: Definitions.this.ModuleSymbol = ???
      lazy val SomeClass: Definitions.this.ClassSymbol = ???
      lazy val NoneModule: Definitions.this.ModuleSymbol = ???
      lazy val SomeModule: Definitions.this.ModuleSymbol = ???
      lazy val ModuleSerializationProxyClass: Definitions.this.ClassSymbol = ???
      def compilerTypeFromTag(tt: scala.reflect.api.Universe#WeakTypeTag[_ >: scala.Nothing <: scala.Any]): Definitions.this.Type = ???
      def compilerSymbolFromTag(tt: scala.reflect.api.Universe#WeakTypeTag[_ >: scala.Nothing <: scala.Any]): Definitions.this.Symbol = ???
      def isJavaMainMethod(sym: Definitions.this.Symbol): scala.Boolean = ???
      def hasJavaMainMethod(sym: Definitions.this.Symbol): scala.Boolean = ???
      class VarArityClass(name: scala.Predef.String, maxArity: scala.Int, countFrom: scala.Int = ???, init: scala.Option[Definitions.this.ClassSymbol] = ???) extends DefinitionsClass.this.VarArityClassApi() {
        val seq: scala.IndexedSeq[Definitions.this.ClassSymbol] = ???
        def apply(i: scala.Int): Definitions.this.Symbol = ???
      }
      object VarArityClass
      val MaxTupleArity: scala.Int = ???
      val MaxProductArity: scala.Int = ???
      val MaxFunctionArity: scala.Int = ???
      val MaxTupleAritySpecialized: scala.Int = ???
      val MaxProductAritySpecialized: scala.Int = ???
      val MaxFunctionAritySpecialized: scala.Int = ???
      lazy val ProductClass: DefinitionsClass.this.VarArityClass = ???
      lazy val TupleClass: DefinitionsClass.this.VarArityClass = ???
      lazy val FunctionClass: DefinitionsClass.this.VarArityClass = ???
      lazy val AbstractFunctionClass: DefinitionsClass.this.VarArityClass = ???
      def tupleType(elems: scala.List[Definitions.this.Type]): Definitions.this.Type = ???
      def functionType(formals: scala.List[Definitions.this.Type], restpe: Definitions.this.Type): Definitions.this.Type = ???
      def abstractFunctionType(formals: scala.List[Definitions.this.Type], restpe: Definitions.this.Type): Definitions.this.Type = ???
      def wrapVarargsArrayMethodName(elemtp: Definitions.this.Type): Definitions.this.TermName = ???
      def isTupleSymbol(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isFunctionSymbol(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isAbstractFunctionSymbol(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isProductNSymbol(sym: Definitions.this.Symbol): scala.Boolean = ???
      lazy val TryClass: Definitions.this.ClassSymbol = ???
      lazy val FailureClass: Definitions.this.ClassSymbol = ???
      lazy val SuccessClass: Definitions.this.ClassSymbol = ???
      lazy val FutureClass: Definitions.this.ClassSymbol = ???
      lazy val PromiseClass: Definitions.this.ClassSymbol = ???
      lazy val NonFatalModule: Definitions.this.ModuleSymbol = ???
      lazy val NonFatal_apply: Definitions.this.TermSymbol = ???
      def unspecializedSymbol(sym: Definitions.this.Symbol): Definitions.this.Symbol = ???
      def unspecializedTypeArgs(tp: Definitions.this.Type): scala.List[Definitions.this.Type] = ???
      object MacroContextType
      def isMacroContextType(tp: Definitions.this.Type): scala.Boolean = ???
      def isWhiteboxContextType(tp: Definitions.this.Type): scala.Boolean = ???
      private def macroBundleParamInfo(tp: Definitions.this.Type): Definitions.this.Type = ???
      def looksLikeMacroBundleType(tp: Definitions.this.Type): scala.Boolean = ???
      def isMacroBundleType(tp: Definitions.this.Type): scala.Boolean = ???
      def isBlackboxMacroBundleType(tp: Definitions.this.Type): scala.Boolean = ???
      def isListType(tp: Definitions.this.Type): scala.Boolean = ???
      def isIterableType(tp: Definitions.this.Type): scala.Boolean = ???
      def isFunctionTypeDirect(tp: Definitions.this.Type): scala.Boolean = ???
      def isTupleTypeDirect(tp: Definitions.this.Type): scala.Boolean = ???
      def isFunctionType(tp: Definitions.this.Type): scala.Boolean = ???
      def isFunctionProto(pt: Definitions.this.Type): scala.Boolean = ???
      def partialFunctionArgResTypeFromProto(pt: Definitions.this.Type): scala.Tuple2[Definitions.this.Type,Definitions.this.Type] = ???
      def functionArityFromType(tp: Definitions.this.Type): scala.Int = ???
      def functionOrPfOrSamArgTypes(tp: Definitions.this.Type): scala.List[Definitions.this.Type] = ???
      def samToFunctionType(tp: Definitions.this.Type, sam: Definitions.this.Symbol = ???): Definitions.this.Type = ???
      final def methodToExpressionTp(tp: Definitions.this.Type): Definitions.this.Type = ???
      def samMatchesFunctionBasedOnArity(sam: Definitions.this.Symbol, formals: scala.List[scala.Any]): scala.Boolean = ???
      def isTupleType(tp: Definitions.this.Type): scala.Boolean = ???
      def tupleComponents(tp: Definitions.this.Type): scala.List[Definitions.this.Type] = ???
      lazy val ProductRootClass: Definitions.this.ClassSymbol = ???
      def Product_productArity: Definitions.this.TermSymbol = ???
      def Product_productElement: Definitions.this.TermSymbol = ???
      def Product_productElementName: Definitions.this.Symbol = ???
      def Product_iterator: Definitions.this.TermSymbol = ???
      def Product_productPrefix: Definitions.this.TermSymbol = ???
      def Product_canEqual: Definitions.this.TermSymbol = ???
      def productProj(z: Definitions.this.Symbol, j: scala.Int): Definitions.this.TermSymbol = ???
      def getProductArgs(tpe: Definitions.this.Type): scala.List[Definitions.this.Type] = ???
      def unapplyUnwrap(tpe: Definitions.this.Type): Definitions.this.Type = ???
      def dropNullaryMethod(tp: Definitions.this.Type): Definitions.this.Type = ???
      final def finalResultType(tp: Definitions.this.Type): Definitions.this.Type = ???
      final def isStable(tp: Definitions.this.Type): scala.Boolean = ???
      final def isVolatile(tp: Definitions.this.Type): scala.Boolean = ???
      private[this] var volatileRecursions: scala.Int = ???
      private[this] val pendingVolatiles: scala.collection.mutable.HashSet[Definitions.this.Symbol] = ???
      def functionNBaseType(tp: Definitions.this.Type): Definitions.this.Type = ???
      def isPartialFunctionType(tp: Definitions.this.Type): scala.Boolean = ???
      private[this] val samCache: scala.collection.mutable.AnyRefMap[Definitions.this.Symbol,Definitions.this.Symbol] = ???
      def samOf(tp: Definitions.this.Type): Definitions.this.Symbol = ???
      def samOfProto(pt: Definitions.this.Type): Definitions.this.Symbol = ???
      def arrayType(arg: Definitions.this.Type): Definitions.this.Type = ???
      def byNameType(arg: Definitions.this.Type): Definitions.this.Type = ???
      def iteratorOfType(tp: Definitions.this.Type): Definitions.this.Type = ???
      def javaRepeatedType(arg: Definitions.this.Type): Definitions.this.Type = ???
      def optionType(tp: Definitions.this.Type): Definitions.this.Type = ???
      def scalaRepeatedType(arg: Definitions.this.Type): Definitions.this.Type = ???
      def seqType(arg: Definitions.this.Type): Definitions.this.Type = ???
      def elementTypeFromGet(tp: Definitions.this.Type): Definitions.this.Type = ???
      def elementTypeFromApply(tp: Definitions.this.Type): Definitions.this.Type = ???
      def resultOfIsEmpty(tp: Definitions.this.Type): Definitions.this.Type = ???
      private def typeArgOfBaseTypeOr(tp: Definitions.this.Type, baseClass: Definitions.this.Symbol)(or: => Definitions.this.Type): Definitions.this.Type = ???
      private def resultOfMatchingMethod(tp: Definitions.this.Type, name: Definitions.this.TermName)(paramTypes: Definitions.this.Type*): Definitions.this.Type = ???
      def ClassType(arg: Definitions.this.Type): Definitions.this.Type = ???
      def neverHasTypeParameters(sym: Definitions.this.Symbol): scala.Boolean = ???
      def EnumType(sym: Definitions.this.Symbol): Definitions.this.Type = ???
      def classExistentialType(prefix: Definitions.this.Type, clazz: Definitions.this.Symbol): Definitions.this.Type = ???
      lazy val `Any_==`: Definitions.this.MethodSymbol = ???
      lazy val `Any_!=`: Definitions.this.MethodSymbol = ???
      lazy val Any_equals: Definitions.this.MethodSymbol = ???
      lazy val Any_hashCode: Definitions.this.MethodSymbol = ???
      lazy val Any_toString: Definitions.this.MethodSymbol = ???
      lazy val `Any_##`: Definitions.this.MethodSymbol = ???
      lazy val Any_getClass: Definitions.this.MethodSymbol = ???
      lazy val Any_isInstanceOf: Definitions.this.MethodSymbol = ???
      lazy val Any_asInstanceOf: Definitions.this.MethodSymbol = ???
      lazy val primitiveGetClassMethods: scala.collection.immutable.Set[Definitions.this.Symbol] = ???
      lazy val getClassMethods: scala.Predef.Set[Definitions.this.Symbol] = ???
      def getClassReturnType(tp: Definitions.this.Type): Definitions.this.Type = ???
      def removeRedundantObjects(tps: scala.List[Definitions.this.Type]): scala.List[Definitions.this.Type] = ???
      def normalizedParents(parents: scala.List[Definitions.this.Type]): scala.List[Definitions.this.Type] = ???
      def allParameters(tpe: Definitions.this.Type): scala.List[Definitions.this.Symbol] = ???
      def typeStringNoPackage(tp: Definitions.this.Type): java.lang.String = ???
      def briefParentsString(parents: scala.List[Definitions.this.Type]): scala.Predef.String = ???
      def parentsString(parents: scala.List[Definitions.this.Type]): scala.Predef.String = ???
      def valueParamsString(tp: Definitions.this.Type): scala.Predef.String = ???
      lazy val `Object_##`: Definitions.this.MethodSymbol = ???
      lazy val `Object_==`: Definitions.this.MethodSymbol = ???
      lazy val `Object_!=`: Definitions.this.MethodSymbol = ???
      lazy val Object_eq: Definitions.this.MethodSymbol = ???
      lazy val Object_ne: Definitions.this.MethodSymbol = ???
      lazy val Object_isInstanceOf: Definitions.this.MethodSymbol = ???
      lazy val Object_asInstanceOf: Definitions.this.MethodSymbol = ???
      lazy val Object_synchronized: Definitions.this.MethodSymbol = ???
      lazy val `String_+`: Definitions.this.MethodSymbol = ???
      def Object_getClass: Definitions.this.TermSymbol = ???
      def Object_clone: Definitions.this.TermSymbol = ???
      def Object_finalize: Definitions.this.TermSymbol = ???
      def Object_notify: Definitions.this.TermSymbol = ???
      def Object_notifyAll: Definitions.this.TermSymbol = ???
      def Object_wait: Definitions.this.TermSymbol = ???
      def Object_equals: Definitions.this.TermSymbol = ???
      def Object_hashCode: Definitions.this.TermSymbol = ???
      def Object_toString: Definitions.this.TermSymbol = ???
      lazy val ObjectsClass: Definitions.this.Symbol = ???
      def Objects_hashCode: Definitions.this.TermSymbol = ???
      def Objects_equals: Definitions.this.TermSymbol = ???
      lazy val ObjectRefClass: Definitions.this.ClassSymbol = ???
      lazy val VolatileObjectRefClass: Definitions.this.ClassSymbol = ???
      lazy val RuntimeStaticsModule: Definitions.this.ModuleSymbol = ???
      lazy val BoxesRunTimeModule: Definitions.this.ModuleSymbol = ???
      lazy val BoxesRunTimeClass: Definitions.this.Symbol = ???
      lazy val BoxedNumberClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedCharacterClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedBooleanClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedByteClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedShortClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedIntClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedLongClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedFloatClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedDoubleClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedUnitClass: Definitions.this.ClassSymbol = ???
      lazy val BoxedUnitModule: Definitions.this.ModuleSymbol = ???
      def BoxedUnit_UNIT: Definitions.this.TermSymbol = ???
      def BoxedUnit_TYPE: Definitions.this.TermSymbol = ???
      lazy val AnnotationClass: Definitions.this.ClassSymbol = ???
      lazy val ConstantAnnotationClass: Definitions.this.Symbol = ???
      lazy val StaticAnnotationClass: Definitions.this.ClassSymbol = ???
      lazy val AnnotationRetentionAttr: Definitions.this.ClassSymbol = ???
      lazy val AnnotationRetentionPolicyAttr: Definitions.this.ClassSymbol = ???
      lazy val AnnotationRepeatableAttr: Definitions.this.ClassSymbol = ???
      lazy val ElidableMethodClass: Definitions.this.ClassSymbol = ???
      lazy val ImplicitNotFoundClass: Definitions.this.ClassSymbol = ???
      lazy val ImplicitAmbiguousClass: Definitions.this.Symbol = ???
      lazy val MigrationAnnotationClass: Definitions.this.ClassSymbol = ???
      lazy val ScalaStrictFPAttr: Definitions.this.ClassSymbol = ???
      lazy val SwitchClass: Definitions.this.ClassSymbol = ???
      lazy val TailrecClass: Definitions.this.ClassSymbol = ???
      lazy val VarargsClass: Definitions.this.ClassSymbol = ???
      lazy val NowarnClass: Definitions.this.Symbol = ???
      lazy val uncheckedStableClass: Definitions.this.ClassSymbol = ???
      lazy val uncheckedVarianceClass: Definitions.this.ClassSymbol = ???
      lazy val uncheckedOverrideClass: Definitions.this.Symbol = ???
      lazy val ChildAnnotationClass: Definitions.this.Symbol = ???
      lazy val RepeatedAnnotationClass: Definitions.this.Symbol = ???
      lazy val TargetNameAnnotationClass: Definitions.this.Symbol = ???
      lazy val StaticMethodAnnotationClass: Definitions.this.Symbol = ???
      lazy val PolyFunctionClass: Definitions.this.Symbol = ???
      lazy val ExperimentalAnnotationClass: Definitions.this.Symbol = ???
      lazy val AnnotationDefaultClass: Definitions.this.Symbol = ???
      lazy val JavaAnnotationClass: Definitions.this.ClassSymbol = ???
      lazy val BeanPropertyAttr: Definitions.this.ClassSymbol = ???
      lazy val BooleanBeanPropertyAttr: Definitions.this.ClassSymbol = ???
      lazy val CompileTimeOnlyAttr: Definitions.this.Symbol = ???
      lazy val DefaultArgAttr: Definitions.this.Symbol = ???
      lazy val DeprecatedAttr: Definitions.this.ClassSymbol = ???
      lazy val DeprecatedNameAttr: Definitions.this.ClassSymbol = ???
      lazy val DeprecatedInheritanceAttr: Definitions.this.ClassSymbol = ???
      lazy val DeprecatedOverridingAttr: Definitions.this.ClassSymbol = ???
      lazy val NativeAttr: Definitions.this.ClassSymbol = ???
      lazy val ScalaInlineClass: Definitions.this.ClassSymbol = ???
      lazy val ScalaNoInlineClass: Definitions.this.ClassSymbol = ???
      lazy val SerialVersionUIDAttr: Definitions.this.ClassSymbol = ???
      lazy val SerialVersionUIDAnnotation: Definitions.this.AnnotationInfo = ???
      lazy val SpecializedClass: Definitions.this.ClassSymbol = ???
      lazy val SuperArgAttr: Definitions.this.Symbol = ???
      lazy val SuperFwdArgAttr: Definitions.this.Symbol = ???
      lazy val ThrowsClass: Definitions.this.ClassSymbol = ???
      lazy val TransientAttr: Definitions.this.ClassSymbol = ???
      lazy val UncheckedClass: Definitions.this.ClassSymbol = ???
      lazy val UncheckedBoundsClass: Definitions.this.Symbol = ???
      lazy val UnspecializedClass: Definitions.this.ClassSymbol = ???
      lazy val UnusedClass: Definitions.this.ClassSymbol = ???
      lazy val VolatileAttr: Definitions.this.ClassSymbol = ???
      lazy val JavaDeprecatedAttr: Definitions.this.ClassSymbol = ???
      lazy val FunctionalInterfaceClass: Definitions.this.ClassSymbol = ???
      lazy val BeanGetterTargetClass: Definitions.this.ClassSymbol = ???
      lazy val BeanSetterTargetClass: Definitions.this.ClassSymbol = ???
      lazy val FieldTargetClass: Definitions.this.ClassSymbol = ???
      lazy val GetterTargetClass: Definitions.this.ClassSymbol = ???
      lazy val ParamTargetClass: Definitions.this.ClassSymbol = ???
      lazy val SetterTargetClass: Definitions.this.ClassSymbol = ???
      lazy val ObjectTargetClass: Definitions.this.ClassSymbol = ???
      lazy val ClassTargetClass: Definitions.this.ClassSymbol = ???
      lazy val MethodTargetClass: Definitions.this.ClassSymbol = ???
      lazy val LanguageFeatureAnnot: Definitions.this.ClassSymbol = ???
      lazy val InheritedAttr: Definitions.this.ClassSymbol = ???
      lazy val JUnitAnnotations: scala.collection.immutable.List[Definitions.this.Symbol] = ???
      lazy val languageFeatureModule: Definitions.this.ModuleSymbol = ???
      final def isTargetAnnotation(sym: Definitions.this.Symbol): scala.Boolean = ???
      lazy val targetAnnotations: scala.Predef.Set[Definitions.this.Symbol] = ???
      def defaultAnnotationTarget(t: Definitions.this.Tree): Definitions.this.Symbol = ???
      lazy val AnnotationDefaultAttr: Definitions.this.ClassSymbol = ???
      private def fatalMissingSymbol(owner: Definitions.this.Symbol, name: Definitions.this.Name, what: scala.Predef.String = ???, addendum: scala.Predef.String = ???): scala.Nothing = ???
      def getLanguageFeature(name: scala.Predef.String, owner: Definitions.this.Symbol = ???): Definitions.this.Symbol = ???
      def termMember(owner: Definitions.this.Symbol, name: scala.Predef.String): Definitions.this.Symbol = ???
      def findNamedMember(fullName: Definitions.this.Name, root: Definitions.this.Symbol): Definitions.this.Symbol = ???
      final def findNamedMember(segs: scala.List[Definitions.this.Name], root: Definitions.this.Symbol): Definitions.this.Symbol = ???
      def getMember(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.Symbol = ???
      def getMemberValue(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.TermSymbol = ???
      def getMemberModule(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.ModuleSymbol = ???
      def getTypeMember(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.TypeSymbol = ???
      def getMemberClass(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.ClassSymbol = ???
      def getMemberMethod(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.TermSymbol = ???
      def getDeclMethod(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.TermSymbol = ???
      def getDeclValue(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.TermSymbol = ???
      private lazy val erasurePhase: scala.reflect.internal.Phase = ???
      def getMemberIfDefined(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.Symbol = ???
      def getDecl(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.Symbol = ???
      def getDeclIfDefined(owner: Definitions.this.Symbol, name: Definitions.this.Name): Definitions.this.Symbol = ???
      private def newAlias(owner: Definitions.this.Symbol, name: Definitions.this.TypeName, alias: Definitions.this.Type): Definitions.this.AliasTypeSymbol = ???
      private def specialPolyClass(name: Definitions.this.TypeName, flags: scala.Long)(parentFn: scala.Function1[Definitions.this.Symbol,Definitions.this.Type]): Definitions.this.ClassSymbol = ???
      def newPolyMethod(typeParamCount: scala.Int, owner: Definitions.this.Symbol, name: Definitions.this.TermName, flags: scala.Long)(createFn: Definitions.this.PolyMethodCreator): Definitions.this.MethodSymbol = ???
      def enterNewPolyMethod(typeParamCount: scala.Int, owner: Definitions.this.Symbol, name: Definitions.this.TermName, flags: scala.Long)(createFn: Definitions.this.PolyMethodCreator): Definitions.this.MethodSymbol = ???
      def newT1NullaryMethod(owner: Definitions.this.Symbol, name: Definitions.this.TermName, flags: scala.Long)(createFn: scala.Function1[Definitions.this.Symbol,Definitions.this.Type]): Definitions.this.MethodSymbol = ???
      def enterNewT1NullaryMethod(owner: Definitions.this.Symbol, name: Definitions.this.TermName, flags: scala.Long)(createFn: scala.Function1[Definitions.this.Symbol,Definitions.this.Type]): Definitions.this.MethodSymbol = ???
      def newT1NilaryMethod(owner: Definitions.this.Symbol, name: Definitions.this.TermName, flags: scala.Long)(createFn: scala.Function1[Definitions.this.Symbol,Definitions.this.Type]): Definitions.this.MethodSymbol = ???
      def enterNewT1NilaryMethod(owner: Definitions.this.Symbol, name: Definitions.this.TermName, flags: scala.Long)(createFn: scala.Function1[Definitions.this.Symbol,Definitions.this.Type]): Definitions.this.MethodSymbol = ???
      def newT1Method(owner: Definitions.this.Symbol, name: Definitions.this.TermName, flags: scala.Long)(createFn: scala.Function1[Definitions.this.Symbol,Definitions.this.Type]): Definitions.this.MethodSymbol = ???
      def enterNewT1Method(owner: Definitions.this.Symbol, name: Definitions.this.TermName, flags: scala.Long)(createFn: scala.Function1[Definitions.this.Symbol,Definitions.this.Type]): Definitions.this.MethodSymbol = ???
      lazy val isPhantomClass: scala.collection.immutable.Set[Definitions.this.Symbol] = ???
      lazy val syntheticCoreClasses: scala.collection.immutable.List[Definitions.this.TypeSymbol{type TypeOfClonedSymbol >: Definitions.this.ClassSymbol <: Definitions.this.TypeSymbol}] = ???
      lazy val syntheticCoreMethods: scala.collection.immutable.List[Definitions.this.MethodSymbol] = ???
      lazy val hijackedCoreClasses: scala.collection.immutable.List[Definitions.this.ClassSymbol] = ???
      lazy val symbolsNotPresentInBytecode: scala.collection.immutable.List[Definitions.this.Symbol{type TypeOfClonedSymbol >: Definitions.this.ClassSymbol with Definitions.this.TermSymbol <: Definitions.this.Symbol{type TypeOfClonedSymbol >: scala.Null <: Definitions.this.Symbol{type NameType >: Definitions.this.TypeName with Definitions.this.TermName <: Definitions.this.Name}; type NameType >: Definitions.this.TypeName with Definitions.this.TermName <: Definitions.this.Name{type ThisNameType >: Definitions.this.TypeName with Definitions.this.TermName <: Definitions.this.Name}}; type NameType >: Definitions.this.TypeName with Definitions.this.TermName <: Definitions.this.Name{def newName(str: scala.Predef.String): Definitions.this.Name{type ThisNameType >: Definitions.this.TypeName with Definitions.this.TermName <: Definitions.this.Name}; def subName(from: scala.Int, to: scala.Int): Definitions.this.Name{type ThisNameType >: Definitions.this.TypeName with Definitions.this.TermName <: Definitions.this.Name}; def companionName: Definitions.this.Name{type ThisNameType >: Definitions.this.TermName with Definitions.this.TypeName <: Definitions.this.Name}; def next: Definitions.this.Name{type ThisNameType >: Definitions.this.TypeName with Definitions.this.TermName <: Definitions.this.Name}; type ThisNameType >: Definitions.this.TypeName with Definitions.this.TermName <: Definitions.this.Name{type ThisNameType >: Definitions.this.TypeName with Definitions.this.TermName <: Definitions.this.Name}}}] = ???
      lazy val isPossibleSyntheticParent: scala.collection.immutable.Set[Definitions.this.Symbol] = ???
      private lazy val boxedValueClassesSet: scala.collection.immutable.Set[Definitions.this.Symbol] = ???
      def isPrimitiveValueClass(sym: Definitions.this.Symbol): scala.Boolean = ???
      def isPrimitiveValueType(tp: Definitions.this.Type): scala.Boolean = ???
      def isBoxedValueClass(sym: Definitions.this.Symbol): scala.Boolean = ???
      def unboxedValueClass(sym: Definitions.this.Symbol): Definitions.this.Symbol = ???
      def isNumericValueType(tp: Definitions.this.Type): scala.Boolean = ???
      lazy val ShowAsInfixAnnotationClass: Definitions.this.Symbol = ???
      def signature(tp: Definitions.this.Type): scala.Predef.String = ???
      def init(): scala.Unit = ???
      class UniverseDependentTypes(universe: Definitions.this.Tree)
      final class RunDefinitions()
    }
  }

  trait ExistentialsAndSkolems { self: scala.reflect.internal.SymbolTable =>

  }

  trait FlagSets extends scala.reflect.api.FlagSets { self: scala.reflect.internal.SymbolTable =>
    type FlagSet = scala.Long
    implicit val FlagSetTag: scala.reflect.ClassTag[FlagSets.this.FlagSet] = ???
    implicit def addFlagOps(left: FlagSets.this.FlagSet): FlagSets.this.FlagOps = ???
    val NoFlags: FlagSets.this.FlagSet = ???
    object Flag extends FlagSets.this.FlagValues {
      val TRAIT: FlagSets.this.FlagSet = ???
      val INTERFACE: FlagSets.this.FlagSet = ???
      val MUTABLE: FlagSets.this.FlagSet = ???
      val MACRO: FlagSets.this.FlagSet = ???
      val DEFERRED: FlagSets.this.FlagSet = ???
      val ABSTRACT: FlagSets.this.FlagSet = ???
      val FINAL: FlagSets.this.FlagSet = ???
      val SEALED: FlagSets.this.FlagSet = ???
      val IMPLICIT: FlagSets.this.FlagSet = ???
      val LAZY: FlagSets.this.FlagSet = ???
      val OVERRIDE: FlagSets.this.FlagSet = ???
      val PRIVATE: FlagSets.this.FlagSet = ???
      val PROTECTED: FlagSets.this.FlagSet = ???
      val LOCAL: FlagSets.this.FlagSet = ???
      val CASE: FlagSets.this.FlagSet = ???
      val ABSOVERRIDE: FlagSets.this.FlagSet = ???
      val BYNAMEPARAM: FlagSets.this.FlagSet = ???
      val PARAM: FlagSets.this.FlagSet = ???
      val COVARIANT: FlagSets.this.FlagSet = ???
      val CONTRAVARIANT: FlagSets.this.FlagSet = ???
      val DEFAULTPARAM: FlagSets.this.FlagSet = ???
      val PRESUPER: FlagSets.this.FlagSet = ???
      val DEFAULTINIT: FlagSets.this.FlagSet = ???
      val ENUM: FlagSets.this.FlagSet = ???
      val PARAMACCESSOR: FlagSets.this.FlagSet = ???
      val CASEACCESSOR: FlagSets.this.FlagSet = ???
      val SYNTHETIC: FlagSets.this.FlagSet = ???
      val ARTIFACT: FlagSets.this.FlagSet = ???
      val STABLE: FlagSets.this.FlagSet = ???
    }
  }

  trait FreshNames { self: scala.reflect.internal.FreshNames with scala.reflect.internal.Names with scala.reflect.internal.StdNames =>

  }

  trait HasFlags {
    def isAbstractOverride: scala.Boolean = ???
    def isCaseAccessor: scala.Boolean = ???
    def isFinal: scala.Boolean = ???
    def isImplicit: scala.Boolean = ???
    def isLazy: scala.Boolean = ???
    def isMacro: scala.Boolean = ???
    def isParamAccessor: scala.Boolean = ???
    def isPrivate: scala.Boolean = ???
    def isProtected: scala.Boolean = ???
    def isPublic: scala.Boolean = ???
    def isSealed: scala.Boolean = ???
    def isSpecialized: scala.Boolean = ???
    def isSynthetic: scala.Boolean = ???
    def isParameter: scala.Boolean = ???
  }

  trait Importers { self: scala.reflect.internal.SymbolTable =>

  }

  trait InfoTransformers { self: scala.reflect.internal.SymbolTable =>

  }

  trait Internals extends scala.reflect.api.Internals { self: scala.reflect.internal.SymbolTable =>
    type Internal = Internals.this.MacroInternalApi
    lazy val internal: Internals.this.Internal = ???
    type Compat = Internals.this.MacroCompatApi
    lazy val compat: Internals.this.Compat = ???
    lazy val treeBuild: Internals.this.TreeGen = ???
  }

  trait Kinds { self: scala.reflect.internal.SymbolTable =>

  }

  trait MacroAnnotionTreeInfo { self: scala.reflect.internal.MacroAnnotionTreeInfo with scala.reflect.internal.TreeInfo =>

  }

  trait Mirrors extends scala.reflect.api.Mirrors { self: scala.reflect.internal.SymbolTable =>
    override type Mirror >: scala.Null <: Mirrors.this.RootsBase
    abstract class RootsBase(rootOwner: Mirrors.this.Symbol) extends scala.reflect.api.Mirror[Mirrors.this.type]()
  }

  final class Mode(val bits: scala.Int) extends scala.AnyVal

  trait Names extends scala.reflect.api.Names {
    def newTermName(s: scala.Predef.String): Names.this.TermName = ???
    def newTypeName(s: scala.Predef.String): Names.this.TypeName = ???
    sealed trait NameHasIsEmpty {
      def isEmpty: scala.Boolean
    }
    sealed abstract class Name(val index: scala.Int, val len: scala.Int, val cachedString: scala.Predef.String) extends Names.this.NameApi() with Names.this.NameHasIsEmpty with java.lang.CharSequence {
      type ThisNameType >: scala.Null <: Names.this.Name
      final def length(): scala.Int = ???
      override final def isEmpty(): scala.Boolean = ???
      override def subSequence(from: scala.Int, to: scala.Int): java.lang.CharSequence = ???
      final def charAt(i: scala.Int): scala.Char = ???
      def decoded: scala.Predef.String = ???
      def encoded: scala.Predef.String = ???
      def encodedName: Name.this.ThisNameType = ???
      def decodedName: Name.this.ThisNameType = ???
      override final def toString(): scala.Predef.String = ???
    }
    implicit val NameTag: scala.reflect.ClassTag[Names.this.Name] = ???
    final class TermName(index0: scala.Int, len0: scala.Int, val next: Names.this.TermName, cachedString: scala.Predef.String) extends Names.this.Name(???, ???, ???) with Names.this.TermNameApi {
      type ThisNameType = Names.this.TermName
      protected def thisName: Names.this.TermName = ???
      def isTermName: scala.Boolean = ???
      def isTypeName: scala.Boolean = ???
      def toTermName: Names.this.TermName = ???
      def toTypeName: Names.this.TypeName = ???
      def newName(str: scala.Predef.String): Names.this.TermName = ???
      def companionName: Names.this.TypeName = ???
      def subName(from: scala.Int, to: scala.Int): Names.this.TermName = ???
      def nameKind: java.lang.String = ???
    }
    implicit val TermNameTag: scala.reflect.ClassTag[Names.this.TermName] = ???
    object TermName extends Names.this.TermNameExtractor() {
      def apply(s: scala.Predef.String): Names.this.TermName = ???
      def unapply(name: Names.this.TermName): scala.Option[scala.Predef.String] = ???
    }
    final class TypeName(index0: scala.Int, len0: scala.Int, val next: Names.this.TypeName, cachedString: scala.Predef.String) extends Names.this.Name(???, ???, ???) with Names.this.TypeNameApi {
      type ThisNameType = Names.this.TypeName
      protected def thisName: Names.this.TypeName = ???
      def isTermName: scala.Boolean = ???
      def isTypeName: scala.Boolean = ???
      def toTermName: Names.this.TermName = ???
      def toTypeName: Names.this.TypeName = ???
      def newName(str: scala.Predef.String): Names.this.TypeName = ???
      def companionName: Names.this.TermName = ???
      def subName(from: scala.Int, to: scala.Int): Names.this.TypeName = ???
      def nameKind: java.lang.String = ???
    }
    implicit val TypeNameTag: scala.reflect.ClassTag[Names.this.TypeName] = ???
    object TypeName extends Names.this.TypeNameExtractor() {
      def apply(s: scala.Predef.String): Names.this.TypeName = ???
      def unapply(name: Names.this.TypeName): scala.Option[scala.Predef.String] = ???
    }
  }

  abstract class Phase(val prev: scala.reflect.internal.Phase) extends scala.Ordered[scala.reflect.internal.Phase]

  trait Positions extends scala.reflect.api.Positions { self: scala.reflect.internal.SymbolTable =>
    type Position = scala.reflect.internal.util.Position
    val NoPosition: scala.reflect.internal.util.NoPosition.type = ???
    implicit val PositionTag: scala.reflect.ClassTag[Positions.this.Position] = ???
    def wrappingPos(default: Positions.this.Position, trees: scala.List[Positions.this.Tree]): Positions.this.Position = ???
    def wrappingPos(trees: scala.List[Positions.this.Tree]): Positions.this.Position = ???
    def atPos[T <: Positions.this.Tree](pos: Positions.this.Position)(tree: T): tree.type = ???
  }

  trait Printers extends scala.reflect.api.Printers { self: scala.reflect.internal.SymbolTable =>
    final type InternalTreePrinter = Printers.this.TreePrinter
    class TreePrinter(out: java.io.PrintWriter) extends Printers.super.TreePrinter {
      def print(args: scala.Any*): scala.Unit = ???
    }
    def newCodePrinter(writer: java.io.PrintWriter, tree: Printers.this.Tree, printRootPkg: scala.Boolean): Printers.this.InternalTreePrinter = ???
    def newTreePrinter(writer: java.io.PrintWriter): Printers.this.InternalTreePrinter = ???
    def newRawTreePrinter(writer: java.io.PrintWriter): Printers.this.RawTreePrinter = ???
    class RawTreePrinter(out: java.io.PrintWriter) extends Printers.super.TreePrinter {
      def print(args: scala.Any*): scala.Unit = ???
    }
    def show(name: Printers.this.Name): scala.Predef.String = ???
    def show(flags: Printers.this.FlagSet): scala.Predef.String = ???
    def show(position: Printers.this.Position): scala.Predef.String = ???
    def showDecl(sym: Printers.this.Symbol): scala.Predef.String = ???
  }

  trait PrivateWithin { self: scala.reflect.internal.SymbolTable =>

  }

  trait ReificationSupport { self: scala.reflect.internal.SymbolTable =>

  }

  abstract class Reporter()

  trait Reporting { self: scala.reflect.internal.Reporting with scala.reflect.internal.Positions =>
    trait RunReporting
    abstract class PerRunReportingBase() {
      def deprecationWarning(pos: Reporting.this.Position, msg: scala.Predef.String, since: scala.Predef.String, site: scala.Predef.String, origin: scala.Predef.String, actions: scala.List[scala.reflect.internal.util.CodeAction] = ???): scala.Unit
    }
  }

  trait ScopeStats { self: scala.reflect.internal.ScopeStats with scala.reflect.internal.util.Statistics =>

  }

  trait Scopes extends scala.reflect.api.Scopes { self: scala.reflect.internal.SymbolTable =>
    class Scope() extends scala.collection.AbstractIterable[Scopes.this.Symbol]() with Scopes.this.ScopeApi with Scopes.this.MemberScopeApi {
      def sorted: scala.List[Scopes.this.Symbol] = ???
      def iterator: scala.Iterator[Scopes.this.Symbol] = ???
      override def filterNot(p: scala.Function1[Scopes.this.Symbol,scala.Boolean]): Scopes.this.Scope = ???
      override def filter(p: scala.Function1[Scopes.this.Symbol,scala.Boolean]): Scopes.this.Scope = ???
    }
    implicit val ScopeTag: scala.reflect.ClassTag[Scopes.this.Scope] = ???
    type MemberScope = Scopes.this.Scope
    implicit val MemberScopeTag: scala.reflect.ClassTag[Scopes.this.MemberScope] = ???
  }

  trait StdAttachments { self: scala.reflect.internal.SymbolTable =>
    trait Attachable {
      def pos: StdAttachments.this.Position = ???
      def `pos_=`(pos: StdAttachments.this.Position): scala.Unit = ???
    }
  }

  trait StdCreators { self: scala.reflect.internal.SymbolTable =>

  }

  trait StdNames { self: scala.reflect.internal.SymbolTable =>
    abstract class CommonNames() extends StdNames.this.NamesApi {
      type NameType >: scala.Null <: StdNames.this.Name
      final val LOCAL_SUFFIX_STRING: " " = ???
      val EMPTY: CommonNames.this.NameType = ???
      val EMPTY_PACKAGE_NAME: CommonNames.this.NameType = ???
      val PACKAGE: CommonNames.this.NameType = ???
      final val ERROR: CommonNames.this.NameType = ???
      final val WILDCARD: CommonNames.this.NameType = ???
    }
    abstract class Keywords() extends StdNames.this.CommonNames()
    abstract class TypeNames() extends StdNames.this.Keywords() with StdNames.this.TypeNamesApi {
      override type NameType = StdNames.this.TypeName
      protected def nameType(name: scala.Predef.String): StdNames.this.TypeName = ???
      final val WILDCARD_STAR: TypeNames.this.NameType = ???
    }
    abstract class TermNames() extends StdNames.this.Keywords() with StdNames.this.TermNamesApi {
      override type NameType = StdNames.this.TermName
      protected def nameType(name: scala.Predef.String): StdNames.this.TermName = ???
      val CONSTRUCTOR: TermNames.this.NameType = ???
      val ROOTPKG: TermNames.this.NameType = ???
    }
    lazy val typeNames: StdNames.this.tpnme.type = ???
    object tpnme extends StdNames.this.TypeNames()
    lazy val termNames: StdNames.this.nme.type = ???
    object nme extends StdNames.this.TermNames()
  }

  abstract class SymbolTable() extends scala.reflect.macros.Universe() with scala.reflect.internal.util.Collections with scala.reflect.internal.Names with scala.reflect.internal.Symbols with scala.reflect.internal.Types with scala.reflect.internal.Variances with scala.reflect.internal.Kinds with scala.reflect.internal.ExistentialsAndSkolems with scala.reflect.internal.FlagSets with scala.reflect.internal.Scopes with scala.reflect.internal.Mirrors with scala.reflect.internal.Definitions with scala.reflect.internal.Constants with scala.reflect.internal.BaseTypeSeqs with scala.reflect.internal.InfoTransformers with scala.reflect.internal.transform.Transforms with scala.reflect.internal.StdNames with scala.reflect.internal.AnnotationInfos with scala.reflect.internal.AnnotationCheckers with scala.reflect.internal.Trees with scala.reflect.internal.Printers with scala.reflect.internal.Positions with scala.reflect.internal.TypeDebugging with scala.reflect.internal.Importers with scala.reflect.internal.CapturedVariables with scala.reflect.internal.StdAttachments with scala.reflect.internal.StdCreators with scala.reflect.internal.ReificationSupport with scala.reflect.internal.PrivateWithin with scala.reflect.internal.pickling.Translations with scala.reflect.internal.FreshNames with scala.reflect.internal.Internals with scala.reflect.internal.Reporting {
    trait ReflectStats extends scala.reflect.internal.BaseTypeSeqsStats with scala.reflect.internal.TypesStats with scala.reflect.internal.SymbolTableStats with scala.reflect.internal.TreesStats with scala.reflect.internal.SymbolsStats with scala.reflect.internal.ScopeStats { self: SymbolTable.this.ReflectStats with scala.reflect.internal.util.Statistics =>

    }
    implicit def lowPriorityNameOrdering[T <: scala.reflect.internal.Names#Name]: scala.Ordering[T] = ???
    type RunId = scala.Int
    def currentRunId: SymbolTable.this.RunId
  }

  trait SymbolTableStats { self: scala.reflect.internal.SymbolTableStats with scala.reflect.internal.TypesStats with scala.reflect.internal.util.Statistics =>

  }

  trait Symbols extends scala.reflect.api.Symbols { self: scala.reflect.internal.SymbolTable =>
    def symbolOf[T](implicit evidence$1: Symbols.this.WeakTypeTag[T]): Symbols.this.TypeSymbol = ???
    abstract class SymbolContextApiImpl() extends Symbols.this.SymbolApi { self: Symbols.this.Symbol =>
      def isExistential: scala.Boolean = ???
      def isParamWithDefault: scala.Boolean = ???
      def isByNameParam: scala.Boolean = ???
      def isImplementationArtifact: scala.Boolean = ???
      def isJava: scala.Boolean = ???
      def isVal: scala.Boolean = ???
      def isVar: scala.Boolean = ???
      def isAbstract: scala.Boolean = ???
      def isPrivateThis: scala.Boolean = ???
      def isProtectedThis: scala.Boolean = ???
      def isJavaEnum: scala.Boolean = ???
      def isJavaAnnotation: scala.Boolean = ???
      def knownDirectSubclasses: scala.Predef.Set[Symbols.this.Symbol] = ???
      def selfType: Symbols.this.Type = ???
      def baseClasses: scala.List[Symbols.this.Symbol] = ???
      def module: Symbols.this.Symbol = ???
      def thisPrefix: Symbols.this.Type = ???
      def typeSignature: Symbols.this.Type = ???
      def typeSignatureIn(site: Symbols.this.Type): Symbols.this.Type = ???
      def toType: Symbols.this.Type = ???
      def toTypeConstructor: Symbols.this.Type = ???
      def getter: Symbols.this.Symbol = ???
      def setter: Symbols.this.Symbol = ???
      def companion: Symbols.this.Symbol = ???
      def infoIn(site: Symbols.this.Type): Symbols.this.Type = ???
      def overrides: scala.List[Symbols.this.Symbol] = ???
      def paramLists: scala.List[scala.List[Symbols.this.Symbol]] = ???
    }
    abstract class Symbol(initOwner: Symbols.this.Symbol, initPos: Symbols.this.Position, initName: Symbols.this.Name) extends Symbols.this.SymbolContextApiImpl() with scala.reflect.internal.HasFlags with Symbols.this.Annotatable[Symbols.this.Symbol] with Symbols.this.Attachable {
      type AccessBoundaryType = Symbols.this.Symbol
      type AnnotationType = Symbols.this.AnnotationInfo
      type TypeOfClonedSymbol >: scala.Null <: Symbols.this.Symbol{type NameType = Symbol.this.NameType}
      final def name: Symbol.this.NameType = ???
      def isAliasType: scala.Boolean = ???
      def isConstructor: scala.Boolean = ???
      def isPackage: scala.Boolean = ???
      def isPackageClass: scala.Boolean = ???
      final def hasFlag(mask: scala.Long): scala.Boolean = ???
      final def hasAllFlags(mask: scala.Long): scala.Boolean = ???
      final def flags: scala.Long = ???
      final def isDerivedValueClass: scala.Boolean = ???
      final def isStable: scala.Boolean = ???
      final def isPrimaryConstructor: scala.Boolean = ???
      def isStatic: scala.Boolean = ???
      def owner: Symbols.this.Symbol = ???
      def `owner_=`(owner: Symbols.this.Symbol): scala.Unit = ???
      final def fullName: scala.Predef.String = ???
      def privateWithin: Symbols.this.Symbol = ???
      final def hasAccessBoundary: scala.Boolean = ???
      def info: Symbols.this.Type = ???
      def `info_=`(info: Symbols.this.Type): scala.Unit = ???
      def typeParams: scala.List[Symbols.this.Symbol] = ???
      def paramss: scala.List[scala.List[Symbols.this.Symbol]] = ???
      def existentialBound: Symbols.this.Type
      def annotations: scala.List[Symbols.this.AnnotationInfo] = ???
      def setAnnotations(annots: scala.List[Symbols.this.AnnotationInfo]): Symbol.this.type = ???
      def withAnnotations(annots: scala.List[Symbols.this.AnnotationInfo]): Symbol.this.type = ???
      def withAnnotation(anno: Symbols.this.AnnotationInfo): Symbol.this.type = ???
      def withoutAnnotations: Symbol.this.type = ???
      def filterAnnotations(p: scala.Function1[Symbols.this.AnnotationInfo,scala.Boolean]): Symbol.this.type = ???
      def alternatives: scala.List[Symbols.this.Symbol] = ???
      def filter(cond: scala.Function1[Symbols.this.Symbol,scala.Boolean]): Symbols.this.Symbol = ???
      def suchThat(cond: scala.Function1[Symbols.this.Symbol,scala.Boolean]): Symbols.this.Symbol = ???
      def cloneSymbolImpl(owner: Symbols.this.Symbol, newFlags: scala.Long): Symbol.this.TypeOfClonedSymbol
      final def accessed: Symbols.this.Symbol = ???
      def companionSymbol: Symbols.this.Symbol = ???
      final def allOverriddenSymbols: scala.List[Symbols.this.Symbol] = ???
      def associatedFile: scala.reflect.io.AbstractFile = ???
      def `associatedFile_=`(f: scala.reflect.io.AbstractFile): scala.Unit = ???
      final def orElse(alt: => Symbols.this.Symbol): Symbols.this.Symbol = ???
      final def map(f: scala.Function1[Symbols.this.Symbol,Symbols.this.Symbol]): Symbols.this.Symbol = ???
    }
    implicit val SymbolTag: scala.reflect.ClassTag[Symbols.this.Symbol] = ???
    class TermSymbol(initOwner: Symbols.this.Symbol, initPos: Symbols.this.Position, initName: Symbols.this.TermName) extends Symbols.this.Symbol(???, ???, ???) with Symbols.this.TermSymbolApi {
      type TypeOfClonedSymbol = Symbols.this.TermSymbol
      final def asNameType(n: Symbols.this.Name): Symbols.this.TermName = ???
      override def companionSymbol: Symbols.this.Symbol = ???
      override def isOverloaded: scala.Boolean = ???
      override def isAccessor: scala.Boolean = ???
      override def isGetter: scala.Boolean = ???
      override def isSetter: scala.Boolean = ???
      override def isConstructor: scala.Boolean = ???
      override def isPackage: scala.Boolean = ???
      def existentialBound: Symbols.this.TypeBounds = ???
      def cloneSymbolImpl(owner: Symbols.this.Symbol, newFlags: scala.Long): Symbols.this.TermSymbol = ???
    }
    implicit val TermSymbolTag: scala.reflect.ClassTag[Symbols.this.TermSymbol] = ???
    class ModuleSymbol(initOwner: Symbols.this.Symbol, initPos: Symbols.this.Position, initName: Symbols.this.TermName) extends Symbols.this.TermSymbol(???, ???, ???) with Symbols.this.ModuleSymbolApi {
      override def associatedFile: scala.reflect.io.AbstractFile = ???
      override def moduleClass: Symbols.this.Symbol = ???
      override def owner: Symbols.this.Symbol = ???
    }
    implicit val ModuleSymbolTag: scala.reflect.ClassTag[Symbols.this.ModuleSymbol] = ???
    class MethodSymbol(initOwner: Symbols.this.Symbol, initPos: Symbols.this.Position, initName: Symbols.this.TermName) extends Symbols.this.TermSymbol(???, ???, ???) with Symbols.this.MethodSymbolApi {
      override def isVarargs: scala.Boolean = ???
      override def returnType: Symbols.this.Type = ???
      override def exceptions: scala.List[Symbols.this.Symbol] = ???
    }
    implicit val MethodSymbolTag: scala.reflect.ClassTag[Symbols.this.MethodSymbol] = ???
    class AliasTypeSymbol(initOwner: Symbols.this.Symbol, initPos: Symbols.this.Position, initName: Symbols.this.TypeName) extends Symbols.this.TypeSymbol(???, ???, ???) {
      type TypeOfClonedSymbol = Symbols.this.TypeSymbol
      override def isContravariant: scala.Boolean = ???
      override def isCovariant: scala.Boolean = ???
      override final def isAliasType: scala.Boolean = ???
      override def cloneSymbolImpl(owner: Symbols.this.Symbol, newFlags: scala.Long): Symbols.this.TypeSymbol = ???
    }
    abstract class TypeSymbol(initOwner: Symbols.this.Symbol, initPos: Symbols.this.Position, initName: Symbols.this.TypeName) extends Symbols.this.Symbol(???, ???, ???) with Symbols.this.TypeSymbolApi {
      final def asNameType(n: Symbols.this.Name): Symbols.this.TypeName = ???
      override def isAbstractType: scala.Boolean = ???
      override def isContravariant: scala.Boolean = ???
      override def isCovariant: scala.Boolean = ???
      def existentialBound: Symbols.this.Type = ???
    }
    implicit val TypeSymbolTag: scala.reflect.ClassTag[Symbols.this.TypeSymbol] = ???
    class TypeSkolem(initOwner: Symbols.this.Symbol, initPos: Symbols.this.Position, initName: Symbols.this.TypeName, origin: scala.AnyRef) extends Symbols.this.TypeSymbol(???, ???, ???) {
      type TypeOfClonedSymbol = Symbols.this.TypeSkolem
      override def isAbstractType: scala.Boolean = ???
      override def existentialBound: Symbols.this.Type = ???
      override def typeParams: scala.List[Symbols.this.Symbol] = ???
      override def cloneSymbolImpl(owner: Symbols.this.Symbol, newFlags: scala.Long): Symbols.this.TypeSkolem = ???
    }
    class ClassSymbol(initOwner: Symbols.this.Symbol, initPos: Symbols.this.Position, initName: Symbols.this.TypeName) extends Symbols.this.TypeSymbol(???, ???, ???) with Symbols.this.ClassSymbolApi {
      type TypeOfClonedSymbol = Symbols.this.ClassSymbol
      override final def isAbstractType: scala.Boolean = ???
      override final def isAliasType: scala.Boolean = ???
      override final def isContravariant: scala.Boolean = ???
      override def isAbstractClass: scala.Boolean = ???
      override def isCaseClass: scala.Boolean = ???
      override def isPackage: scala.Boolean = ???
      override def isPackageClass: scala.Boolean = ???
      override def isTrait: scala.Boolean = ???
      override def isNumeric: scala.Boolean = ???
      override def isPrimitive: scala.Boolean = ???
      override def companionSymbol: Symbols.this.Symbol = ???
      override def existentialBound: Symbols.this.Type = ???
      override def primaryConstructor: Symbols.this.Symbol = ???
      override def associatedFile: scala.reflect.io.AbstractFile = ???
      override def owner: Symbols.this.Symbol = ???
      override def cloneSymbolImpl(owner: Symbols.this.Symbol, newFlags: scala.Long): Symbols.this.ClassSymbol = ???
    }
    implicit val ClassSymbolTag: scala.reflect.ClassTag[Symbols.this.ClassSymbol] = ???
    trait FreeSymbol extends Symbols.this.Symbol
    class FreeTermSymbol(name0: Symbols.this.TermName, value0: => scala.Any, val origin: scala.Predef.String) extends Symbols.this.TermSymbol(???, ???, ???) with Symbols.this.FreeSymbol with Symbols.this.FreeTermSymbolApi {
      def value: scala.Any = ???
    }
    implicit val FreeTermSymbolTag: scala.reflect.ClassTag[Symbols.this.FreeTermSymbol] = ???
    class FreeTypeSymbol(name0: Symbols.this.TypeName, val origin: scala.Predef.String) extends Symbols.this.TypeSkolem(???, ???, ???, ???) with Symbols.this.FreeSymbol with Symbols.this.FreeTypeSymbolApi
    implicit val FreeTypeSymbolTag: scala.reflect.ClassTag[Symbols.this.FreeTypeSymbol] = ???
    class NoSymbol() extends Symbols.this.Symbol(???, ???, ???) {
      final type NameType = Symbols.this.TermName
      type TypeOfClonedSymbol = Symbols.this.NoSymbol
      def asNameType(n: Symbols.this.Name): Symbols.this.TermName = ???
      override def companionSymbol: Symbols.this.NoSymbol = ???
      override def filter(cond: scala.Function1[Symbols.this.Symbol,scala.Boolean]): Symbols.this.NoSymbol = ???
      override def associatedFile: scala.reflect.io.NoAbstractFile.type = ???
      override def owner: Symbols.this.Symbol = ???
      override def alternatives: scala.List[Symbols.this.Symbol] = ???
      override def info: Symbols.this.Type = ???
      override def existentialBound: Symbols.this.Type = ???
      def cloneSymbolImpl(owner: Symbols.this.Symbol, newFlags: scala.Long): scala.Nothing = ???
    }
    lazy val NoSymbol: Symbols.this.NoSymbol = ???
  }

  trait SymbolsStats { self: scala.reflect.internal.SymbolsStats with scala.reflect.internal.util.Statistics =>

  }

  abstract class TreeInfo()

  trait Trees extends scala.reflect.api.Trees { self: scala.reflect.internal.SymbolTable =>
    abstract class Tree() extends Trees.this.TreeContextApiImpl() with Trees.this.Attachable with scala.Product {
      override final def pos: Trees.this.Position = ???
      final def tpe: Trees.this.Type = ???
      def symbol: Trees.this.Symbol = ???
      def `symbol_=`(sym: Trees.this.Symbol): scala.Unit = ???
      def isDef: scala.Boolean = ???
      def isEmpty: scala.Boolean = ???
      def nonEmpty: scala.Boolean = ???
      def canHaveAttrs: scala.Boolean = ???
      def isTerm: scala.Boolean = ???
      def isType: scala.Boolean = ???
      override def equals(that: scala.Any): scala.Boolean = ???
      override def duplicate: Tree.this.type = ???
    }
    abstract class TreeContextApiImpl() extends Trees.this.TreeApi { self: Trees.this.Tree =>
      override def orElse(alt: => Trees.this.Tree): Trees.this.Tree = ???
      override def foreach(f: scala.Function1[Trees.this.Tree,scala.Unit]): scala.Unit = ???
      override def withFilter(f: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.List[Trees.this.Tree] = ???
      override def filter(f: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.List[Trees.this.Tree] = ???
      override def collect[T](pf: scala.PartialFunction[Trees.this.Tree,T]): scala.List[T] = ???
      override def find(p: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.Option[Trees.this.Tree] = ???
      override def exists(p: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.Boolean = ???
      override def forAll(p: scala.Function1[Trees.this.Tree,scala.Boolean]): scala.Boolean = ???
      override def equalsStructure(that: Trees.this.Tree): scala.Boolean = ???
      override def children: scala.List[Trees.this.Tree] = ???
    }
    trait TermTree extends Trees.this.Tree with Trees.this.TermTreeApi
    trait TypTree extends Trees.this.Tree with Trees.this.TypTreeApi
    abstract class SymTree() extends Trees.this.Tree() with Trees.this.SymTreeApi {
      override var symbol: Trees.this.Symbol = ???
    }
    trait NameTree extends Trees.this.Tree with Trees.this.NameTreeApi {
      def namePos: Trees.this.Position = ???
    }
    trait RefTree extends Trees.this.SymTree with Trees.this.NameTree with Trees.this.RefTreeApi
    object RefTree extends Trees.this.RefTreeExtractor() {
      def apply(qualifier: Trees.this.Tree, name: Trees.this.Name): Trees.this.RefTree = ???
      def unapply(refTree: Trees.this.RefTree): scala.Option[scala.Tuple2[Trees.this.Tree,Trees.this.Name]] = ???
    }
    sealed abstract class DefTree() extends Trees.this.SymTree() with Trees.this.NameTree with Trees.this.DefTreeApi {
      override def isDef: scala.Boolean = ???
    }
    sealed abstract class MemberDef() extends Trees.this.DefTree() with Trees.this.MemberDefApi
    case class PackageDef(pid: Trees.this.RefTree, stats: scala.List[Trees.this.Tree]) extends Trees.this.MemberDef() with Trees.this.PackageDefApi with scala.Product with scala.Serializable {
      def name: Trees.this.Name = ???
      def mods: Trees.this.Modifiers = ???
    }
    object PackageDef extends Trees.this.PackageDefExtractor() with java.io.Serializable
    sealed abstract class ImplDef() extends Trees.this.MemberDef() with Trees.this.ImplDefApi
    case class ClassDef(mods: Trees.this.Modifiers, name: Trees.this.TypeName, tparams: scala.List[Trees.this.TypeDef], impl: Trees.this.Template) extends Trees.this.ImplDef() with Trees.this.ClassDefApi with scala.Product with scala.Serializable
    object ClassDef extends Trees.this.ClassDefExtractor() with java.io.Serializable
    case class ModuleDef(mods: Trees.this.Modifiers, name: Trees.this.TermName, impl: Trees.this.Template) extends Trees.this.ImplDef() with Trees.this.ModuleDefApi with scala.Product with scala.Serializable
    object ModuleDef extends Trees.this.ModuleDefExtractor() with java.io.Serializable
    sealed abstract class ValOrDefDef() extends Trees.this.MemberDef() with Trees.this.ValOrDefDefApi
    case class ValDef(mods: Trees.this.Modifiers, name: Trees.this.TermName, tpt: Trees.this.Tree, rhs: Trees.this.Tree) extends Trees.this.ValOrDefDef() with Trees.this.ValDefApi with scala.Product with scala.Serializable
    object ValDef extends Trees.this.ValDefExtractor() with java.io.Serializable
    case class DefDef(mods: Trees.this.Modifiers, name: Trees.this.TermName, tparams: scala.List[Trees.this.TypeDef], vparamss: scala.List[scala.List[Trees.this.ValDef]], tpt: Trees.this.Tree, rhs: Trees.this.Tree) extends Trees.this.ValOrDefDef() with Trees.this.DefDefApi with scala.Product with scala.Serializable
    object DefDef extends Trees.this.DefDefExtractor() with java.io.Serializable
    case class TypeDef(mods: Trees.this.Modifiers, name: Trees.this.TypeName, tparams: scala.List[Trees.this.TypeDef], rhs: Trees.this.Tree) extends Trees.this.MemberDef() with Trees.this.TypeDefApi with scala.Product with scala.Serializable
    object TypeDef extends Trees.this.TypeDefExtractor() with java.io.Serializable
    case class LabelDef(name: Trees.this.TermName, params: scala.List[Trees.this.Ident], rhs: Trees.this.Tree) extends Trees.this.DefTree() with Trees.this.TermTree with Trees.this.LabelDefApi with scala.Product with scala.Serializable
    object LabelDef extends Trees.this.LabelDefExtractor() with java.io.Serializable
    case class ImportSelector(name: Trees.this.Name, namePos: scala.Int, rename: Trees.this.Name, renamePos: scala.Int) extends Trees.this.ImportSelectorApi with scala.Product with scala.Serializable {
      def isWildcard: scala.Boolean = ???
      def isMask: scala.Boolean = ???
      def isRename: scala.Boolean = ???
      def isSpecific: scala.Boolean = ???
    }
    object ImportSelector extends Trees.this.ImportSelectorExtractor() with java.io.Serializable
    case class Import(expr: Trees.this.Tree, selectors: scala.List[Trees.this.ImportSelector]) extends Trees.this.SymTree() with Trees.this.ImportApi with scala.Product with scala.Serializable
    object Import extends Trees.this.ImportExtractor() with java.io.Serializable
    case class Template(parents: scala.List[Trees.this.Tree], self: Trees.this.ValDef, body: scala.List[Trees.this.Tree]) extends Trees.this.SymTree() with Trees.this.TemplateApi with scala.Product with scala.Serializable
    object Template extends Trees.this.TemplateExtractor() with java.io.Serializable
    case class Block(stats: scala.List[Trees.this.Tree], expr: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.BlockApi with scala.Product with scala.Serializable
    object Block extends Trees.this.BlockExtractor() with java.io.Serializable
    case class CaseDef(pat: Trees.this.Tree, guard: Trees.this.Tree, body: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.CaseDefApi with scala.Product with scala.Serializable
    object CaseDef extends Trees.this.CaseDefExtractor() with java.io.Serializable
    case class Alternative(trees: scala.List[Trees.this.Tree]) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.AlternativeApi with scala.Product with scala.Serializable
    object Alternative extends Trees.this.AlternativeExtractor() with java.io.Serializable
    case class Star(elem: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.StarApi with scala.Product with scala.Serializable
    object Star extends Trees.this.StarExtractor() with java.io.Serializable
    case class Bind(name: Trees.this.Name, body: Trees.this.Tree) extends Trees.this.DefTree() with Trees.this.BindApi with scala.Product with scala.Serializable
    object Bind extends Trees.this.BindExtractor() with java.io.Serializable
    case class UnApply(fun: Trees.this.Tree, args: scala.List[Trees.this.Tree]) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.UnApplyApi with scala.Product with scala.Serializable
    object UnApply extends Trees.this.UnApplyExtractor() with java.io.Serializable
    case class Function(vparams: scala.List[Trees.this.ValDef], body: Trees.this.Tree) extends Trees.this.SymTree() with Trees.this.TermTree with Trees.this.FunctionApi with scala.Product with scala.Serializable
    object Function extends Trees.this.FunctionExtractor() with java.io.Serializable
    case class Assign(lhs: Trees.this.Tree, rhs: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.AssignApi with scala.Product with scala.Serializable
    object Assign extends Trees.this.AssignExtractor() with java.io.Serializable
    case class NamedArg(lhs: Trees.this.Tree, rhs: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.NamedArgApi with scala.Product with scala.Serializable
    object NamedArg extends Trees.this.NamedArgExtractor() with java.io.Serializable
    case class If(cond: Trees.this.Tree, thenp: Trees.this.Tree, elsep: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.IfApi with scala.Product with scala.Serializable
    object If extends Trees.this.IfExtractor() with java.io.Serializable
    case class Match(selector: Trees.this.Tree, cases: scala.List[Trees.this.CaseDef]) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.MatchApi with scala.Product with scala.Serializable
    object Match extends Trees.this.MatchExtractor() with java.io.Serializable
    case class Return(expr: Trees.this.Tree) extends Trees.this.SymTree() with Trees.this.TermTree with Trees.this.ReturnApi with scala.Product with scala.Serializable
    object Return extends Trees.this.ReturnExtractor() with java.io.Serializable
    case class Try(block: Trees.this.Tree, catches: scala.List[Trees.this.CaseDef], finalizer: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.TryApi with scala.Product with scala.Serializable
    object Try extends Trees.this.TryExtractor() with java.io.Serializable
    case class Throw(expr: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.ThrowApi with scala.Product with scala.Serializable
    object Throw extends Trees.this.ThrowExtractor() with java.io.Serializable
    case class New(tpt: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.NewApi with scala.Product with scala.Serializable
    object New extends Trees.this.NewExtractor() with java.io.Serializable
    case class Typed(expr: Trees.this.Tree, tpt: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.TypedApi with scala.Product with scala.Serializable
    object Typed extends Trees.this.TypedExtractor() with java.io.Serializable
    abstract class GenericApply() extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.GenericApplyApi
    case class TypeApply(fun: Trees.this.Tree, args: scala.List[Trees.this.Tree]) extends Trees.this.GenericApply() with Trees.this.TypeApplyApi with scala.Product with scala.Serializable {
      override def symbol: Trees.this.Symbol = ???
    }
    object TypeApply extends Trees.this.TypeApplyExtractor() with java.io.Serializable
    case class Apply(fun: Trees.this.Tree, args: scala.List[Trees.this.Tree]) extends Trees.this.GenericApply() with Trees.this.ApplyApi with scala.Product with scala.Serializable {
      override def symbol: Trees.this.Symbol = ???
    }
    object Apply extends Trees.this.ApplyExtractor() with java.io.Serializable
    def ApplyConstructor(tpt: Trees.this.Tree, args: scala.List[Trees.this.Tree]): Trees.this.Apply = ???
    case class Super(qual: Trees.this.Tree, mix: Trees.this.TypeName) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.SuperApi with scala.Product with scala.Serializable {
      override def symbol: Trees.this.Symbol = ???
    }
    object Super extends Trees.this.SuperExtractor() with java.io.Serializable
    case class This(qual: Trees.this.TypeName) extends Trees.this.SymTree() with Trees.this.TermTree with Trees.this.ThisApi with scala.Product with scala.Serializable
    object This extends Trees.this.ThisExtractor() with java.io.Serializable
    case class Select(qualifier: Trees.this.Tree, name: Trees.this.Name) extends Trees.this.SymTree() with Trees.this.RefTree with Trees.this.SelectApi with scala.Product with scala.Serializable
    object Select extends Trees.this.SelectExtractor() with java.io.Serializable
    case class Ident(name: Trees.this.Name) extends Trees.this.SymTree() with Trees.this.RefTree with Trees.this.IdentApi with scala.Product with scala.Serializable {
      def qualifier: Trees.this.Tree = ???
      def isBackquoted: scala.Boolean = ???
    }
    object Ident extends Trees.this.IdentExtractor() with java.io.Serializable
    case class ReferenceToBoxed(ident: Trees.this.Ident) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.ReferenceToBoxedApi with scala.Product with scala.Serializable {
      override def symbol: Trees.this.Symbol = ???
    }
    object ReferenceToBoxed extends Trees.this.ReferenceToBoxedExtractor() with java.io.Serializable
    case class Literal(value: Trees.this.Constant) extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.LiteralApi with scala.Product with scala.Serializable
    object Literal extends Trees.this.LiteralExtractor() with java.io.Serializable
    case class Annotated(annot: Trees.this.Tree, arg: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.AnnotatedApi with scala.Product with scala.Serializable
    object Annotated extends Trees.this.AnnotatedExtractor() with java.io.Serializable
    case class SingletonTypeTree(ref: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TypTree with Trees.this.SingletonTypeTreeApi with scala.Product with scala.Serializable
    object SingletonTypeTree extends Trees.this.SingletonTypeTreeExtractor() with java.io.Serializable
    case class SelectFromTypeTree(qualifier: Trees.this.Tree, name: Trees.this.TypeName) extends Trees.this.SymTree() with Trees.this.RefTree with Trees.this.TypTree with Trees.this.SelectFromTypeTreeApi with scala.Product with scala.Serializable
    object SelectFromTypeTree extends Trees.this.SelectFromTypeTreeExtractor() with java.io.Serializable
    case class CompoundTypeTree(templ: Trees.this.Template) extends Trees.this.Tree() with Trees.this.TypTree with Trees.this.CompoundTypeTreeApi with scala.Product with scala.Serializable
    object CompoundTypeTree extends Trees.this.CompoundTypeTreeExtractor() with java.io.Serializable
    case class AppliedTypeTree(tpt: Trees.this.Tree, args: scala.List[Trees.this.Tree]) extends Trees.this.Tree() with Trees.this.TypTree with Trees.this.AppliedTypeTreeApi with scala.Product with scala.Serializable {
      override def symbol: Trees.this.Symbol = ???
    }
    object AppliedTypeTree extends Trees.this.AppliedTypeTreeExtractor() with java.io.Serializable
    case class TypeBoundsTree(lo: Trees.this.Tree, hi: Trees.this.Tree) extends Trees.this.Tree() with Trees.this.TypTree with Trees.this.TypeBoundsTreeApi with scala.Product with scala.Serializable
    object TypeBoundsTree extends Trees.this.TypeBoundsTreeExtractor() with java.io.Serializable
    case class ExistentialTypeTree(tpt: Trees.this.Tree, whereClauses: scala.List[Trees.this.MemberDef]) extends Trees.this.Tree() with Trees.this.TypTree with Trees.this.ExistentialTypeTreeApi with scala.Product with scala.Serializable
    object ExistentialTypeTree extends Trees.this.ExistentialTypeTreeExtractor() with java.io.Serializable
    case class TypeTree() extends Trees.this.Tree() with Trees.this.TypTree with Trees.this.TypeTreeApi with scala.Product with scala.Serializable {
      override def symbol: Trees.this.Symbol = ???
      override def isEmpty: scala.Boolean = ???
      def original: Trees.this.Tree = ???
    }
    object TypeTree extends Trees.this.TypeTreeExtractor() with java.io.Serializable
    def TypeTree(tp: Trees.this.Type): Trees.this.TypeTree = ???
    abstract class InternalTreeCopierOps() extends Trees.this.TreeCopierOps()
    case class Modifiers(flags: scala.Long, privateWithin: Trees.this.Name, annotations: scala.List[Trees.this.Tree]) extends Trees.this.ModifiersApi() with scala.reflect.internal.HasFlags with scala.Product with scala.Serializable {
      type AccessBoundaryType = Trees.this.Name
      type AnnotationType = Trees.this.Tree
      def hasAccessBoundary: scala.Boolean = ???
      def hasAllFlags(mask: scala.Long): scala.Boolean = ???
      def hasFlag(flag: scala.Long): scala.Boolean = ???
    }
    object Modifiers extends Trees.this.ModifiersExtractor() with java.io.Serializable
    implicit val ModifiersTag: scala.reflect.ClassTag[Trees.this.Modifiers] = ???
    trait CannotHaveAttrs extends Trees.this.Tree {
      override def canHaveAttrs: scala.Boolean = ???
    }
    case object EmptyTree extends Trees.this.Tree() with Trees.this.TermTree with Trees.this.CannotHaveAttrs with scala.Product with scala.Serializable {
      override def isEmpty: scala.Boolean = ???
    }
    object noSelfType extends Trees.this.ValDef(???, ???, ???, ???) with Trees.this.CannotHaveAttrs
    object pendingSuperCall extends Trees.this.Apply(???, ???) with Trees.this.CannotHaveAttrs
    lazy val emptyValDef: Trees.this.noSelfType.type = ???
    def CaseDef(pat: Trees.this.Tree, body: Trees.this.Tree): Trees.this.CaseDef = ???
    def Bind(sym: Trees.this.Symbol, body: Trees.this.Tree): Trees.this.Bind = ???
    def Try(body: Trees.this.Tree, cases: scala.Tuple2[Trees.this.Tree,Trees.this.Tree]*): Trees.this.Try = ???
    def Throw(tpe: Trees.this.Type, args: Trees.this.Tree*): Trees.this.Throw = ???
    def Apply(sym: Trees.this.Symbol, args: Trees.this.Tree*): Trees.this.Tree = ???
    def New(tpt: Trees.this.Tree, argss: scala.List[scala.List[Trees.this.Tree]]): Trees.this.Tree = ???
    def New(tpe: Trees.this.Type, args: Trees.this.Tree*): Trees.this.Tree = ???
    def New(sym: Trees.this.Symbol, args: Trees.this.Tree*): Trees.this.Tree = ???
    def Super(sym: Trees.this.Symbol, mix: Trees.this.TypeName): Trees.this.Tree = ???
    def This(sym: Trees.this.Symbol): Trees.this.Tree = ???
    def Select(qualifier: Trees.this.Tree, name: scala.Predef.String): Trees.this.Select = ???
    def Select(qualifier: Trees.this.Tree, sym: Trees.this.Symbol): Trees.this.Select = ???
    def Ident(name: scala.Predef.String): Trees.this.Ident = ???
    def Ident(sym: Trees.this.Symbol): Trees.this.Ident = ???
    def Block(stats: Trees.this.Tree*): Trees.this.Block = ???
    implicit val AlternativeTag: scala.reflect.ClassTag[Trees.this.Alternative] = ???
    implicit val AnnotatedTag: scala.reflect.ClassTag[Trees.this.Annotated] = ???
    implicit val AppliedTypeTreeTag: scala.reflect.ClassTag[Trees.this.AppliedTypeTree] = ???
    implicit val ApplyTag: scala.reflect.ClassTag[Trees.this.Apply] = ???
    implicit val NamedArgTag: scala.reflect.ClassTag[Trees.this.NamedArg] = ???
    implicit val AssignTag: scala.reflect.ClassTag[Trees.this.Assign] = ???
    implicit val BindTag: scala.reflect.ClassTag[Trees.this.Bind] = ???
    implicit val BlockTag: scala.reflect.ClassTag[Trees.this.Block] = ???
    implicit val CaseDefTag: scala.reflect.ClassTag[Trees.this.CaseDef] = ???
    implicit val ClassDefTag: scala.reflect.ClassTag[Trees.this.ClassDef] = ???
    implicit val CompoundTypeTreeTag: scala.reflect.ClassTag[Trees.this.CompoundTypeTree] = ???
    implicit val DefDefTag: scala.reflect.ClassTag[Trees.this.DefDef] = ???
    implicit val DefTreeTag: scala.reflect.ClassTag[Trees.this.DefTree] = ???
    implicit val ExistentialTypeTreeTag: scala.reflect.ClassTag[Trees.this.ExistentialTypeTree] = ???
    implicit val FunctionTag: scala.reflect.ClassTag[Trees.this.Function] = ???
    implicit val GenericApplyTag: scala.reflect.ClassTag[Trees.this.GenericApply] = ???
    implicit val IdentTag: scala.reflect.ClassTag[Trees.this.Ident] = ???
    implicit val IfTag: scala.reflect.ClassTag[Trees.this.If] = ???
    implicit val ImplDefTag: scala.reflect.ClassTag[Trees.this.ImplDef] = ???
    implicit val ImportSelectorTag: scala.reflect.ClassTag[Trees.this.ImportSelector] = ???
    implicit val ImportTag: scala.reflect.ClassTag[Trees.this.Import] = ???
    implicit val LabelDefTag: scala.reflect.ClassTag[Trees.this.LabelDef] = ???
    implicit val LiteralTag: scala.reflect.ClassTag[Trees.this.Literal] = ???
    implicit val MatchTag: scala.reflect.ClassTag[Trees.this.Match] = ???
    implicit val MemberDefTag: scala.reflect.ClassTag[Trees.this.MemberDef] = ???
    implicit val ModuleDefTag: scala.reflect.ClassTag[Trees.this.ModuleDef] = ???
    implicit val NameTreeTag: scala.reflect.ClassTag[Trees.this.NameTree] = ???
    implicit val NewTag: scala.reflect.ClassTag[Trees.this.New] = ???
    implicit val PackageDefTag: scala.reflect.ClassTag[Trees.this.PackageDef] = ???
    implicit val ReferenceToBoxedTag: scala.reflect.ClassTag[Trees.this.ReferenceToBoxed] = ???
    implicit val RefTreeTag: scala.reflect.ClassTag[Trees.this.RefTree] = ???
    implicit val ReturnTag: scala.reflect.ClassTag[Trees.this.Return] = ???
    implicit val SelectFromTypeTreeTag: scala.reflect.ClassTag[Trees.this.SelectFromTypeTree] = ???
    implicit val SelectTag: scala.reflect.ClassTag[Trees.this.Select] = ???
    implicit val SingletonTypeTreeTag: scala.reflect.ClassTag[Trees.this.SingletonTypeTree] = ???
    implicit val StarTag: scala.reflect.ClassTag[Trees.this.Star] = ???
    implicit val SuperTag: scala.reflect.ClassTag[Trees.this.Super] = ???
    implicit val SymTreeTag: scala.reflect.ClassTag[Trees.this.SymTree] = ???
    implicit val TemplateTag: scala.reflect.ClassTag[Trees.this.Template] = ???
    implicit val TermTreeTag: scala.reflect.ClassTag[Trees.this.TermTree] = ???
    implicit val ThisTag: scala.reflect.ClassTag[Trees.this.This] = ???
    implicit val ThrowTag: scala.reflect.ClassTag[Trees.this.Throw] = ???
    implicit val TreeTag: scala.reflect.ClassTag[Trees.this.Tree] = ???
    implicit val TryTag: scala.reflect.ClassTag[Trees.this.Try] = ???
    implicit val TypTreeTag: scala.reflect.ClassTag[Trees.this.TypTree] = ???
    implicit val TypeApplyTag: scala.reflect.ClassTag[Trees.this.TypeApply] = ???
    implicit val TypeBoundsTreeTag: scala.reflect.ClassTag[Trees.this.TypeBoundsTree] = ???
    implicit val TypeDefTag: scala.reflect.ClassTag[Trees.this.TypeDef] = ???
    implicit val TypeTreeTag: scala.reflect.ClassTag[Trees.this.TypeTree] = ???
    implicit val TypedTag: scala.reflect.ClassTag[Trees.this.Typed] = ???
    implicit val UnApplyTag: scala.reflect.ClassTag[Trees.this.UnApply] = ???
    implicit val ValDefTag: scala.reflect.ClassTag[Trees.this.ValDef] = ???
    implicit val ValOrDefDefTag: scala.reflect.ClassTag[Trees.this.ValOrDefDef] = ???
  }

  trait TreesStats { self: scala.reflect.internal.TreesStats with scala.reflect.internal.util.Statistics =>

  }

  trait TypeDebugging { self: scala.reflect.internal.SymbolTable =>

  }

  trait Types extends scala.reflect.api.Types with scala.reflect.internal.tpe.TypeComparers with scala.reflect.internal.tpe.TypeToStrings with scala.reflect.internal.tpe.CommonOwners with scala.reflect.internal.tpe.GlbLubs with scala.reflect.internal.tpe.TypeMaps with scala.reflect.internal.tpe.TypeConstraints with scala.reflect.internal.tpe.FindMembers with scala.reflect.internal.util.Collections { self: scala.reflect.internal.SymbolTable =>
    trait SimpleTypeProxy extends Types.this.Type {
      override def typeConstructor: Types.this.Type = ???
      override def paramss: scala.List[scala.List[Types.this.Symbol]] = ???
      override def termSymbol: Types.this.Symbol = ???
      override def typeParams: scala.List[Types.this.Symbol] = ???
      override def typeSymbol: Types.this.Symbol = ???
      override def widen: Types.this.Type = ???
      override def decls: Types.this.Scope = ???
      override def baseType(clazz: Types.this.Symbol): Types.this.Type = ???
      override def baseClasses: scala.List[Types.this.Symbol] = ???
    }
    trait RewrappingTypeProxy extends Types.this.Type with Types.this.SimpleTypeProxy {
      protected def rewrap(newtp: Types.this.Type): Types.this.Type
      override def widen: Types.this.Type = ???
      override def resultType: Types.this.Type = ???
      override def paramss: scala.List[scala.List[Types.this.Symbol]] = ???
      override def typeArgs: scala.List[Types.this.Type] = ???
      override def normalize: Types.this.Type = ???
      override def etaExpand: Types.this.Type = ???
      override def dealias: Types.this.Type = ???
      override def withAnnotations(annots: scala.List[Types.this.AnnotationInfo]): Types.this.Type = ???
      override def withoutAnnotations: Types.this.Type = ???
    }
    abstract class TypeApiImpl() extends Types.this.TypeApi() { self: Types.this.Type =>
      def declaration(name: Types.this.Name): Types.this.Symbol = ???
      def declarations: Types.this.Scope = ???
      def erasure: Types.this.Type = ???
      def substituteSymbols(from: scala.List[Types.this.Symbol], to: scala.List[Types.this.Symbol]): Types.this.Type = ???
      def substituteTypes(from: scala.List[Types.this.Symbol], to: scala.List[Types.this.Type]): Types.this.Type = ???
      def companion: Types.this.Type = ???
      def paramLists: scala.List[scala.List[Types.this.Symbol]] = ???
    }
    abstract class Type() extends Types.this.TypeApiImpl() with Types.this.Annotatable[Types.this.Type] {
      def takesTypeArgs: scala.Boolean = ???
      def termSymbol: Types.this.Symbol = ???
      def typeSymbol: Types.this.Symbol = ???
      def widen: Types.this.Type = ???
      def typeConstructor: Types.this.Type = ???
      def typeArgs: scala.List[Types.this.Type] = ???
      def resultType: Types.this.Type = ???
      final def finalResultType: Types.this.Type = ???
      def paramss: scala.List[scala.List[Types.this.Symbol]] = ???
      def typeParams: scala.List[Types.this.Symbol] = ???
      def normalize: Types.this.Type = ???
      def etaExpand: Types.this.Type = ???
      def dealias: Types.this.Type = ???
      def decls: Types.this.Scope = ???
      def decl(name: Types.this.Name): Types.this.Symbol = ???
      def members: Types.this.Scope = ???
      def member(name: Types.this.Name): Types.this.Symbol = ???
      def baseType(clazz: Types.this.Symbol): Types.this.Type = ???
      def asSeenFrom(pre: Types.this.Type, clazz: Types.this.Symbol): Types.this.Type = ???
      final def orElse(alt: => Types.this.Type): Types.this.Type = ???
      def find(p: scala.Function1[Types.this.Type,scala.Boolean]): scala.Option[Types.this.Type] = ???
      def foreach(f: scala.Function1[Types.this.Type,scala.Unit]): scala.Unit = ???
      def map(f: scala.Function1[Types.this.Type,Types.this.Type]): Types.this.Type = ???
      def exists(p: scala.Function1[Types.this.Type,scala.Boolean]): scala.Boolean = ???
      def contains(sym: Types.this.Symbol): scala.Boolean = ???
      def <:<(that: Types.this.Type): scala.Boolean = ???
      def `weak_<:<`(that: Types.this.Type): scala.Boolean = ???
      def =:=(that: Types.this.Type): scala.Boolean = ???
      def baseClasses: scala.List[Types.this.Symbol] = ???
      def annotations: scala.List[Types.this.AnnotationInfo] = ???
      def withoutAnnotations: Types.this.Type = ???
      def filterAnnotations(p: scala.Function1[Types.this.AnnotationInfo,scala.Boolean]): Types.this.Type = ???
      def setAnnotations(annots: scala.List[Types.this.AnnotationInfo]): Types.this.Type = ???
      def withAnnotations(annots: scala.List[Types.this.AnnotationInfo]): Types.this.Type = ???
      def withAnnotation(anno: Types.this.AnnotationInfo): Types.this.Type = ???
    }
    abstract class UniqueType() extends Types.this.Type() with scala.Product
    abstract class SubType() extends Types.this.UniqueType()
    abstract class SingletonType() extends Types.this.SubType() with Types.this.SimpleTypeProxy with Types.this.SingletonTypeApi
    case object WildcardType extends Types.this.ProtoType() with scala.Product with scala.Serializable
    case class BoundedWildcardType(override val bounds: Types.this.TypeBounds) extends Types.this.ProtoType() with Types.this.BoundedWildcardTypeApi with scala.Product with scala.Serializable {
      override def members: Types.this.Scope = ???
    }
    object BoundedWildcardType extends Types.this.BoundedWildcardTypeExtractor() with java.io.Serializable
    abstract class ProtoType() extends Types.this.Type() {
      override def members: Types.this.Scope = ???
    }
    case object NoType extends Types.this.Type() with scala.Product with scala.Serializable
    case object NoPrefix extends Types.this.Type() with scala.Product with scala.Serializable
    abstract case class ThisType(sym: Types.this.Symbol) extends Types.this.SingletonType() with Types.this.ThisTypeApi with scala.Product with scala.Serializable
    object ThisType extends Types.this.ThisTypeExtractor() with java.io.Serializable
    abstract case class SingleType(pre: Types.this.Type, sym: Types.this.Symbol) extends Types.this.SingletonType() with Types.this.SingleTypeApi with scala.Product with scala.Serializable
    object SingleType extends Types.this.SingleTypeExtractor() with java.io.Serializable
    abstract case class SuperType(thistpe: Types.this.Type, supertpe: Types.this.Type) extends Types.this.SingletonType() with Types.this.SuperTypeApi with scala.Product with scala.Serializable
    object SuperType extends Types.this.SuperTypeExtractor() with java.io.Serializable
    abstract case class TypeBounds(lo: Types.this.Type, hi: Types.this.Type) extends Types.this.SubType() with Types.this.TypeBoundsApi with scala.Product with scala.Serializable
    object TypeBounds extends Types.this.TypeBoundsExtractor() with java.io.Serializable
    abstract class CompoundType() extends Types.this.Type() with Types.this.CompoundTypeApi {
      override def baseClasses: scala.List[Types.this.Symbol] = ???
      override def baseType(sym: Types.this.Symbol): Types.this.Type = ???
    }
    case class RefinedType(override val parents: scala.List[Types.this.Type], override val decls: Types.this.Scope) extends Types.this.CompoundType() with Types.this.RefinedTypeApi with scala.Product with scala.Serializable {
      override def typeParams: scala.List[Types.this.Symbol] = ???
      override def typeConstructor: Types.this.Type = ???
      override final def normalize: Types.this.Type = ???
      override final def etaExpand: Types.this.Type = ???
    }
    object RefinedType extends Types.this.RefinedTypeExtractor() with java.io.Serializable
    case class ClassInfoType(override val parents: scala.List[Types.this.Type], override val decls: Types.this.Scope, override val typeSymbol: Types.this.Symbol) extends Types.this.CompoundType() with Types.this.ClassInfoTypeApi with scala.Product with scala.Serializable
    object ClassInfoType extends Types.this.ClassInfoTypeExtractor() with java.io.Serializable
    abstract class ConstantType() extends Types.this.SingletonType() with Types.this.ConstantTypeApi
    object ConstantType extends Types.this.ConstantTypeExtractor() {
      def unapply(tpe: Types.this.ConstantType): scala.Some[Types.this.Constant] = ???
    }
    class NoArgsTypeRef(pre0: Types.this.Type, sym0: Types.this.Symbol) extends Types.this.TypeRef(???, ???, ???) {
      override def typeParams: scala.List[Types.this.Symbol] = ???
      override def typeConstructor: Types.this.NoArgsTypeRef = ???
    }
    abstract case class TypeRef(pre: Types.this.Type, sym: Types.this.Symbol, args: scala.List[Types.this.Type]) extends Types.this.UniqueType() with Types.this.TypeRefApi with scala.Product with scala.Serializable {
      override final def equals(other: scala.Any): scala.Boolean = ???
      override final def baseType(clazz: Types.this.Symbol): Types.this.Type = ???
      override final def normalize: Types.this.Type = ???
      override final def etaExpand: Types.this.Type = ???
      override def termSymbol: Types.this.Symbol = ???
      override def typeArgs: scala.List[Types.this.Type] = ???
      override def typeSymbol: Types.this.Symbol = ???
      override def baseClasses: scala.List[Types.this.Symbol] = ???
      override def decls: Types.this.Scope = ???
    }
    object TypeRef extends Types.this.TypeRefExtractor() with java.io.Serializable
    private[internal] final class ObjectTpeJavaRef() extends Types.this.NoArgsTypeRef(???, ???) {
      override def contains(sym0: Types.this.Symbol): scala.Boolean = ???
    }
    case class MethodType(override val params: scala.List[Types.this.Symbol], override val resultType: Types.this.Type) extends Types.this.Type() with Types.this.MethodTypeApi with scala.Product with scala.Serializable {
      override def paramss: scala.List[scala.List[Types.this.Symbol]] = ???
    }
    object MethodType extends Types.this.MethodTypeExtractor() with java.io.Serializable
    case class NullaryMethodType(override val resultType: Types.this.Type) extends Types.this.Type() with Types.this.NullaryMethodTypeApi with scala.Product with scala.Serializable {
      override def termSymbol: Types.this.Symbol = ???
      override def typeSymbol: Types.this.Symbol = ???
      override def decls: Types.this.Scope = ???
      override def baseClasses: scala.List[Types.this.Symbol] = ???
      override def baseType(clazz: Types.this.Symbol): Types.this.Type = ???
    }
    object NullaryMethodType extends Types.this.NullaryMethodTypeExtractor() with java.io.Serializable
    case class PolyType(override val typeParams: scala.List[Types.this.Symbol], override val resultType: Types.this.Type) extends Types.this.Type() with Types.this.PolyTypeApi with scala.Product with scala.Serializable {
      override def paramss: scala.List[scala.List[Types.this.Symbol]] = ???
      override def decls: Types.this.Scope = ???
      override def termSymbol: Types.this.Symbol = ???
      override def typeSymbol: Types.this.Symbol = ???
      override def baseClasses: scala.List[Types.this.Symbol] = ???
      override def baseType(clazz: Types.this.Symbol): Types.this.Type = ???
    }
    object PolyType extends Types.this.PolyTypeExtractor() with java.io.Serializable
    case class ExistentialType(quantified: scala.List[Types.this.Symbol], override val underlying: Types.this.Type) extends Types.this.Type() with Types.this.RewrappingTypeProxy with Types.this.ExistentialTypeApi with scala.Product with scala.Serializable {
      protected override def rewrap(newtp: Types.this.Type): Types.this.Type = ???
      override def typeArgs: scala.collection.immutable.List[Types.this.Type] = ???
      override def baseType(clazz: Types.this.Symbol): Types.this.Type = ???
    }
    object ExistentialType extends Types.this.ExistentialTypeExtractor() with java.io.Serializable
    case class AnnotatedType(override val annotations: scala.List[Types.this.AnnotationInfo], override val underlying: Types.this.Type) extends Types.this.Type() with Types.this.RewrappingTypeProxy with Types.this.AnnotatedTypeApi with scala.Product with scala.Serializable {
      protected override def rewrap(tp: Types.this.Type): Types.this.AnnotatedType = ???
      override def filterAnnotations(p: scala.Function1[Types.this.AnnotationInfo,scala.Boolean]): Types.this.Type = ???
      override def setAnnotations(annots: scala.List[Types.this.AnnotationInfo]): Types.this.Type = ???
      override def withAnnotations(annots: scala.List[Types.this.AnnotationInfo]): Types.this.Type = ???
      override def withAnnotation(anno: Types.this.AnnotationInfo): Types.this.Type = ???
      override def withoutAnnotations: Types.this.Type = ???
    }
    object AnnotatedType extends Types.this.AnnotatedTypeExtractor() with java.io.Serializable
    def appliedType(tycon: Types.this.Type, args: scala.List[Types.this.Type]): Types.this.Type = ???
    def appliedType(tycon: Types.this.Type, args: Types.this.Type*): Types.this.Type = ???
    def appliedType(tyconSym: Types.this.Symbol, args: scala.List[Types.this.Type]): Types.this.Type = ???
    def appliedType(tyconSym: Types.this.Symbol, args: Types.this.Type*): Types.this.Type = ???
    implicit val AnnotatedTypeTag: scala.reflect.ClassTag[Types.this.AnnotatedType] = ???
    implicit val BoundedWildcardTypeTag: scala.reflect.ClassTag[Types.this.BoundedWildcardType] = ???
    implicit val ClassInfoTypeTag: scala.reflect.ClassTag[Types.this.ClassInfoType] = ???
    implicit val CompoundTypeTag: scala.reflect.ClassTag[Types.this.CompoundType] = ???
    implicit val ConstantTypeTag: scala.reflect.ClassTag[Types.this.ConstantType] = ???
    implicit val ExistentialTypeTag: scala.reflect.ClassTag[Types.this.ExistentialType] = ???
    implicit val MethodTypeTag: scala.reflect.ClassTag[Types.this.MethodType] = ???
    implicit val NullaryMethodTypeTag: scala.reflect.ClassTag[Types.this.NullaryMethodType] = ???
    implicit val PolyTypeTag: scala.reflect.ClassTag[Types.this.PolyType] = ???
    implicit val RefinedTypeTag: scala.reflect.ClassTag[Types.this.RefinedType] = ???
    implicit val SingletonTypeTag: scala.reflect.ClassTag[Types.this.SingletonType] = ???
    implicit val SingleTypeTag: scala.reflect.ClassTag[Types.this.SingleType] = ???
    implicit val SuperTypeTag: scala.reflect.ClassTag[Types.this.SuperType] = ???
    implicit val ThisTypeTag: scala.reflect.ClassTag[Types.this.ThisType] = ???
    implicit val TypeBoundsTag: scala.reflect.ClassTag[Types.this.TypeBounds] = ???
    implicit val TypeRefTag: scala.reflect.ClassTag[Types.this.TypeRef] = ???
    implicit val TypeTagg: scala.reflect.ClassTag[Types.this.Type] = ???
  }

  trait TypesStats { self: scala.reflect.internal.TypesStats with scala.reflect.internal.BaseTypeSeqsStats with scala.reflect.internal.util.Statistics =>

  }

  trait Variances { self: scala.reflect.internal.SymbolTable =>

  }

}
package scala.reflect.internal.pickling {
  trait Translations { self: scala.reflect.internal.SymbolTable =>

  }

}
package scala.reflect.internal.settings {
  trait AbsSettings {
    trait AbsSettingValue {
      type T
    }
  }

  abstract class MutableSettings() extends scala.reflect.internal.settings.AbsSettings {
    trait SettingValue extends MutableSettings.this.AbsSettingValue {
      def isDefault: scala.Boolean = ???
      def value: SettingValue.this.T = ???
      def `value_=`(arg: SettingValue.this.T): scala.Unit = ???
    }
  }

}
package scala.reflect.internal.tpe {
  private[internal] trait CommonOwners { self: scala.reflect.internal.SymbolTable =>

  }

  trait FindMembers { self: scala.reflect.internal.SymbolTable =>

  }

  private[internal] trait GlbLubs { self: scala.reflect.internal.SymbolTable =>
    def lub(ts: scala.List[GlbLubs.this.Type]): GlbLubs.this.Type = ???
    def glb(ts: scala.List[GlbLubs.this.Type]): GlbLubs.this.Type = ???
  }

  trait TypeComparers { self: scala.reflect.internal.SymbolTable =>

  }

  private[internal] trait TypeConstraints { self: scala.reflect.internal.SymbolTable =>

  }

  private[internal] trait TypeMaps { self: scala.reflect.internal.SymbolTable =>

  }

  private[internal] trait TypeToStrings { self: scala.reflect.internal.SymbolTable =>

  }

}
package scala.reflect.internal.transform {
  trait Transforms { self: scala.reflect.internal.SymbolTable =>

  }

}
package scala.reflect.internal.util {
  case class CodeAction(title: scala.Predef.String, description: scala.Option[scala.Predef.String], edits: scala.List[scala.reflect.internal.util.TextEdit]) extends scala.Product with scala.Serializable

  trait Collections

  private[util] trait DeprecatedPosition { self: scala.reflect.internal.util.Position =>
    def toSingleLine: scala.reflect.internal.util.Position = ???
    def startOrPoint: scala.Int = ???
    def endOrPoint: scala.Int = ???
  }

  class FreshNameCreator(creatorPrefix: scala.Predef.String = ???)

  private[util] trait InternalPositionImpl { self: scala.reflect.internal.util.Position =>
    def isTransparent: scala.Boolean = ???
    def isOpaqueRange: scala.Boolean = ???
    def pointOrElse(alt: scala.Int): scala.Int = ???
    def makeTransparent: scala.reflect.internal.util.Position = ???
    def withStart(start: scala.Int): scala.reflect.internal.util.Position = ???
    def withPoint(point: scala.Int): scala.reflect.internal.util.Position = ???
    def withEnd(end: scala.Int): scala.reflect.internal.util.Position = ???
    def focusStart: scala.reflect.internal.util.Position = ???
    def focus: scala.reflect.internal.util.Position = ???
    def focusEnd: scala.reflect.internal.util.Position = ???
    def union(pos: scala.reflect.internal.util.Position): scala.reflect.internal.util.Position = ???
    def includes(pos: scala.reflect.internal.util.Position): scala.Boolean = ???
    def properlyIncludes(pos: scala.reflect.internal.util.Position): scala.Boolean = ???
    def precedes(pos: scala.reflect.internal.util.Position): scala.Boolean = ???
    def properlyPrecedes(pos: scala.reflect.internal.util.Position): scala.Boolean = ???
    def sameRange(pos: scala.reflect.internal.util.Position): scala.Boolean = ???
    def overlaps(pos: scala.reflect.internal.util.Position): scala.Boolean = ???
    def line: scala.Int = ???
    def column: scala.Int = ???
    def lineContent: scala.Predef.String = ???
    def show: java.lang.String = ???
  }

  object NoFile extends scala.reflect.io.VirtualFile(???, ???)

  case object NoPosition extends scala.reflect.internal.util.UndefinedPosition() with scala.Product with scala.Serializable

  object NoSourceFile extends scala.reflect.internal.util.SourceFile() {
    def content: scala.Array[scala.Char] = ???
    def file: scala.reflect.internal.util.NoFile.type = ???
    def isLineBreak(idx: scala.Int): scala.Boolean = ???
    def isEndOfLine(idx: scala.Int): scala.Boolean = ???
    def isSelfContained: scala.Boolean = ???
    def length: scala.Int = ???
    def lineCount: scala.Int = ???
    def offsetToLine(offset: scala.Int): scala.Int = ???
    def lineToOffset(index: scala.Int): scala.Int = ???
    def lines(start: scala.Int = ???, end: scala.Int = ???): scala.collection.Iterator[scala.Nothing] = ???
  }

  class Position() extends scala.reflect.macros.EmptyAttachments() with scala.reflect.api.Position with scala.reflect.internal.util.InternalPositionImpl with scala.reflect.internal.util.DeprecatedPosition {
    type Pos = scala.reflect.internal.util.Position
    def pos: scala.reflect.internal.util.Position = ???
    def withPos(newPos: scala.reflect.internal.util.Position): scala.reflect.macros.Attachments{type Pos = Position.this.Pos} = ???
    def isDefined: scala.Boolean = ???
    def isRange: scala.Boolean = ???
    def source: scala.reflect.internal.util.SourceFile = ???
    def start: scala.Int = ???
    def point: scala.Int = ???
    def end: scala.Int = ???
  }

  abstract class SourceFile()

  abstract class Statistics(val symbolTable: scala.reflect.internal.SymbolTable, settings: scala.reflect.internal.settings.MutableSettings)

  trait StripMarginInterpolator

  case class TextEdit(position: scala.reflect.internal.util.Position, newText: scala.Predef.String) extends scala.Product with scala.Serializable

  sealed abstract class UndefinedPosition() extends scala.reflect.internal.util.Position() {
    override final def isDefined: scala.Boolean = ???
    override def isRange: scala.Boolean = ???
    override def source: scala.reflect.internal.util.NoSourceFile.type = ???
    override def start: scala.Nothing = ???
    override def point: scala.Nothing = ???
    override def end: scala.Nothing = ???
  }

  object `package` {
    class StringContextStripMarginOps(val stringContext: scala.StringContext) extends scala.reflect.internal.util.StripMarginInterpolator
  }

}
package scala.reflect.io {
  abstract class AbstractFile() extends scala.collection.AbstractIterable[scala.reflect.io.AbstractFile]() {
    def path: scala.Predef.String
    def output: java.io.OutputStream
  }

  object NoAbstractFile extends scala.reflect.io.AbstractFile() {
    def absolute: scala.reflect.io.AbstractFile = ???
    def container: scala.reflect.io.AbstractFile = ???
    def create(): scala.Unit = ???
    def delete(): scala.Unit = ???
    def file: java.io.File = ???
    def input: java.io.InputStream = ???
    def isDirectory: scala.Boolean = ???
    def iterator: scala.Iterator[scala.reflect.io.AbstractFile] = ???
    def lastModified: scala.Long = ???
    def lookupName(name: scala.Predef.String, directory: scala.Boolean): scala.reflect.io.AbstractFile = ???
    def lookupNameUnchecked(name: scala.Predef.String, directory: scala.Boolean): scala.reflect.io.AbstractFile = ???
    def name: scala.Predef.String = ???
    def output: java.io.OutputStream = ???
    def path: scala.Predef.String = ???
  }

  class VirtualFile(val name: scala.Predef.String, override val path: scala.Predef.String) extends scala.reflect.io.AbstractFile() {
    def absolute: scala.reflect.io.VirtualFile = ???
    def file: java.io.File = ???
    def input: java.io.InputStream = ???
    override def output: java.io.OutputStream = ???
    def container: scala.reflect.io.AbstractFile = ???
    def isDirectory: scala.Boolean = ???
    def lastModified: scala.Long = ???
    def iterator: scala.Iterator[scala.reflect.io.AbstractFile] = ???
    def create(): scala.Unit = ???
    def delete(): scala.Unit = ???
    def lookupName(name: scala.Predef.String, directory: scala.Boolean): scala.reflect.io.AbstractFile = ???
    def lookupNameUnchecked(name: scala.Predef.String, directory: scala.Boolean): scala.Nothing = ???
  }

}
package scala.reflect.macros {
  abstract class Attachments() {
    def all: scala.Predef.Set[scala.Any]
    def isEmpty: scala.Boolean
  }

  private[reflect] abstract class EmptyAttachments() extends scala.reflect.macros.Attachments() { self: scala.reflect.internal.util.Position =>
    override final def all: scala.Predef.Set[scala.Any] = ???
    override final def isEmpty: scala.Boolean = ???
  }

  abstract class Universe() extends scala.reflect.api.Universe() {
    trait MacroInternalApi extends Universe.this.InternalApi
    trait TreeGen
    trait MacroCompatApi extends Universe.this.CompatApi {
      class MacroCompatibleSymbol(symbol: Universe.this.Symbol)
      class MacroCompatibleTree(tree: Universe.this.Tree)
      class CompatibleTypeTree(tt: Universe.this.TypeTree)
    }
    trait RunContextApi
    trait CompilationUnitContextApi
  }

}
package scala.reflect.macros.runtime {
  trait JavaReflectionRuntimes { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait MacroRuntimes extends scala.reflect.macros.runtime.JavaReflectionRuntimes { self: scala.tools.nsc.typechecker.Analyzer =>

  }

}
package scala.reflect.macros.util {
  trait Helpers { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait Traces

}
package scala.tools.nsc {
  trait CompilationUnits { self: scala.tools.nsc.Global =>
    class CompilationUnit(val source: scala.reflect.internal.util.SourceFile, freshNameCreator: scala.reflect.internal.util.FreshNameCreator) extends CompilationUnits.this.CompilationUnitContextApi {
      implicit val fresh: scala.reflect.internal.util.FreshNameCreator = ???
      var body: CompilationUnits.this.Tree = ???
    }
  }

  class Global(var currentSettings: scala.tools.nsc.Settings, reporter0: scala.tools.nsc.reporters.Reporter) extends scala.tools.nsc.symtab.SymbolTable() with java.io.Closeable with scala.tools.nsc.CompilationUnits with scala.tools.nsc.plugins.Plugins with scala.tools.nsc.PhaseAssembly with scala.tools.nsc.ast.Trees with scala.tools.nsc.ast.Printers with scala.tools.nsc.ast.DocComments with scala.tools.nsc.ast.Positions with scala.tools.nsc.Reporting with scala.tools.nsc.Parsing {
    type RuntimeClass = java.lang.Class[_ >: scala.Nothing <: scala.Any]
    implicit val RuntimeClassTag: scala.reflect.ClassTag[Global.this.RuntimeClass] = ???
    implicit val MirrorTag: scala.reflect.ClassTag[Global.this.Mirror] = ???
    lazy val rootMirror: Global.this.Mirror = ???
    override def settings: scala.tools.nsc.Settings = ???
    def reporter: scala.tools.nsc.reporters.FilteringReporter = ???
    def picklerPhase: scala.tools.nsc.Phase = ???
    def erasurePhase: scala.tools.nsc.Phase = ???
    trait GlobalStats extends Global.this.ReflectStats with scala.tools.nsc.typechecker.TypersStats with scala.tools.nsc.typechecker.ImplicitsStats with scala.tools.nsc.typechecker.MacrosStats with scala.tools.nsc.backend.jvm.BackendStats with scala.tools.nsc.transform.patmat.PatternMatchingStats { self: Global.this.GlobalStats with scala.reflect.internal.util.Statistics =>

    }
    object statistics extends scala.reflect.internal.util.Statistics(???, ???) with Global.this.GlobalStats
    final def log(msg: => scala.AnyRef): scala.Unit = ???
    override lazy val internal: Global.this.Internal = ???
    def mirrorThatLoaded(sym: Global.this.Symbol): Global.this.Mirror = ???
    def currentRun: Global.this.Run = ???
    def currentFreshNameCreator: scala.reflect.internal.util.FreshNameCreator = ???
    override def currentRunId: scala.Int = ???
    class Run() extends Global.this.RunContextApi with Global.this.RunReporting with Global.this.RunParsing {
      var currentUnit: Global.this.CompilationUnit = ???
      def units: scala.Iterator[Global.this.CompilationUnit] = ???
    }
    def close(): scala.Unit = ???
  }

  trait Parsing { self: scala.tools.nsc.Parsing with scala.reflect.internal.Positions with scala.tools.nsc.Reporting =>
    trait RunParsing
  }

  trait PhaseAssembly { self: scala.tools.nsc.Global =>

  }

  trait Reporting extends scala.reflect.internal.Reporting { self: scala.tools.nsc.Reporting with scala.tools.nsc.ast.Positions with scala.tools.nsc.CompilationUnits with scala.reflect.internal.Symbols =>
    def settings: scala.tools.nsc.Settings
    protected def PerRunReporting: Reporting.this.PerRunReporting = ???
    class PerRunReporting() extends Reporting.this.PerRunReportingBase() {
      override def deprecationWarning(pos: Reporting.this.Position, msg: scala.Predef.String, since: scala.Predef.String, site: scala.Predef.String, origin: scala.Predef.String, actions: scala.List[scala.reflect.internal.util.CodeAction] = ???): scala.Unit = ???
    }
  }
  object Reporting {
    sealed class WarningCategory()
  }

  class Settings(errorFn: scala.Function1[scala.Predef.String,scala.Unit], pathFactory: scala.tools.nsc.settings.PathFactory) extends scala.tools.nsc.settings.MutableSettings(???, ???)

  object `package` {
    type Mode = scala.reflect.internal.Mode
    type Phase = scala.reflect.internal.Phase
    implicit val `strip margin`: scala.Function1[scala.StringContext,scala.reflect.internal.util.StringContextStripMarginOps] = ???
  }

}
package scala.tools.nsc.ast {
  trait DocComments { self: scala.tools.nsc.Global =>

  }

  trait Positions extends scala.reflect.internal.Positions { self: scala.tools.nsc.Global =>

  }

  trait Printers extends scala.reflect.internal.Printers { self: scala.tools.nsc.Global =>
    final type AstTreePrinter = Printers.this.TreePrinter
    class TreePrinter(out: java.io.PrintWriter) extends Printers.this.InternalTreePrinter(???) {
      override def print(args: scala.Any*): scala.Unit = ???
    }
    override def newTreePrinter(writer: java.io.PrintWriter): Printers.this.AstTreePrinter = ???
  }

  trait TreeDSL

  abstract class TreeInfo() extends scala.reflect.internal.TreeInfo() with scala.reflect.internal.MacroAnnotionTreeInfo

  trait Trees extends scala.reflect.internal.Trees { self: scala.tools.nsc.Global =>
    object treeInfo extends scala.tools.nsc.ast.TreeInfo() {
      val global: Trees.this.type = ???
    }
    trait TreeCopier extends Trees.this.InternalTreeCopierOps
    implicit val TreeCopierTag: scala.reflect.ClassTag[Trees.this.TreeCopier] = ???
    def newStrictTreeCopier: Trees.this.TreeCopier = ???
    def newLazyTreeCopier: Trees.this.TreeCopier = ???
  }

}
package scala.tools.nsc.backend.jvm {
  trait BackendStats { self: scala.tools.nsc.backend.jvm.BackendStats with scala.reflect.internal.util.Statistics =>

  }

}
package scala.tools.nsc.plugins {
  trait Plugins { self: scala.tools.nsc.Global =>

  }

}
package scala.tools.nsc.reporters {
  abstract class FilteringReporter() extends scala.tools.nsc.reporters.Reporter()

  abstract class Reporter() extends scala.reflect.internal.Reporter()

}
package scala.tools.nsc.settings {
  trait AbsSettings extends scala.reflect.internal.settings.AbsSettings {
    type Setting <: AbsSettings.this.AbsSetting
    type ResultOfTryToSet
    trait AbsSetting extends scala.Ordered[AbsSettings.this.Setting] with AbsSettings.this.AbsSettingValue {
      protected[nsc] def tryToSetColon(args: scala.List[scala.Predef.String]): scala.Option[AbsSettings.this.ResultOfTryToSet]
      def compare(that: AbsSettings.this.Setting): scala.Int = ???
    }
  }

  class MutableSettings(val errorFn: scala.Function1[scala.Predef.String,scala.Unit], val pathFactory: scala.tools.nsc.settings.PathFactory) extends scala.reflect.internal.settings.MutableSettings() with scala.tools.nsc.settings.AbsSettings with scala.tools.nsc.settings.ScalaSettings {
    type ResultOfTryToSet = scala.List[scala.Predef.String]
    abstract class Setting(val name: scala.Predef.String, val helpDescription: scala.Predef.String) extends MutableSettings.this.AbsSetting with MutableSettings.this.SettingValue {
      def withHelpSyntax(s: scala.Predef.String): Setting.this.type = ???
      def withAbbreviation(s: scala.Predef.String): Setting.this.type = ???
      def withDeprecationMessage(msg: scala.Predef.String): Setting.this.type = ???
      def reset(): scala.Unit
    }
    class IntSetting(name: scala.Predef.String, descr: scala.Predef.String, val default: scala.Int, val range: scala.Option[scala.Tuple2[scala.Int,scala.Int]], parser: scala.Function1[scala.Predef.String,scala.Option[scala.Int]]) extends MutableSettings.this.Setting(???, ???) {
      type T = scala.Int
      protected var v: scala.Int = ???
      override def value: scala.Int = ???
      def tryToSet(args: scala.List[scala.Predef.String]): scala.Option[MutableSettings.this.ResultOfTryToSet] = ???
      def tryToSetColon(args: scala.List[scala.Predef.String]): scala.Option[MutableSettings.this.ResultOfTryToSet] = ???
      def unparse: scala.List[scala.Predef.String] = ???
      override def reset(): scala.Unit = ???
    }
    class BooleanSetting(name: scala.Predef.String, descr: scala.Predef.String, default: scala.Boolean) extends MutableSettings.this.Setting(???, ???) {
      type T = scala.Boolean
      protected var v: scala.Boolean = ???
      override def value: scala.Boolean = ???
      def tryToSet(args: scala.List[scala.Predef.String]): scala.Some[scala.List[scala.Predef.String]] = ???
      def unparse: scala.List[scala.Predef.String] = ???
      override def tryToSetColon(args: scala.List[scala.Predef.String]): scala.Option[MutableSettings.this.ResultOfTryToSet] = ???
      override def reset(): scala.Unit = ???
    }
    class StringSetting(name: scala.Predef.String, val arg: scala.Predef.String, descr: scala.Predef.String, val default: scala.Predef.String, helpText: scala.Option[scala.Predef.String]) extends MutableSettings.this.Setting(???, ???) {
      type T = scala.Predef.String
      protected var v: StringSetting.this.T = ???
      def tryToSet(args: scala.List[scala.Predef.String]): scala.Option[MutableSettings.this.ResultOfTryToSet] = ???
      def tryToSetColon(args: scala.List[scala.Predef.String]): scala.Option[MutableSettings.this.ResultOfTryToSet] = ???
      def unparse: scala.List[scala.Predef.String] = ???
      override def reset(): scala.Unit = ???
    }
    class PathSetting(name: scala.Predef.String, descr: scala.Predef.String, default: scala.Predef.String, prependPath: MutableSettings.this.StringSetting, appendPath: MutableSettings.this.StringSetting) extends MutableSettings.this.StringSetting(???, ???, ???, ???, ???) {
      override def isDefault: scala.Boolean = ???
      override def value: scala.Predef.String = ???
      override def reset(): scala.Unit = ???
    }
    class MultiStringSetting(name: scala.Predef.String, val arg: scala.Predef.String, descr: scala.Predef.String, default: scala.List[scala.Predef.String], helpText: scala.Option[scala.Predef.String], prepend: scala.Boolean) extends MutableSettings.this.Setting(???, ???) with scala.collection.mutable.Clearable {
      type T = scala.List[scala.Predef.String]
      protected var v: MultiStringSetting.this.T = ???
      def tryToSet(args: scala.List[scala.Predef.String]): scala.Some[scala.List[scala.Predef.String]] = ???
      def tryToSetColon(args: scala.List[scala.Predef.String]): scala.Some[scala.List[scala.Predef.String]] = ???
      def clear(): scala.Unit = ???
      def unparse: scala.List[scala.Predef.String] = ???
      override def reset(): scala.Unit = ???
    }
    protected class EnableSettings[T <: MutableSettings.this.BooleanSetting](val s: T)
    protected implicit def installEnableSettings[T <: MutableSettings.this.BooleanSetting](s: T): MutableSettings.this.EnableSettings[T] = ???
  }

  trait PathFactory

  trait ScalaSettings extends scala.tools.nsc.settings.StandardScalaSettings with scala.tools.nsc.settings.Warnings { self: scala.tools.nsc.settings.MutableSettings =>
    protected[scala] lazy val allSettings: scala.collection.mutable.LinkedHashMap[java.lang.String,ScalaSettings.this.Setting] = ???
    val classpath: ScalaSettings.this.PathSetting = ???
    val async: ScalaSettings.this.BooleanSetting = ???
    val developer: ScalaSettings.this.BooleanSetting = ???
    val XnoPatmatAnalysis: ScalaSettings.this.BooleanSetting = ???
    val breakCycles: ScalaSettings.this.BooleanSetting = ???
    val Yrecursion: ScalaSettings.this.IntSetting = ???
    val Yrangepos: ScalaSettings.this.BooleanSetting = ???
    val debug: ScalaSettings.this.BooleanSetting = ???
    val Yposdebug: ScalaSettings.this.BooleanSetting = ???
    val Xprintpos: ScalaSettings.this.BooleanSetting = ???
    val printtypes: ScalaSettings.this.BooleanSetting = ???
    val Yshowsymkinds: ScalaSettings.this.BooleanSetting = ???
    val Yshowsymowners: ScalaSettings.this.BooleanSetting = ???
    val YstatisticsEnabled: ScalaSettings.this.BooleanSetting = ???
    val YhotStatisticsEnabled: ScalaSettings.this.BooleanSetting = ???
    val optimise: ScalaSettings.this.BooleanSetting = ???
  }

  trait StandardScalaSettings { self: scala.tools.nsc.settings.MutableSettings =>
    val explaintypes: StandardScalaSettings.this.BooleanSetting = ???
    val uniqid: StandardScalaSettings.this.BooleanSetting = ???
    val verbose: StandardScalaSettings.this.BooleanSetting = ???
  }

  trait Warnings { self: scala.tools.nsc.settings.MutableSettings =>

  }

}
package scala.tools.nsc.symtab {
  abstract class SymbolTable() extends scala.reflect.internal.SymbolTable()

}
package scala.tools.nsc.transform.patmat {
  trait PatternMatchingStats { self: scala.tools.nsc.transform.patmat.PatternMatchingStats with scala.reflect.internal.util.Statistics =>

  }

}
package scala.tools.nsc.typechecker {
  trait Adaptations { self: scala.tools.nsc.typechecker.Analyzer =>
    trait Adaptation { self: Adaptations.this.Typer =>

    }
  }

  trait Analyzer extends scala.tools.nsc.typechecker.Contexts with scala.tools.nsc.typechecker.Namers with scala.tools.nsc.typechecker.Typers with scala.tools.nsc.typechecker.Infer with scala.tools.nsc.typechecker.Implicits with scala.tools.nsc.typechecker.EtaExpansion with scala.tools.nsc.typechecker.SyntheticMethods with scala.tools.nsc.typechecker.Unapplies with scala.tools.nsc.typechecker.Macros with scala.tools.nsc.typechecker.NamesDefaults with scala.tools.nsc.typechecker.TypeDiagnostics with scala.tools.nsc.typechecker.ContextErrors with scala.tools.nsc.typechecker.StdAttachments with scala.tools.nsc.typechecker.MacroAnnotationAttachments with scala.tools.nsc.typechecker.AnalyzerPlugins with scala.tools.nsc.typechecker.ImportTracking {
    val global: scala.tools.nsc.Global
  }

  trait AnalyzerPlugins { self: scala.tools.nsc.typechecker.Analyzer with scala.tools.nsc.typechecker.splain.SplainData =>

  }

  trait Checkable { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait ContextErrors extends scala.tools.nsc.typechecker.splain.SplainErrors { self: scala.tools.nsc.typechecker.Analyzer =>
    case class ContextWarning(pos: ContextErrors.this.global.Position, msg: scala.Predef.String, cat: scala.tools.nsc.Reporting.WarningCategory, sym: ContextErrors.this.global.Symbol, actions: scala.List[scala.reflect.internal.util.CodeAction]) extends scala.Product with scala.Serializable
    sealed abstract class AbsTypeError()
    trait TyperContextErrors { self: ContextErrors.this.Typer =>

    }
  }

  trait Contexts { self: scala.tools.nsc.typechecker.Analyzer with scala.tools.nsc.typechecker.ImportTracking =>
    class Context(val tree: Contexts.this.global.Tree, val owner: Contexts.this.global.Symbol, val scope: Contexts.this.global.Scope, val unit: Contexts.this.global.CompilationUnit, _outer: Contexts.this.Context, val depth: scala.Int, _reporter: Contexts.this.ContextReporter = ???)
    abstract class ContextReporter(_errorBuffer: scala.collection.mutable.LinkedHashSet[Contexts.this.AbsTypeError] = ???, _warningBuffer: scala.collection.mutable.LinkedHashSet[Contexts.this.ContextWarning] = ???)
  }

  trait EtaExpansion { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait Implicits extends scala.tools.nsc.typechecker.splain.SplainData { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait ImplicitsStats { self: scala.tools.nsc.typechecker.ImplicitsStats with scala.reflect.internal.TypesStats with scala.reflect.internal.util.Statistics =>

  }

  trait ImportTracking { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait Infer extends scala.tools.nsc.typechecker.Checkable { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait MacroAnnotationAttachments { self: scala.tools.nsc.typechecker.Analyzer =>
    class RichTree(tree: MacroAnnotationAttachments.this.global.Tree)
  }

  trait Macros extends scala.reflect.macros.runtime.MacroRuntimes with scala.reflect.macros.util.Traces with scala.reflect.macros.util.Helpers { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait MacrosStats { self: scala.tools.nsc.typechecker.MacrosStats with scala.reflect.internal.TypesStats with scala.reflect.internal.util.Statistics =>

  }

  trait MethodSynthesis { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait Namers extends scala.tools.nsc.typechecker.MethodSynthesis { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait NamesDefaults { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait PatternTypers { self: scala.tools.nsc.typechecker.Analyzer =>
    trait PatternTyper { self: PatternTypers.this.Typer =>

    }
  }

  trait StdAttachments { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait SyntheticMethods extends scala.tools.nsc.ast.TreeDSL { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait Tags { self: scala.tools.nsc.typechecker.Analyzer =>
    trait Tag { self: Tags.this.Typer =>

    }
  }

  trait TypeDiagnostics extends scala.tools.nsc.typechecker.splain.SplainDiagnostics { self: scala.tools.nsc.typechecker.Analyzer with scala.tools.nsc.typechecker.StdAttachments =>
    trait TyperDiagnostics { self: TypeDiagnostics.this.Typer =>

    }
  }

  trait Typers extends scala.tools.nsc.typechecker.Adaptations with scala.tools.nsc.typechecker.Tags with scala.tools.nsc.typechecker.TypersTracking with scala.tools.nsc.typechecker.PatternTypers { self: scala.tools.nsc.typechecker.Analyzer =>
    global.definitions.AnyTpe: global.Type
    final def forArgMode(fun: Typers.this.global.Tree, mode: scala.tools.nsc.Mode): scala.reflect.internal.Mode = ???
    final val shortenImports: false = ???
    private val rightAssocValDefs: scala.collection.mutable.HashMap[Typers.this.global.Symbol,Typers.this.global.Tree] = ???
    private val inlinedRightAssocValDefs: scala.collection.mutable.HashSet[Typers.this.global.Symbol] = ???
    private val superConstructorCalls: scala.collection.mutable.HashMap[Typers.this.global.Symbol,scala.collection.Map[Typers.this.global.Symbol,Typers.this.global.Symbol]] = ???
    def resetDocComments(): scala.Unit = ???
    def resetTyper(): scala.Unit = ???
    sealed abstract class SilentResult[+T]() {
      def isEmpty: scala.Boolean
    }
    class SilentTypeError(val errors: scala.List[Typers.this.AbsTypeError], val warnings: scala.List[Typers.this.ContextWarning]) extends Typers.this.SilentResult[scala.Nothing]() {
      override def isEmpty: scala.Boolean = ???
    }
    object SilentTypeError
    case class SilentResultValue[+T](value: T) extends Typers.this.SilentResult[T]() with scala.Product with scala.Serializable {
      override def isEmpty: scala.Boolean = ???
    }
    def newTyper(context: Typers.this.Context): Typers.this.Typer = ???
    private class NormalTyper(context: Typers.this.Context) extends Typers.this.Typer(???)
    private[typechecker] final val SYNTHETIC_PRIVATE: 274877906944L = ???
    private final val InterpolatorCodeRegex: scala.util.matching.Regex = ???
    private final val InterpolatorIdentRegex: scala.util.matching.Regex = ???
    private final val typerFreshNameCreators: scala.collection.mutable.AnyRefMap[Typers.this.global.Symbol,scala.reflect.internal.util.FreshNameCreator] = ???
    def freshNameCreatorFor(context: Typers.this.Context): scala.reflect.internal.util.FreshNameCreator = ???
    abstract class Typer(context0: Typers.this.Context) extends Typers.this.TyperDiagnostics with Typers.this.Adaptation with Typers.this.Tag with Typers.this.PatternTyper with Typers.this.TyperContextErrors {
      implicit def fresh: scala.reflect.internal.util.FreshNameCreator = ???
    }
    final def finishComputeParamAlias(): scala.Unit = ???
  }

  trait TypersStats { self: scala.tools.nsc.typechecker.TypersStats with scala.reflect.internal.TypesStats with scala.reflect.internal.util.Statistics =>

  }

  trait TypersTracking { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait Unapplies extends scala.tools.nsc.ast.TreeDSL { self: scala.tools.nsc.typechecker.Analyzer =>

  }

}
package scala.tools.nsc.typechecker.splain {
  sealed trait FormattedName

  case class SimpleName(name: scala.Predef.String) extends scala.tools.nsc.typechecker.splain.FormattedName with scala.Product with scala.Serializable

  trait SplainData { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait SplainDiagnostics extends scala.tools.nsc.typechecker.splain.SplainFormatting { self: scala.tools.nsc.typechecker.Analyzer =>

  }

  trait SplainErrors { self: scala.tools.nsc.typechecker.Analyzer with scala.tools.nsc.typechecker.splain.SplainFormatting =>

  }

  trait SplainFormatters { self: scala.tools.nsc.typechecker.Analyzer =>
    implicit def asSimpleName(s: scala.Predef.String): scala.tools.nsc.typechecker.splain.SimpleName = ???
  }

  trait SplainFormatting extends scala.tools.nsc.typechecker.splain.SplainFormatters { self: scala.tools.nsc.typechecker.Analyzer =>

  }

}
