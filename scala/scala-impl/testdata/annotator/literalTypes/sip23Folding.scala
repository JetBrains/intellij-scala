object Test {

  ({ val () = (); 0 } + 0): 0 // compiles

  ({ (); 0 } + 0): 0 // compiles

  ({ 0; 0 } + 0): 0 // compiles

  ({ val 0 = 0; 0 } + 0): 0 // compiles
}