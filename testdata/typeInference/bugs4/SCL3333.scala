/*start*/for (first <- 1 to 44;
     second <- first + 1 to 45;
     third <- second + 1 to 46;
     fourth <- third + 1 to 47;
     fifth <- fourth + 1 to 48;
     sixth <- fifth + 1 to 49) yield {
  (first, second, third, fourth, fifth, sixth)
}/*end*/
//IndexedSeq[(Int, Int, Int, Int, Int, Int)]