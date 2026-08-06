package org.jetbrains.plugins.scala.decompiler.scalasig

import scala.reflect.internal.pickling.PickleFormat._

object TagGroups {
  def isSymbolTag(tag: Int): Boolean = firstSymTag <= tag && tag <= lastExtSymTag

  def isConstantTag(tag: Int): Boolean = tag >= LITERALunit && tag <= LITERALenum

  def isAnnotArgTag(tag: Int): Boolean = tag == TREE || isConstantTag(tag)

  def isConstAnnotArgTag(tag: Int): Boolean = isConstantTag(tag) || tag == TREE || tag == ANNOTINFO || tag == ANNOTATEDtree

  def isTypeTag(tag: Int): Boolean =
    tag >= NOtpe && tag <= IMPLICITMETHODtpe ||
      tag == ANNOTATEDtpe || tag == DEBRUIJNINDEXtpe ||
      tag == EXISTENTIALtpe || tag == SUPERtpe

  def isNameTag(tag: Int): Boolean = tag == TERMname || tag == TYPEname

  final val SUPERtpe2 = 52 //There is an inconsistency in PicklerFormat
}
