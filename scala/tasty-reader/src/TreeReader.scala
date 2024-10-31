package org.jetbrains.plugins.scala.tasty.reader

import dotty.tools.tasty.TastyBuffer.{Addr, NameRef}
import dotty.tools.tasty.TastyFormat.*
import dotty.tools.tasty.{TastyReader, UnpickleException}

private class TreeReader(nameAtRef: NameTable, positions: Map[Addr, Int]) {
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
        case IDENT | IDENTtpt | SELECT | SELECTtpt | TERMREF | TYPEREF | SELFDEF | NAMEDARG => names :+= readName(in)
        case _ => nat = readNat(in)
      }
      children = { val tree = readTree(in); () => Seq(tree) }
    }
    else if (tag >= firstASTTreeTag)
      children = { val tree = readTree(in); () => Seq(tree) }
    else if (tag >= firstNatTreeTag)
      tag match {
        case TERMREFpkg | TYPEREFpkg | STRINGconst | IMPORTED => names :+= readName(in)
        case CHARconst => value = in.readNat()
        case BYTEconst | SHORTconst | INTconst | FLOATconst => value = in.readInt()
        case LONGconst | DOUBLEconst => value = in.readLongInt()
        case _ => nat = readNat(in)
      }

    tag match {
      case SHAREDtype =>
        val node = readTree(in.subReader(Addr(nat), in.endAddr)) // TODO cache?
        node.isSharedType = true
        node
      case SHAREDterm => readTree(in.subReader(Addr(nat), in.endAddr)) // TODO cache?
      case INLINED => children().head
      case _ =>
        val node = new Node(addr, tag, names, children, () => positions.get(addr))
        tag match {
          case TYPEREFsymbol | TYPEREFdirect | TERMREFsymbol | TERMREFdirect =>
            val in0 = in.subReader(Addr(nat), in.endAddr)
            node.refTag = Some(in0.readByte())
            val length = in0.readNat()
            node.refName = Some(readName(in0)) // TODO use as node name?
            in0.goto(Addr(nat + length))
            node.refPrivate = in0.readByte() == PRIVATE;
          case _ =>
        }
        node.value = value
        node
    }
  }
}

object TreeReader {
  def treeFrom(bytes: Array[Byte]): Node = {
    val in = new TastyReader(bytes)

    val header = new HeaderReader(in).readFullHeader()

    val nameTableReader = new NameTableReader(in)
    nameTableReader.read()
    val nameTable = nameTableReader.nameAtRef

    if (nameTable.apply(NameRef(in.readNat())).asSimpleName.toString != "ASTs") throw new UnpickleException("No ASTs section")
    val astSectionEnd = in.readEnd()
    val astSectionBegin = in.currentAddr

    val positions = if (header.minorVersion < 6) Map.empty[Addr, Int] else {
      in.goto(astSectionEnd)

      if (nameTable.apply(NameRef(in.readNat())).asSimpleName.toString != "Positions") throw new UnpickleException("No Positions section")
      val positionSectionEnd = in.readEnd()
      val positionSectionBegin = in.currentAddr

      readPositions(in.subReader(positionSectionBegin, positionSectionEnd))
    }

    new TreeReader(nameTable, positions).readTree(new TastyReader(bytes, astSectionBegin.index, astSectionEnd.index, astSectionBegin.index))
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
