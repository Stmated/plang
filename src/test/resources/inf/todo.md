
* Investigate some way of upgrading "groups" of types where something similar changes for all of them.
  * For example that for one step/context of the code, some properties are not required, but in later code they are
  * There needs to be a way of making them compatible with each other, so that later stages are allowed for earlier stages
  * ie. the changes to the types need/should be more restrictive for the changed types -- but perhaps not needed to be declared? Checked at callsites?

* Make "var" and "val" optional, and make "val" default
* Create support for the comptime functions which can take an expression and refactor them

