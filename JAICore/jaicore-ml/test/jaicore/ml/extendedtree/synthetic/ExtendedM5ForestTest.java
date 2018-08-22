package jaicore.ml.extendedtree.synthetic;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import jaicore.ml.core.Interval;
import jaicore.ml.intervaltree.ExtendedM5Forest;
import junit.framework.Assert;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader.ArffReader;

public class ExtendedM5ForestTest {

	private static String trainFile = "resources/regression_data/autos_200_RQPtrain.arff";

	private static String testFile = "resources/regression_data/autos_200_RQPtest.arff";

	private static int SIZE = 100;
	private ExtendedM5Forest[] classifier = new ExtendedM5Forest[seedNum];
	private static final int seedNum = 10;
	
	private static final double [] l1Lower = new double[seedNum];
	private static final double [] l1Upper = new double[seedNum];


	@Before
	public void testTrain() {
		try (BufferedReader reader = Files.newBufferedReader(Paths.get(trainFile), StandardCharsets.UTF_8)) {
			ArffReader arffReader = new ArffReader(reader);
			Instances data = arffReader.getData();
			for (int seed = 0; seed < seedNum; seed++) {
				Instances iData = randomSubset(data, seed);
				iData.setClassIndex(iData.numAttributes() - 1);

				classifier[seed] = new ExtendedM5Forest();
				classifier[seed].buildClassifier(iData);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}
	}

	private static Instances randomSubset(Instances data, int seed) {
		int len = data.size() - SIZE;
		Random rnd = new Random(seed);
		int begin = rnd.nextInt(len);
		Instances toReturn = new Instances(data, begin, SIZE);
		return toReturn;
	}

	/**
	 * Test the classifier without any cross-validation
	 */
	@Test
	public void testPredict() {
		for (int seed = 0; seed < seedNum; seed++) {
			try (BufferedReader reader = Files.newBufferedReader(Paths.get(testFile), StandardCharsets.UTF_8)) {
				ArffReader arffReader = new ArffReader(reader);
				Instances data = arffReader.getData();
				List<Double> predictedLowers = new ArrayList<>();
				List<Double> actualLowers = new ArrayList<>();
				List<Double> predictedUppers = new ArrayList<>();
				List<Double> actualUppers = new ArrayList<>();
				for (Instance instance : data) {
					// construct the real interval
					double lower = instance.value(data.numAttributes() - 2);
					double upper = instance.value(data.numAttributes() - 1);
					Instance strippedInstance = new DenseInstance(data.numAttributes() - 2);
					for (int i = 0; i < data.numAttributes() - 2; i++) {
						strippedInstance.setValue(i, instance.value(i));
					}
					Interval actualInterval = new Interval(lower, upper);
					Interval predictedInterval = classifier[seed].predictInterval(strippedInstance);
				//	System.out.println(
				//			"Actual interval: " + actualInterval + ", predicted Interval " + predictedInterval);
					predictedLowers.add(predictedInterval.getLowerBound());
					predictedUppers.add(predictedInterval.getUpperBound());
					actualLowers.add(lower);
					actualUppers.add(upper);
				}
				// construct R^2 loss
		//		double r2lossLower = r2Loss(predictedLowers, actualLowers);
		//		double r2LossUpper = r2Loss(predictedUppers, actualUppers);
		//		System.out.println("R^2 loss for the lower bound is " + r2lossLower);
		//		System.out.println("R^2 loss for the upper bound is " + r2LossUpper);

				double l1LossLower = L1Loss(predictedLowers, actualLowers);
				double l1LossUpper = L1Loss(predictedUppers, actualUppers);
				System.out.println("L1 loss for the lower bound is " + l1LossLower);
				System.out.println("L1 loss for the upper bound is " + l1LossUpper);
				
				l1Lower[seed] = l1LossLower;
				l1Upper[seed] = l1LossUpper;
				
			} catch (Exception e) {
				e.printStackTrace();
				Assert.fail();
			}
		}
		double lowerMax = Arrays.stream(l1Lower).max().getAsDouble();
		double upperMax = Arrays.stream(l1Upper).max().getAsDouble();
		System.out.println(Arrays.stream(l1Lower).filter(d -> d != lowerMax).average().getAsDouble());
		System.out.println(Arrays.stream(l1Upper).filter(d -> d != upperMax).average().getAsDouble());

	}

	private static final double L1Loss(List<Double> predicted, List<Double> actual) {
		double accumulated = 0;
		for (int i = 0; i < predicted.size(); i++) {
			accumulated += Math.abs(predicted.get(i) - actual.get(i));
		}
		return (accumulated / predicted.size());
	}

	private static final double r2Loss(List<Double> predicted, List<Double> actual) {
		double actualAvg = actual.stream().mapToDouble((s) -> s).average().orElseThrow(IllegalStateException::new);
		double ssTot = actual.stream().mapToDouble((s) -> Math.pow(s - actualAvg, 2)).sum();
		double ssRes = 0;
		for (int i = 0; i < predicted.size(); i++) {
			ssRes += Math.pow(predicted.get(i) - actual.get(i), 2);
		}
		return 1 - (ssRes / ssTot);
	}
}
