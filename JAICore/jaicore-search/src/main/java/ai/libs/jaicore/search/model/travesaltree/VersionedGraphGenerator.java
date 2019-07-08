package ai.libs.jaicore.search.model.travesaltree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import ai.libs.jaicore.search.core.interfaces.GraphGenerator;
import ai.libs.jaicore.search.structure.graphgenerator.MultipleRootGenerator;
import ai.libs.jaicore.search.structure.graphgenerator.NodeGoalTester;
import ai.libs.jaicore.search.structure.graphgenerator.PathGoalTester;
import ai.libs.jaicore.search.structure.graphgenerator.SingleRootGenerator;
import ai.libs.jaicore.search.structure.graphgenerator.SuccessorGenerator;

/**
 * Class which wraps up a normal GraphGenerator and is adding a id to every node
 *
 * @author jkoepe
 *
 */
public class VersionedGraphGenerator<T, A> implements VersionedGraphGeneratorInterface<VersionedDomainNode<T>, A> {

	// variables
	private GraphGenerator<T, A> gen;
	private boolean nodeNumbering;
	private Random rnd;

	public VersionedGraphGenerator(final GraphGenerator<T, A> gen) {
		this.gen = gen;
		this.nodeNumbering = true;
		this.rnd = new Random();
	}

	/**
	 * Retrieves the next id
	 *
	 * @return
	 *         returns a unique id if numbering is enable, otherwise -1
	 */
	public int getNextID() {
		if (this.nodeNumbering) {
			return this.rnd.nextInt(Integer.MAX_VALUE);
		} else {
			return -1;
		}
	}

	@Override
	public SingleRootGenerator<VersionedDomainNode<T>> getRootGenerator() {
		return () -> {
			SingleRootGenerator<T> rootGenerator = (SingleRootGenerator<T>) this.gen.getRootGenerator();
			T root = rootGenerator.getRoot();
			return new VersionedDomainNode<>(root, this.getNextID());
		};
	}

	public SingleRootGenerator<VersionedDomainNode<T>> getSingleRootGenerator() {
		return () -> {
			SingleRootGenerator<T> rootGenerator = (SingleRootGenerator<T>) this.gen.getRootGenerator();
			T root = rootGenerator.getRoot();
			return new VersionedDomainNode<>(root, this.getNextID());
		};
	}

	public MultipleRootGenerator<VersionedDomainNode<T>> getMultipleRootGenerator() {
		return () -> {
			MultipleRootGenerator<T> rootGenerator = (MultipleRootGenerator<T>) this.gen.getRootGenerator();
			Collection<VersionedDomainNode<T>> vRoots = new ArrayList<>();
			Collection<T> roots = rootGenerator.getRoots();

			roots.stream().forEach(n -> vRoots.add(new VersionedDomainNode<T>(n, this.getNextID())));
			return vRoots;
		};
	}

	@Override
	public SuccessorGenerator<VersionedDomainNode<T>, A> getSuccessorGenerator() {
		return nodeToExpand -> {
			SuccessorGenerator<T, A> successorGenerator = this.gen.getSuccessorGenerator();
			Collection<NodeExpansionDescription<T, A>> successorDescriptions = successorGenerator.generateSuccessors(nodeToExpand.getNode());

			List<NodeExpansionDescription<VersionedDomainNode<T>, A>> versionedDescriptions = new ArrayList<>();

			successorDescriptions.stream()
			.forEach(description -> versionedDescriptions.add(new NodeExpansionDescription<>(new VersionedDomainNode<>(description.getTo(), this.getNextID()), description.getAction(), description.getTypeOfToNode())));
			return versionedDescriptions;
		};
	}

	@Override
	public NodeGoalTester<VersionedDomainNode<T>> getGoalTester() {
		return n -> {
			NodeGoalTester<T> goalTester = (NodeGoalTester<T>) this.gen.getGoalTester();
			return goalTester.isGoal(n.getNode());
		};
	}

	/**
	 * A method which redirects the NodeGoalTester from VersionedT<T> to T
	 *
	 * @return
	 */
	public NodeGoalTester<VersionedDomainNode<T>> getNodeGoalTester() {
		return n -> {
			NodeGoalTester<T> goalTester = (NodeGoalTester<T>) this.gen.getGoalTester();
			return goalTester.isGoal(n.getNode());
		};
	}

	/**
	 * Method which redirects the pathgoaltester from versioned<T> nodes to simple t nodes.
	 * This method does currently not work as it is not implemented to extract the path from a versioned node
	 *
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public PathGoalTester<VersionedDomainNode<T>> getPathGoalTester() {
		return n -> {
			PathGoalTester<T> goalTester = (PathGoalTester<T>) this.gen.getGoalTester();
			return goalTester.isGoal((List<T>) n);
		};
	}

	@Override
	public void setNodeNumbering(final boolean numbering) {
		this.nodeNumbering = numbering;
	}

}