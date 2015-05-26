package org.threadly.load;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.threadly.load.AbstractScriptFactoryInitializer;
import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.load.ExecutableScript.ExecutionItem.ChildItems;
import org.threadly.load.gui.Node;

/**
 * <p>Class which builds a graph of {@link Node}'s based off a script produced by a 
 * {@link ScriptFactory}.  This builds a script and then traverses the executable items to produce 
 * a graph of {@link Node} objects.</p>
 * 
 * @author jent - Mike Jensen
 */
public class ScriptGraphBuilder extends AbstractScriptFactoryInitializer {
  public static Node buildGraph(String[] args) {
    return new ScriptGraphBuilder(args).makeGraph();
  }
  
  public static Node makeGraph(Collection<ExecutionItem> steps) {
    Node head = new Node("start");
    Node current = head;
    Iterator<ExecutionItem> it = steps.iterator();
    boolean chainOfOne = steps.size() == 1;
    while (it.hasNext()) {
      current = expandNode(head, it.next(), chainOfOne, new AtomicInteger());
    }
    
    if (current.isBranchOrMergeNode()) {
      current.deleteFromGraph();
    }
    return head;
  }
  
  private static Node expandNode(Node previousNode, ExecutionItem item, 
                                 boolean onlyItemOnChain, AtomicInteger chainLength) {
    ChildItems childItems = item.getChildItems();
    if (! childItems.hasChildren()) {
      Node result = new Node(item.toString());
      previousNode = previousNode.replaceWithParentIfMergeWithOneParent();
      previousNode.addChildNode(result);
      chainLength.incrementAndGet();
      return result;
    } else {
      int maxLength = -1;
      List<Node> childNodes = new LinkedList<Node>();
      Node longestNode = previousNode;
      Iterator<ExecutionItem> it = childItems.iterator();
      if (! childItems.itemsRunSequential() && ! previousNode.isBranchOrMergeNode()) {
        Node branchPoint = new Node();
        previousNode.addChildNode(branchPoint);
        previousNode = branchPoint;
      }
      Boolean chainOfOne = null;
      while (it.hasNext()) {
        if (chainOfOne == null) {
          chainOfOne = ! it.hasNext();
        }
        ExecutionItem childItem = it.next();
        AtomicInteger length = new AtomicInteger();
        Node endNode = expandNode(previousNode, childItem, chainOfOne, length);
        if (childItems.itemsRunSequential()) {
          previousNode = endNode;
        }
        if (length.get() >= maxLength) {
          maxLength = length.get();
          longestNode = endNode;
        }
        childNodes.add(endNode);
      }
      if (childItems.itemsRunSequential() || childNodes.size() == 1) {
        return longestNode;
      } else {
        Node joinPoint = new Node();
        for(Node n : childNodes) {
          n.addChildNode(joinPoint);
        }
        return joinPoint;
      }
    }
  }

  public ScriptGraphBuilder(String[] args) {
    super(args);
  }
  
  public Node makeGraph() {
    return ScriptGraphBuilder.makeGraph(script.steps);
  }
}
