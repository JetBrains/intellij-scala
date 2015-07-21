package org.jetbrains.plugins.scala.meta.trees

import org.jetbrains.plugins.scala.meta.FileProvider

abstract class TreeConverter extends TreeAdapter
                    with TypeAdapter
                    with MemberAdapter
                    with Namer
                    with SymbolTable
                    with Attributes
                    with Utils
                    with FileProvider
