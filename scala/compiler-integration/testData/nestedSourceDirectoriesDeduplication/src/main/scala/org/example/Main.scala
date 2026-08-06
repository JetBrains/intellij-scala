package org.example

object Main:
  def main(args: Array[String]): Unit =
    val person = Person("Name", 25)
    println(person)

    val pojoPerson = PojoPerson("OtherName", 26)
    println(pojoPerson.getName())
    println(pojoPerson.getAge())
  end main
