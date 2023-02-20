package types

trait And {
  type T1 = Int & Long

  type T2 = (Int & Long) & Float
}