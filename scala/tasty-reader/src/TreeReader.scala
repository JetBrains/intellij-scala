package org.jetbrains.plugins.scala.tasty.reader

import dotty.tools.tasty.TastyBuffer.{Addr, NameRef}
import dotty.tools.tasty.TastyFormat.*
import dotty.tools.tasty.{TastyReader, UnpickleException}

/**
 * This class contains logic to read binary content of a Tasty file into internal representation ([[Node]])<br>
 * The entry point is [[treeFrom]] method.
 *
 * @see [[TreePrinter]] ~ printing Tasty content as Scala source code
 */
private class TreeReader(
  nameAtRef: NameTable,
  positions: Map[Addr, Int]
) {
  private def readNat(in: TastyReader): Int = in.readNat()

  private def nameToString(name: Name): String = name.toString

  private def nameRefToString(ref: NameRef): String = nameToString(nameAtRef(ref))

  private def readName(in: TastyReader): String = {
    val idx = in.readNat()
    nameRefToString(NameRef(idx))
  }

  private def readTree(in: TastyReader): Node = {
    val addr = in.currentAddr

    val tag = in.readByte()

    var nat = -1

    var value = -1L

    var names = Seq.empty[String]
    var children: () => Seq[Node] = () => Seq.empty

    if (tag >= firstLengthTreeTag) {
      val len = in.readNat()
      val end = in.currentAddr + len

      def treeReader(): () => Seq[Node] = {
        val addr = in.currentAddr
        () => {
          in.goto(addr)
          val trees = in.until(end)(readTree(in))
          trees.zip(trees.drop(1)).foreach { case (a, b) =>
            a.nextSibling = Some(b)
            b.prevSibling = Some(a)
          }
          trees
        }
      }

      tag match {
        case RENAMED =>
//          names :+= readName(in); names :+= readName(in)
        case VALDEF | DEFDEF | TYPEDEF | TYPEPARAM | PARAM | BIND =>
          names :+= readName(in); children = treeReader()
        case REFINEDtype | TERMREFin | TYPEREFin | SELECTin =>
          names :+= readName(in); children = treeReader()
        case RETURN | HOLE =>
//          readNat(in); children :++= readTrees()
        case /*METHODtype | POLYtype |*/ TYPELAMBDAtype =>
          children = { val tree = readTree(in); () => Seq(tree) }
//          children :+= readTree(in)
//          while (in.currentAddr.index < end.index && !isModifierTag(in.nextByte)) { children :+= readTree(in); names :+= readName(in); }
//          children :++= readTrees()
        case PARAMtype =>
//          readNat(in); readNat(in)
        case _ =>
          children = treeReader()
      }
      in.goto(end)
    }
    else if (tag >= firstNatASTTreeTag) {
      tag match {
        case IDENT | IDENTtpt | SELECT | SELECTtpt | TERMREF | TYPEREF | SELFDEF | NAMEDARG =>
          names :+= readName(in)
        case _ =>
          nat = readNat(in)
      }
      children = { val tree = readTree(in); () => Seq(tree) }
    }
    else if (tag >= firstASTTreeTag)
      children = { val tree = readTree(in); () => Seq(tree) }
    else if (tag >= firstNatTreeTag)
      tag match {
        case TERMREFpkg | TYPEREFpkg | STRINGconst | IMPORTED =>
          names :+= readName(in)
        case CHARconst =>
          value = in.readNat()
        case BYTEconst | SHORTconst | INTconst | FLOATconst =>
          value = in.readInt()
        case LONGconst | DOUBLEconst =>
          value = in.readLongInt()
        case _ =>
          nat = readNat(in)
      }

    val result = tag match {
      case SHAREDtype =>
        val node = readTree(in.subReader(Addr(nat), in.endAddr)) // Cache?
        node.isSharedType = true
        node
      case SHAREDterm => readTree(in.subReader(Addr(nat), in.endAddr)) // Cache?
      case INLINED => children().head
      case _ =>
        val node = new Node(addr, tag, names, children, () => positions.get(addr))
        tag match {
          case TYPEREFsymbol | TYPEREFdirect | TERMREFsymbol | TERMREFdirect =>
            val in0 = in.subReader(Addr(nat), in.endAddr)
            node.refTag = Some(in0.readByte())
            val length = in0.readNat()
            node.refName = Some(readName(in0)) // Use as node name?
            in0.goto(Addr(nat + length))
            node.refPrivate = in0.readByte() == PRIVATE;
          case _ =>
        }
        node.value = value
        node
    }
    result
  }
}

