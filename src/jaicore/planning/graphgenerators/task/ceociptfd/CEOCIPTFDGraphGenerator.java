package jaicore.planning.graphgenerators.task.ceociptfd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jaicore.basic.PerformanceLogger;
import jaicore.logic.fol.structure.CNFFormula;
import jaicore.logic.fol.structure.ConstantParam;
import jaicore.logic.fol.structure.Literal;
import jaicore.logic.fol.structure.LiteralParam;
import jaicore.logic.fol.structure.Monom;
import jaicore.planning.graphgenerators.task.TaskPlannerUtil;
import jaicore.planning.graphgenerators.task.tfd.TFDNode;
import jaicore.planning.graphgenerators.task.tfd.TFDNodeUtil;
import jaicore.planning.graphgenerators.task.tfd.TFDRestProblem;
import jaicore.planning.model.ceoc.CEOCAction;
import jaicore.planning.model.ceoc.CEOCOperation;
import jaicore.planning.model.conditional.CEAction;
import jaicore.planning.model.conditional.CEOperation;
import jaicore.planning.model.core.Action;
import jaicore.planning.model.core.Operation;
import jaicore.planning.model.task.ceocipstn.CEOCIPSTNPlanningProblem;
import jaicore.planning.model.task.ceocipstn.OCIPMethod;
import jaicore.planning.model.task.stn.Method;
import jaicore.planning.model.task.stn.MethodInstance;
import jaicore.search.algorithms.parallel.parallelexploration.distributed.interfaces.SerializableGraphGenerator;
import jaicore.search.algorithms.parallel.parallelexploration.distributed.interfaces.SerializableRootGenerator;
import jaicore.search.structure.core.NodeExpansionDescription;
import jaicore.search.structure.core.NodeType;
import jaicore.search.structure.graphgenerator.NodeGoalTester;
import jaicore.search.structure.graphgenerator.RootGenerator;
import jaicore.search.structure.graphgenerator.SuccessorGenerator;

/**
 * Graph Generator for HTN planning where (i) operations have conditional effects, (ii) operations may create new objects, and (iii) method preconditions may contain evaluable predicates.
 * 
 * @author fmohr
 *
 */
@SuppressWarnings("serial")
public class CEOCIPTFDGraphGenerator implements SerializableGraphGenerator<TFDNode, String> {

	private static final Logger logger = LoggerFactory.getLogger(CEOCIPTFDGraphGenerator.class);
	private static final int checkpointDistance = 1;
	private final CEOCIPSTNPlanningProblem problem;
	private final CNFFormula knowledge;
	private final Map<String, Operation> primitiveTasks = new HashMap<>();
	private SerializableRootGenerator<TFDNode> rootGenerator;
	private final TaskPlannerUtil util = new TaskPlannerUtil();
	private final Map<String, EvaluablePredicate> evaluablePredicates;
	private final Map<TFDNode,TFDNode> parentMap = new HashMap<>();

	public CEOCIPTFDGraphGenerator(CEOCIPSTNPlanningProblem problem, Map<String, EvaluablePredicate> pEvaluablePredicates) {
		if (problem == null)
			throw new IllegalArgumentException("Planning problem must not be NULL");
		this.problem = problem;
		this.knowledge = problem.getKnowledge();
		for (Operation op : problem.getDomain().getOperations())
			primitiveTasks.put(op.getName(), op);
		this.rootGenerator = () -> new TFDNode(problem.getInit(), util.getTaskChainOfTotallyOrderedNetwork(problem.getNetwork()));
		this.evaluablePredicates = pEvaluablePredicates != null ? pEvaluablePredicates : new HashMap<>();
	}

	@Override
	public RootGenerator<TFDNode> getRootGenerator() {
		return rootGenerator;
	}

