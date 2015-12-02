package org.threadly.load;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.load.ExecutableScript.ExecutionItem.ExecutionAssistant;
import org.threadly.load.ExecutableScript.ExecutionItem.StepStartHandler;
import org.threadly.util.Pair;

public class ScriptBuilderUtils {
  public ParallelScriptBuilder balanceBuilders(AbstractScriptBuilder ... builders) {
    if (builders.length == 0) {
      return new ParallelScriptBuilder();
    } else if (builders.length == 1) {
      if (builders[0] instanceof ParallelScriptBuilder) {
        return (ParallelScriptBuilder)builders[0];
      } else {
        ParallelScriptBuilder result = new ParallelScriptBuilder();
        result.addSteps(builders[0]);
        return result;
      }
    }

    int largestBuilderCount = -1;
    AbstractScriptBuilder largestBuilder = null;
    List<Pair<AbstractScriptBuilder, Integer>> flowControlledBuilders = 
        new ArrayList<Pair<AbstractScriptBuilder, Integer>>(builders.length - 1);
    for (AbstractScriptBuilder builder : builders) {
      int builderCount = 0;
      for (ExecutionItem item : builder.getStepAsExecutionItem().getChildItems()) {
        if (item.isChainExecutor()) {
          throw new UnsupportedOperationException("Can not provide a builder containing other builders");
        }
        builderCount++;
      }
      if (builderCount > largestBuilderCount) {
        largestBuilderCount = builderCount;
        largestBuilder = builder;
      } else {
        flowControlledBuilders.add(new Pair<AbstractScriptBuilder, Integer>(builder, builderCount));
      }
    }
    
    List<RunSignalAcceptor> signalAcceptors = new ArrayList<RunSignalAcceptor>(builders.length - 1);
    for (Pair<AbstractScriptBuilder, Integer> fcBuilder : flowControlledBuilders) {
      // integer division is necessary to ensure execution
      int signalsPerRun = largestBuilderCount / fcBuilder.getRight();
      if (signalsPerRun > 1) {
        RunSignalAcceptor signalAcceptor = new RunSignalAcceptor(signalsPerRun);
        signalAcceptors.add(signalAcceptor);
        fcBuilder.getLeft().setStartHandlerOnAllSteps(signalAcceptor);
      }
    }
    
    if (! signalAcceptors.isEmpty()) {
      RunSignalAcceptor[] signalAcceptorsArray = new RunSignalAcceptor[signalAcceptors.size()];
      signalAcceptorsArray = signalAcceptors.toArray(signalAcceptorsArray);
      RunSignalSender signalSender = new RunSignalSender(signalAcceptorsArray);
      largestBuilder.setStartHandlerOnAllSteps(signalSender);
    }
    
    ParallelScriptBuilder result = new ParallelScriptBuilder();
    for (AbstractScriptBuilder builder : builders) {
      result.addSteps(builder);
    }
    return result;
  }
  
  private static class RunSignalAcceptor implements StepStartHandler {
    private final int neededSignalCountPerStep;
    private final Semaphore runSemaphore;
    private final AtomicInteger signalCount;
    
    public RunSignalAcceptor(int neededSignalCountPerStep) {
      this.neededSignalCountPerStep = neededSignalCountPerStep;
      runSemaphore = new Semaphore(0, true);  // no permits till executions complete
      signalCount = new AtomicInteger(0);
    }
    
    public void handleRunSignal() {
      signalCount.incrementAndGet();
      int casCount;
      while ((casCount = signalCount.get()) >= neededSignalCountPerStep) {
        if (signalCount.compareAndSet(casCount, casCount - neededSignalCountPerStep)) {
          // allow execution
          runSemaphore.release();
        }
      }
    }

    @Override
    public void readyToRun(ExecutionItem step, ExecutionAssistant assistant) {
      try {
        runSemaphore.acquire();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }

      step.setStartHandler(null); // unset ourself so that execution can happen naturally
      step.itemReadyForExecution(assistant);
    }
  }
  
  private static class RunSignalSender implements StepStartHandler {
    private final RunSignalAcceptor[] signalAcceptors;
    
    public RunSignalSender(RunSignalAcceptor[] signalAcceptors) {
      this.signalAcceptors = signalAcceptors;
    }

    @Override
    public void readyToRun(ExecutionItem step, ExecutionAssistant assistant) {
      for (RunSignalAcceptor rsa : signalAcceptors) {
        rsa.handleRunSignal();
      }
      
      step.setStartHandler(null); // unset ourself so that execution can happen naturally
      step.itemReadyForExecution(assistant);
    }
  }
}
