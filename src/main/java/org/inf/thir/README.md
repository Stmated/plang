# THIR
The Typed High-Level Intermediate Representation, which is just the HIR with added types.
The model for the THIR is the same as the HIR, but with an added mapping structure between HIR entities and their types.
All expressions have a resulting type, so at the end of the HIR -> THIR raising, all the expressions should have their types resolved.
