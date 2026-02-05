package com.example.tests

trait A {
  val <TYPO descr="Typo: In word 'typpo'">typpo</TYPO>: Int
  def <TYPO descr="Typo: In word 'typpo'">typpo</TYPO>(<TYPO descr="Typo: In word 'typpo'">typpo</TYPO>: Int): Unit
}

class B extends A {
  // typos are ignored because of the `override` keyword
  override val typpo: Int = 0
  override def typpo(typpo: Int): Unit = { }
}

object ObjectWith<TYPO descr="Typo: In word 'Eror'">Eror</TYPO>

class ClassWith<TYPO descr="Typo: In word 'Eror'">Eror</TYPO>

object Test {
  val variableWith<TYPO descr="Typo: In word 'Eror'">Eror</TYPO> = "error"
  def <TYPO descr="Typo: In word 'eror'">eror</TYPO>Function(<TYPO descr="Typo: In word 'eror'">eror</TYPO>: Int): Unit = {}
}
