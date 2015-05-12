package org.threadly.load.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>Class which represents a node on the graph.</p>
 * 
 * @author jent - Mike Jensen
 */
public class Node {
  private final String name;
  private final List<Node> childNodes;
  
  /**
   * Constructs a new graph node with a specified identifier.
   * 
   * @param name Identifier for this node
   */
  public Node(String name) {
    this.name = name;
    childNodes = new ArrayList<Node>(2);
  }
  
  /**
   * Gets the name this node was constructed with.
   * 
   * @return Name of this node
   */
  public String getName() {
    return name;
  }
  
  @Override
  public String toString() {
    return "node:" + name;
  }
  
  /**
   * Adds a node to be represented as a child node to this current instance.
   * 
   * @param node Node to be added as a child
   */
  public void addChildNode(Node node) {
    if (! childNodes.contains(node)) {
      childNodes.add(node);
    }
  }
  
  /**
   * A collection of child nodes attached to this node.
   * 
   * @return A collection of child node references
   */
  public List<Node> getChildNodes() {
    return Collections.unmodifiableList(childNodes);
  }
}
