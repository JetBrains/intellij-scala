package org.jetbrains.plugins.scala.text.scala2

import org.jetbrains.plugins.scala.text.TextToTextTestBase

class ScalaReflectTest extends TextToTextTestBase(
  Seq.empty,
  Seq("scala.reflect"), Set.empty, 214,
  Set(
    "scala.reflect.api.TypeTags", // TypeTags.this. vs Universe.this
    "scala.reflect.internal.Definitions", // type NameTypeDefinitions.this.TermName in type refinement
    "scala.reflect.internal.Kinds", // No this. prefix for object
    "scala.reflect.internal.StdNames", // No this. prefix for object
    "scala.reflect.internal.Symbols", // Symbols.this. vs SymbolTable.this.
    "scala.reflect.internal.Types", // Typs.this. vs SymbolTable.this.
    "scala.reflect.internal.tpe.CommonOwners", // CommonOwners.this. vs SymbolTable.this.
    "scala.reflect.internal.tpe.FindMembers", // Cannot resolve reference
    "scala.reflect.internal.tpe.TypeMaps", // TypeMaps.this. vs SymbolTable.this.
    "scala.reflect.internal.transform.Transforms", // $1, _1, cannot resolve reference
    "scala.reflect.runtime.ReflectionUtils", // Existential type
  ),
  includeScalaReflect = true
)