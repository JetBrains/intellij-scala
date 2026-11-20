package com

sealed trait Node

case class Leaf(value: Int) extends Node
