# Notes
- The low-level structure of tasty nodes that we construct from the tasty bytecode is different from the one 
  used in the compiler. For example:
  - We skip some nodes (e.g. RHS)
  - We handled "shared nodes" differently
  - etc...