package org.inf.llvm.lowering;

import java.util.HashMap;
import java.util.Map;

record LLVMScope(String name, Map<String, LLVMScopeValue> map) {

  LLVMScope(String name) {
    this(name, new HashMap<>());
  }

  LLVMScope(String name, Map<String, LLVMScopeValue> map) {
    this.name = name;
    this.map = map;
  }
}
