package org.threadly.load.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.threadly.util.StringUtils;

/**
 * <p>Class which represents a node on the graph.</p>
 * 
 * @author jent - Mike Jensen
 */
public class Node {
  private static final String BRANCH_NAME = StringUtils.randomString(64);

  protected final List<Node> parentNodes;
  private final List<Node> childNodes;
  private final String name;
  
  /**
   * Constructs a new graph node with a specified identifier.  This is a node which is a branch or 
   * fork point.
   */
  public Node() {
    this(BRANCH_NAME);
  }
  
  /**
   * Constructs a new graph node with a specified identifier.
   * 
   * @param name Identifier for this node
   */
  public Node(String name) {
    this.name = name;
    childNodes = new ArrayList<Node>(2);
    parentNodes = new ArrayList<Node>(2);
  }
  
  /**
   * Gets the name this node was constructed with.
   * 
   * @return Name of this node
   */
  public String getName() {
    if (isBranchOrMergeNode()) {
      return "";
    } else {
      return name;
    }
  }
  
  public boolean isBranchOrMergeNode() {
    return BRANCH_NAME.equals(name);
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
      if (! node.parentNodes.contains(this)) {
        node.parentNodes.add(this);
      }
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

  public void deleteFromGraph() {
    for(Node n : parentNodes) {
      n.childNodes.remove(this);
    }
  }

  public void replace(Node node) {
    deleteFromGraph();
    for(Node n : parentNodes) {
      n.addChildNode(node);
    }
  }

  public Node replaceWithParentIfMergeWithOneParent() {
    if (isBranchOrMergeNode()) {
      if (parentNodes.size() == 1) {
        Node parentNode = parentNodes.get(0);
        parentNode.childNodes.remove(this);
        return parentNode;
      }
    }
    return this;
  }
}
