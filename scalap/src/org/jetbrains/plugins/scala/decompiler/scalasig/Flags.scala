package org.jetbrains.plugins.scala.decompiler.scalasig

trait Flags {
  def hasFlag(flag : Long) : Boolean
  
  def isImplicit: Boolean = hasFlag(0x00000001)
  def isFinal: Boolean = hasFlag(0x00000002)
  def isPrivate: Boolean = hasFlag(0x00000004)
  def isProtected: Boolean = hasFlag(0x00000008)
    
  def isSealed: Boolean = hasFlag(0x00000010)
  def isOverride: Boolean = hasFlag(0x00000020)
  def isCase: Boolean = hasFlag(0x00000040)
  def isAbstract: Boolean = hasFlag(0x00000080)

  def isDeferred: Boolean = hasFlag(0x00000100)
  def isMethod: Boolean = hasFlag(0x00000200)
  def isModule: Boolean = hasFlag(0x00000400)
  def isInterface: Boolean = hasFlag(0x00000800)

  def isMutable: Boolean = hasFlag(0x00001000)
  def isParam: Boolean = hasFlag(0x00002000)
  def isPackage: Boolean = hasFlag(0x00004000)
  def isDeprecated: Boolean = hasFlag(0x00008000)

  def isCovariant: Boolean = hasFlag(0x00010000)
  def isCaptured: Boolean = hasFlag(0x00010000)

  def isByNameParam: Boolean = hasFlag(0x00010000)
  def isContravariant: Boolean = hasFlag(0x00020000)
  def isLabel: Boolean = hasFlag(0x00020000) // method symbol is a label. Set by TailCall
  def isInConstructor: Boolean = hasFlag(0x00020000) // class symbol is defined in this/superclass constructor

  def isAbstractOverride: Boolean = hasFlag(0x00040000)
  def isLocal: Boolean = hasFlag(0x00080000)

  def isJava: Boolean = hasFlag(0x00100000)
  def isSynthetic: Boolean = hasFlag(0x00200000)
  def isStable: Boolean = hasFlag(0x00400000)
  def isStatic: Boolean = hasFlag(0x00800000)

  def isCaseAccessor: Boolean = hasFlag(0x01000000)
  def isTrait: Boolean = hasFlag(0x02000000)
  def hasDefault: Boolean = hasFlag(0x02000000)
  def isBridge: Boolean = hasFlag(0x04000000)
  def isAccessor: Boolean = hasFlag(0x08000000)

  def isSuperAccessor: Boolean = hasFlag(0x10000000)
  def isParamAccessor: Boolean = hasFlag(0x20000000)

  def isModuleVar: Boolean = hasFlag(0x40000000) // for variables: is the variable caching a module value
  def isSyntheticMethod: Boolean = hasFlag(0x40000000) // for methods: synthetic method, but without SYNTHETIC flag
  def isMonomorphic: Boolean = hasFlag(0x40000000) // for type symbols: does not have type parameters
  def isLazy: Boolean = hasFlag(0x80000000L) // symbol is a lazy val. can't have MUTABLE unless transformed by typer

  def isError: Boolean = hasFlag(0x100000000L)
  def isOverloaded: Boolean = hasFlag(0x200000000L)
  def isLifted: Boolean = hasFlag(0x400000000L)

  def isMixedIn: Boolean = hasFlag(0x800000000L)
  def isExistential: Boolean = hasFlag(0x800000000L)

  def isExpandedName: Boolean = hasFlag(0x1000000000L)
  def isImplementationClass: Boolean = hasFlag(0x2000000000L)
  def isPreSuper: Boolean = hasFlag(0x2000000000L)

}
