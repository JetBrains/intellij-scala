package types

trait Compound {
  type T1 = Int & Long

  type T2 = Int & (Long & Float)
}