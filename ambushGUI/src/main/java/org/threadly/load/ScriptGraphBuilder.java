package org.threadly.load;

import java.util.Iterator;
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

  public ScriptGraphBuilder(String[] args) {
    super(args);
  }
  
  public Node makeGraph() {
    Node head = new Node("start");
    Node current = head;
    Iterator<ExecutionItem> it = script.steps.iterator();
    while (it.hasNext()) {
      current = expandNode(current, it.next(), new AtomicInteger());
    }
    return head;
  }
  
  private Node expandNode(Node previousNode, ExecutionItem item, AtomicInteger chainLength) {
    ChildItems childItems = item.getChildItems();
    if (! childItems.hasChildren()) {
      Node result = new Node(item.toString());
      previousNode.addChildNode(result);
      chainLength.incrementAndGet();
      return result;
    } else {
      int maxLength = -1;
      Node longestNode = null;
      Iterator<ExecutionItem> it = childItems.iterator();
      while (it.hasNext()) {
        ExecutionItem childItem = it.next();
        AtomicInteger length = new AtomicInteger();
        Node endNode = expandNode(previousNode, childItem, length);
        if (childItems.itemsRunSequential()) {
          previousNode = endNode;
        }
        if (length.get() >= maxLength) {
          maxLength = length.get();
          longestNode = endNode;
        }
      }
      return longestNode;
    }
  }
}
