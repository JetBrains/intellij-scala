package scala.meta.trees

import scala.meta.EnvironmentProvider

abstract class TreeConverter extends TreeAdapter
                    with TypeAdapter
                    with MemberAdapter
                    with Namer
                    with SymbolTable
                    with Attributes
                    with Utils
                    with EnvironmentProvider
                    with TreeConverterBuilder
