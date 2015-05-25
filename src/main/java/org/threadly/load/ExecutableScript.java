package org.threadly.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;

/**
 * <p>This class handles the execution of a completely generated execution script.</p>
 * 
 * @author jent - Mike Jensen
 */
public class ExecutableScript {
  protected final int neededThreadQty;
  protected final Collection<ExecutionItem> steps;
  protected final ScriptAssistant scriptAssistant;
  
  /**
   * Constructs a new {@link ExecutableScript}.  If the minimum threads needed don't match the 
   * execution graph provided, it may restrict load, or never complete.  
   * 
   * Execution will not proceed to the next step until the previous step has fully completed.
   * 
   * @param neededThreadQty Minimum number of threads to execute provided steps
   * @param steps Collection of steps which should be executed one after another
   */
  public ExecutableScript(int neededThreadQty, Collection<ExecutionItem> steps) {
    this.neededThreadQty = neededThreadQty;
    this.steps = steps;
    scriptAssistant = new ScriptAssistant();
  }
  
  /**
   * Returns how many threads will be started when the script is executed.
   * 
   * @return Number of threads needed to run script as provided
   */
  public int getThreadQtyNeeded() {
    return neededThreadQty;
  }
  
  /**
   * Creates a copy of the {@link ExecutionItem} chain.  This could be provided to 
   * {@link #ExecutableScript(int, Collection)} to produce another runnable script.
   *  
   * @return Copy of execution graph
   */
  public Collection<ExecutionItem> makeItemsCopy() {
    List<ExecutionItem> result = new ArrayList<ExecutionItem>(steps.size());
    Iterator<ExecutionItem> it = steps.iterator();
    while (it.hasNext()) {
      result.add(it.next().makeCopy());
    }
    return result;
  }
  
  /**
   * Starts the execution of the script.  It will traverse through the execution graph an execute 
   * things as previously defined by using the builder.  
   * 
   * This returns a collection of futures.  If an execution step was executed, the future will 
   * return a {@link StepResult}.  That {@link StepResult} will indicate either a successful or 
   * failure in execution.  If a failure does occur then future test steps will NOT be executed.  
   * If a step was never executed due to a failure, those futures will be resolved in an error 
   * (thus calls to {@link ListenableFuture#get()} will throw a 
   * {@link java.util.concurrent.ExecutionException}).  You can use 
   * {@link StepResultCollectionUtils#getFailedResult(Collection)} to see if any steps failed.  
   * This will block till all steps have completed (or a failed test step occurred).  If 
   * {@link StepResultCollectionUtils#getFailedResult(Collection)} returns null, then the test 
   * completed without error. 
   * 
   * @return A collection of futures which will represent each execution step
   */
  public List<ListenableFuture<StepResult>> startScript() {
    ArrayList<ListenableFuture<StepResult>> result = new ArrayList<ListenableFuture<StepResult>>();
    
    Iterator<ExecutionItem> it = steps.iterator();
    while (it.hasNext()) {
      result.addAll(it.next().getFutures());
    }
    
    result.trimToSize();
    
    scriptAssistant.start(neededThreadQty + 1, result);
    
    // perform a gc before starting execution so that we can run as smooth as possible
    System.gc();
    
    // TODO - move this to a regular class?
    scriptAssistant.executeAsyncIfStillRunning(new Runnable() {
      @Override
      public void run() {
        Iterator<ExecutionItem> it = steps.iterator();
        while (it.hasNext()) {
          ExecutionItem stepRunner = it.next();
          stepRunner.runChainItem(scriptAssistant);
          // this call will block till the step is done, thus preventing execution of the next step
          try {
            if (StepResultCollectionUtils.getFailedResult(stepRunner.getFutures()) != null) {
              FutureUtils.cancelIncompleteFutures(scriptAssistant.getRunningFutureSet(), true);
              return;
            }
          } catch (InterruptedException e) {
            // let thread exit
            return;
          }
        }
      }
    });
    
    return result;
  }
  
  /**
   * <p>Small class for managing access and needs from running script steps.</p>
   * 
   * @author jent - Mike Jensen
   */
  private static class ScriptAssistant implements ExecutionItem.ExecutionAssistant {
    private final AtomicBoolean running;
    private List<ListenableFuture<StepResult>> futures = null;
    private PriorityScheduler scheduler = null;
    
    public ScriptAssistant() {
      running = new AtomicBoolean(false);
    }
    
    public void start(int threadPoolSize, List<ListenableFuture<StepResult>> futures) {
      if (! running.compareAndSet(false, true)) {
        throw new IllegalStateException("Already running");
      }
      scheduler = new PriorityScheduler(threadPoolSize);
      scheduler.prestartAllThreads();
      this.futures = Collections.unmodifiableList(futures);
      
      /* with the way FutureUtils works, the ListenableFuture made here wont be able to be 
       * garbage collected, even though we don't have a reference to it.  Thus ensuring we 
       * cleanup our running references.
       */
      FutureUtils.makeCompleteFuture(futures).addListener(new Runnable() {
        @Override
        public void run() {
          scheduler = null;
          running.set(false);
        }
      });
    }

    @Override
    public List<ListenableFuture<StepResult>> getRunningFutureSet() {
      return futures;
    }
    
    @Override
    public void executeAsyncIfStillRunning(Runnable toRun) {
      PriorityScheduler scheduler = this.scheduler;
      if (scheduler != null) {
        scheduler.execute(toRun);
      }
    }
  }
  
  /**
   * <p>Interface for chain item, all items provided for execution must implement this interface.  
   * This will require test steps to be wrapped in a class which provides this functionality.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected interface ExecutionItem {
    /**
     * Run the current items execution.  This may execute async on the provided 
     * {@link ExecutionAssistant}, but returned futures from {@link #getFutures()} should not fully 
     * complete until the chain item completes.
     * 
     * @param script {@link ExecutionAssistant} which is performing the execution
     */
    public void runChainItem(ExecutionAssistant script);

    /**
     * Returns the collection of futures which represent this test.  There should be one future 
     * per test step.  These futures should complete as their respective test steps complete.
     * 
     * @return Collection of futures that provide results from their test steps
     */
    public Collection<? extends SettableListenableFuture<StepResult>> getFutures();
    
    /**
     * Produces a copy of the item so that it can be run in another execution chain.
     * 
     * @return A copy of the test item
     */
    public ExecutionItem makeCopy();
    
    // TODO - javadoc
    public ChildItems getChildItems();
    
    public interface ChildItems {
      public boolean itemsRunSequential();

      public boolean hasChildren();

      public Iterator<ExecutionItem> iterator();
    }
    
    /**
     * <p>Class passed to the test item at the start of execution.  This can provide information 
     * and facilities it can use to perform it's execution.</p>
     * 
     * @author jent - Mike Jensen
     */
    public interface ExecutionAssistant {
      /**
       * This farms off tasks on to another thread for execution.  This may not execute if the script 
       * has already stopped (likely from an error or failed step).  In those cases the task's future 
       * was already canceled so execution should not be needed.
       * 
       * @param toRun Task to be executed
       */
      public void executeAsyncIfStillRunning(Runnable toRun);
      
      /**
       * Returns the list of futures for the current test script run.  If not currently running this 
       * will be null.
       * 
       * @return List of futures that will complete for the current execution
       */
      public List<ListenableFuture<StepResult>> getRunningFutureSet();
    }
  }
}