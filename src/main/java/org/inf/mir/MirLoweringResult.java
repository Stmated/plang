package org.inf.mir;

import jakarta.annotation.Nonnull;
import org.inf.mir.model.MirNode;

/**
 * A lowering can result in multiple nodes, where each node in essence is a function.
 * <p>
 * TODO: Need a way of saying that a node is capturing context of another node.
 *        So we can know that it should be backed by a struct of captured data or whatever.
 */
public record MirLoweringResult(

  @Nonnull
  MirNode initNode
) {

}
