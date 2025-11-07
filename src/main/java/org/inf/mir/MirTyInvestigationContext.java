package org.inf.mir;

import org.inf.mir.Mir.Instr;

import java.util.Collection;

public record MirTyInvestigationContext(Collection<Instr> visited) {

}
