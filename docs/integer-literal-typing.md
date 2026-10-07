# Contextual integer literals

An unsuffixed integer literal takes its expected integer type when its value fits. This permits small constants without allowing narrowing conversions of arbitrary integer expressions.

```inf
val n: uint8 = 255;
val read = (value: uint8) => value;
read(255);
val make = (): uint8 => 255;
val values = [0x99, 255; uint8;2];
val t: (uint8,) = (255,);
```

`256` and `-1` cannot satisfy `uint8`.
Explicit suffixes such as `1i32`, `1u8`, and `1L` retain their source types and use the ordinary conversion rules. 
Without an expected integer type, literals retain their default types.