object TreeReader {

  def treeFrom(bytes: Array[Byte]): Node = {
    read(bytes)._1
  }

  def treeAndNameTableFrom(bytes: Array[Byte]): (Node, NameTable) = {
    val (tree, nameTable, _) = read(bytes)
    (tree, nameTable)
  }

  /**
   * Returns the AST and the raw source path from `SOURCEFILEattr`, when present. The path is not
   * reduced to a filename here. Older TASTy files may have no such attribute; the printer can
   * still obtain their filename from an `@SourceFile` annotation in the tree.
   */
  def treeAndSourceFileFrom(bytes: Array[Byte]): (Node, Option[String]) = {
    val (tree, _, sourceFile) = read(bytes)
    (tree, sourceFile)
  }

  private def read(bytes: Array[Byte]): (Node, NameTable, Option[String]) = {
    val in = new TastyReader(bytes)

    val header = new HeaderReader(in).readFullHeader()

    val nameTableReader = new NameTableReader(in)
    nameTableReader.read()
    val nameTable = nameTableReader.nameAtRef

    if (nameTable.apply(NameRef(in.readNat())).asSimpleName.toString != "ASTs") throw new UnpickleException("No ASTs section")
    val astSectionEnd = in.readEnd()
    val astSectionBegin = in.currentAddr

    val (positions, nextSectionStart) = if (header.minorVersion < 6) (Map.empty[Addr, Int], astSectionEnd) else {
      in.goto(astSectionEnd)

      if (nameTable.apply(NameRef(in.readNat())).asSimpleName.toString != "Positions") throw new UnpickleException("No Positions section")
      val positionSectionEnd = in.readEnd()
      val positionSectionBegin = in.currentAddr

      (readPositions(in.subReader(positionSectionBegin, positionSectionEnd)), positionSectionEnd)
    }

    in.goto(nextSectionStart)
    val sourceFile = readSourceFileFromSections(in, nameTable)

    val treeReader = new TreeReader(nameTable, positions)
    val tastyReader = new TastyReader(bytes, astSectionBegin.index, astSectionEnd.index, astSectionBegin.index)
    val rootNode = treeReader.readTree(tastyReader)
    (rootNode, nameTable, sourceFile)
  }

  /**
   * Finds the source path recorded as `SOURCEFILEattr` in a TASTy Attributes section.
   * The reader starts after the ASTs (and Positions, when present) and advances through the remaining sections.
   * Unknown attribute payloads cannot be skipped individually, so scanning stops for that Attributes section;
   * any source path found before the unknown attribute is retained.
   *
   * @param in the reader positioned at the first remaining section
   * @param nameTable the decoded names used by section headers and string attributes
   * @return the recorded source path, if one was found
   */
  private def readSourceFileFromSections(in: TastyReader, nameTable: NameTable): Option[String] = {
    var sourceFile = Option.empty[String]
    while (!in.isAtEnd) {
      val sectionName = nameTable(in.readNameRef()).asSimpleName.toString
      val sectionEnd = in.readEnd()
      if (sectionName == "Attributes") {
        val attributes = in.subReader(in.currentAddr, sectionEnd)
        while (!attributes.isAtEnd) {
          val tag = attributes.readByte()
          if (isStringAttrTag(tag)) {
            val value = nameTable(attributes.readNameRef()).toString
            if (tag == SOURCEFILEattr) sourceFile = Some(value)
          } else if (!isBooleanAttrTag(tag)) {
            attributes.goto(sectionEnd)
          }
        }
      }
      in.goto(sectionEnd)
    }
    sourceFile
  }

  private def readPositions(in: TastyReader): Map[Addr, Int] = {
    var result = Map[Addr, Int]()

    val lines = in.readNat()
    var i = 0; while (i < lines) { in.readNat(); i += 1 }

    var curIndex = 0
    var curStart = 0
    while (!in.isAtEnd) {
      val header = in.readInt()
      if (header == SOURCE) {
        in.readNameRef()
      } else {
        val addrDelta = header >> 3
        val hasStart = (header & 4) != 0
        val hasEnd = (header & 2) != 0
        val hasPoint = (header & 1) != 0
        curIndex += addrDelta
        if (hasStart) curStart += in.readInt()
        if (hasEnd) in.readInt()
        result += (Addr(curIndex), curStart)
        if (hasPoint) in.readInt()
      }
    }

    result
  }
}
