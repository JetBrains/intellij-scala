package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

enum CompilationKind:
  case JPSIncremental, BSPIncremental, Document, InMemoryDocument, Worksheet

  override def toString: String = this match
    case JPSIncremental => "JPS Incremental"
    case BSPIncremental => "BSP Incremental"
    case InMemoryDocument => "Document (In memory)"
    case Document => "Document"
    case Worksheet => "Worksheet"