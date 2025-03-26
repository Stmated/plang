package org.inf.mir;

import org.inf.mir.model.MirNode;
import jakarta.annotation.Nonnull;

/**
 * A lowering can result in multiple nodes, where each node in essence is a function.
 * <p>
 * TODO: Need a way of saying that a node is capturing context of another node.
 *        So we can know that it should be backed by a struct of captured data or whatever.
 *
 * @param nodes
 */
public record MirLoweringResult(

  @Nonnull
  MirNode initNode
) {

}
