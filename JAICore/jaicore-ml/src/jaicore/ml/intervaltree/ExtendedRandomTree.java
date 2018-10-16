package jaicore.ml.intervaltree;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map.Entry;

import jaicore.ml.core.Interval;
import weka.classifiers.trees.RandomTree;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Extension of a classic RandomTree to predict intervals.
 * 
 * @author mirkoj
 *
 */
public class ExtendedRandomTree extends RandomTree {

	/**
	 * 
	 */
	private static final long serialVersionUID = -467555221387281335L;

	public Interval predictInterval(Instance data) {
		// pruneIntervals(data);
		Interval[] mappedData = new Interval[data.numAttributes() / 2];
		int counter = 0;
		for (int attrNum = 0; attrNum < data.numAttributes(); attrNum = attrNum + 2) {
			mappedData[counter] = new Interval(data.value(attrNum), data.value(attrNum + 1));
			counter++;
		}
		return predictInterval(mappedData);
	}

	public Interval predictInterval(Interval[] queriedInterval) {
		// the stack of elements that still have to be processed.
		Deque<Entry<Interval[], Tree>> stack = new ArrayDeque<>();
		// initially, the root and the queried interval
		stack.push(getEntry(queriedInterval, m_Tree));

		// the list of all leaf values
		ArrayList<Double> list = new ArrayList<>();
		while (stack.peek() != null) {
			// pick the next node to process
			Entry<Interval[], Tree> toProcess = stack.pop();
			Tree nextTree = toProcess.getValue();
			double threshold = nextTree.getSplitPoint();
			int attribute = nextTree.getAttribute();
			Tree[] children = nextTree.getSuccessors();
			double[] classDistribution = nextTree.getClassDistribution();
			// process node
			if (attribute == -1) {
				// node is a leaf
				// for now, assume that we have regression!
				list.add(classDistribution[0]);
			} else {
				Interval intervalForAttribute = queriedInterval[attribute];
				// no leaf node...
				Tree leftChild = children[0];
				Tree rightChild = children[1];
				// traverse the tree

				if (intervalForAttribute.getLowerBound() <= threshold) {

					if (threshold <= intervalForAttribute.getUpperBound()) {
						// scenario: x_min <= threshold <= x_max
						// query [x_min, threshold] on the left child
						// query [threshold, x_max] right
						Interval[] newInterval = substituteInterval(toProcess.getKey(),
								new Interval(intervalForAttribute.getLowerBound(), threshold), attribute);
						Interval[] newMaxInterval = substituteInterval(toProcess.getKey(),
								new Interval(threshold, intervalForAttribute.getUpperBound()), attribute);
						stack.push(getEntry(newInterval, leftChild));
						stack.push(getEntry(newMaxInterval, rightChild));
					} else {
						// scenario: threshold <= x_min <= x_max
						// query [x_min, x_max] on the left child
						stack.push(getEntry(toProcess.getKey(), leftChild));
					}
				}
				// analogously...
				if (intervalForAttribute.getUpperBound() > threshold) {
					stack.push(getEntry(toProcess.getKey(), rightChild));
				}
			}
		}
		return combineInterval(list);
	}

	private Interval combineInterval(ArrayList<Double> list) {
		double min = list.stream().min(Double::compareTo)
				.orElseThrow(() -> new IllegalStateException("Couldn't find minimum?!"));
		double max = list.stream().max(Double::compareTo)
				.orElseThrow(() -> new IllegalStateException("Couldn't find maximum?!"));
		return new Interval(min, max);
	}

	private Interval[] substituteInterval(Interval[] original, Interval toSubstitute, int index) {
		Interval[] copy = Arrays.copyOf(original, original.length);
		copy[index] = toSubstitute;
		return copy;
	}

	private Entry<Interval[], Tree> getEntry(Interval[] interval, Tree tree) {
		return new AbstractMap.SimpleEntry<>(interval, tree);
	}

	/**
	 * Prunes the range query to the features of this bagged random tree (i.e.
	 * removes the features that were not selected for this tree)Ï
	 * 
	 * @param rangeQuery
	 * @return
	 */
	private Instance pruneIntervals(Instance rangeQuery) {
		Instances header = this.m_Info;
		System.out.println("Num attr header " + (header.numAttributes() - 1));
		System.out.println("Num attr range query " + (rangeQuery.numAttributes() / 2));
		for (int i = 0; i < header.numAttributes(); i++) {
			weka.core.Attribute attribute = header.attribute(i);
			// System.out.println("Attribute at pos "+ i + " has header "+
			// attribute.name());
		}
		return rangeQuery;
	}
}