	@Override
	public SuccessorGenerator<TFDNode, String> getSuccessorGenerator() {
		return l -> {
			logger.info("Generating successors for {}", l);
			List<NodeExpansionDescription<TFDNode, String>> successors = new ArrayList<>();

			List<TFDNode> path = TFDNodeUtil.getPathOfNode(l, parentMap);
			TFDRestProblem rp = l.getProblem();
			if (rp == null)
				rp = TFDNodeUtil.getRestProblem(path);
			Monom state = rp.getState();
			List<Literal> currentlyRemainingTasks = rp.getRemainingTasks();
			Literal nextTaskTmp = currentlyRemainingTasks.get(0);
			if (nextTaskTmp == null)
				return successors;
			if (nextTaskTmp.getPropertyName() == null)
				throw new IllegalStateException("Invalid task " + nextTaskTmp + " without property name!");
			String nextTaskName = nextTaskTmp.getPropertyName().substring(nextTaskTmp.getPropertyName().indexOf("-") + 1, nextTaskTmp.getPropertyName().length());
			Literal nextTask = new Literal(nextTaskName, nextTaskTmp.getParameters());
			int depth = path.size();

			/* if the task is primitive */
			if (primitiveTasks.containsKey(nextTask.getPropertyName())) {

				logger.info("Computing successors for PRIMITIVE task {} in state {}", nextTask, state);
				for (Action applicableAction : util.getActionsForPrimitiveTaskThatAreApplicableInState(knowledge, primitiveTasks.get(nextTask.getPropertyName()), nextTask,
						state)) {
					logger.info("Adding successor for PRIMITIVE task {} in state {}: {}", nextTask, state, applicableAction.getEncoding());

					/* if the depth is % k == 0, then compute the rest problem explicitly */
					Monom updatedState = new Monom(state, false);
					TFDNodeUtil.updateState(updatedState, applicableAction);
					List<Literal> remainingTasks = new ArrayList<>(currentlyRemainingTasks);
					remainingTasks.remove(0);
					TFDNode node = null;
					if (depth % checkpointDistance == 0)
						node = new TFDNode(updatedState, remainingTasks, null, new CEOCAction((CEOCOperation) applicableAction.getOperation(), applicableAction.getGrounding()));
					else
						node = new TFDNode(new CEAction((CEOperation) applicableAction.getOperation(), applicableAction.getGrounding()), remainingTasks.isEmpty());
					parentMap.put(node, l);
					successors.add(new NodeExpansionDescription<>(l, node, "edge label", NodeType.OR));
				}
				if (successors.size() != new HashSet<>(successors).size()) {
					System.err.println("Doppelte Knoten im Nachfolger!");
					System.exit(1);
				}
				logger.info("Computed {} successors", successors.size());
			}

			/* otherwise determine methods for the task */
			else {

				logger.info("Computing successors for COMPLEX task {} in state {}", nextTask, state);
				Set<Method> usedMethods = new HashSet<>();
				PerformanceLogger.logStart("compute instances");
				Collection<MethodInstance> instances = util.getMethodInstancesForTaskThatAreApplicableInState(knowledge, this.problem.getDomain().getMethods(), nextTask, state,
						currentlyRemainingTasks);
				PerformanceLogger.logEnd("compute instances");
				for (MethodInstance instance : instances) {
					
					/* check the evaluable condition of this method instance */
					if (instance.getMethod() instanceof OCIPMethod) {
						Monom additionalPrecondition = new Monom(((OCIPMethod)instance.getMethod()).getEvaluablePrecondition(), instance.getGrounding());
						boolean containsUnsatisfiedLiteral = false;
						for (Literal evaluableLiteral : additionalPrecondition) {
							if (!evaluablePredicates.containsKey(evaluableLiteral.getPropertyName()))
								throw new IllegalArgumentException("It is not known how to evaluate the evaluatable predicate \"" + evaluableLiteral.getPropertyName() + "\" in the precondition of " + instance.getMethod());
							EvaluablePredicate ep = evaluablePredicates.get(evaluableLiteral.getPropertyName());
							List<LiteralParam> paramList = evaluableLiteral.getParameters();
							ConstantParam[] groundParamArray = new ConstantParam[paramList.size()];
							for (int i = 0; i < groundParamArray.length; i++) {
								LiteralParam param = paramList.get(i);
								groundParamArray[i] = (param instanceof ConstantParam) ? (ConstantParam)param : instance.getGrounding().get(param);
							}
							if (!ep.test(state, groundParamArray)) {
								containsUnsatisfiedLiteral = true;
								break;
							}
						}
						if (containsUnsatisfiedLiteral)
							continue;
					}

					/* skip this instance if the method is lonely and we already used it */
					if (!usedMethods.contains(instance.getMethod())) {
						usedMethods.add(instance.getMethod());
					} else if (instance.getMethod().isLonely()) {
						continue;
					}

					logger.info("Adding successor {}", instance);
					
					/* if the depth is % k == 0, then compute the rest problem explicitly */

					List<Literal> prependedTasks = util.getTaskChainOfTotallyOrderedNetwork(instance.getNetwork());
					List<Literal> remainingTasks = new ArrayList<>(prependedTasks);
					remainingTasks.addAll(currentlyRemainingTasks);
					remainingTasks.remove(prependedTasks.size()); // remove the first literal of the 2ndly appended list
					TFDNode node = null;
					if (depth % checkpointDistance == 0)
						node = new TFDNode(new Monom(state, false), remainingTasks, instance, null);
					else
						node = new TFDNode(instance, remainingTasks.isEmpty());
					parentMap.put(node, l);
					successors.add(new NodeExpansionDescription<>(l, node, "edge label", NodeType.OR));
				}

				logger.info("Computed {} successors", successors.size());
			}
			// l.getPoint().clear();
			return successors;
		};
	}

	@Override
	public NodeGoalTester<TFDNode> getGoalTester() {
		return p -> p.isGoal();
	}

	public CEOCIPSTNPlanningProblem getProblem() {
		return problem;
	}

	@Override
	public String toString() {
		return "CEOCTFDGraphGenerator [problem=" + problem + ", knowledge=" + knowledge + ", primitiveTasks=" + primitiveTasks + "]";
	}

	@Override
	public boolean isSelfContained() {
		return false;
	}


}
