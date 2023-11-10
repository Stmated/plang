package com.github.stmated.plang.mir;

import com.github.stmated.plang.mir.model.MirInstr;
import java.util.Collection;

public record MirTyInvestigationContext(Collection<MirInstr> visited) {

}
