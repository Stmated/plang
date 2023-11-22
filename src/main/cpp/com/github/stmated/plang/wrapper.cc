#include <stdio.h>

extern "C" {
  FILE* freopen_stdout(const char *filename, const char *mode){
     return freopen(filename, mode, stdout);
  }
}
