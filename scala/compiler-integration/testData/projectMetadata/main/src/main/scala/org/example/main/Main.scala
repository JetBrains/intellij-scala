package org.example.main

import org.example.Greeter
import org.example.impl.HelloWorldGreeter

object Main:
  def main(args: Array[String]): Unit =
    val greeter: Greeter = HelloWorldGreeter
    println(greeter.greeting)
