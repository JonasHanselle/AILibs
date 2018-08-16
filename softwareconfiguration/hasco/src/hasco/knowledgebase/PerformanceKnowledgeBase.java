package hasco.knowledgebase;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import hasco.core.Util;
import hasco.model.CategoricalParameterDomain;
import hasco.model.ComponentInstance;
import hasco.model.NumericParameterDomain;
import hasco.model.Parameter;
import hasco.model.ParameterDomain;
import jaicore.basic.SQLAdapter;
import jaicore.basic.sets.PartialOrderedSet;
import jaicore.ml.core.FeatureSpace;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.ProtectedProperties;

/**
 * Knowledge base that manages observed performance behavior
 * 
 * @author jmhansel
 *
 */

public class PerformanceKnowledgeBase {

	private SQLAdapter sqlAdapter;
	private Map<String, HashMap<ComponentInstance, Double>> performanceSamples;
	/** This is map contains a String */
	private Map<String, HashMap<String, List<Pair<ParameterConfiguration, Double>>>> performanceSamplesByIdentifier;
	private Map<String, HashMap<String, Instances>> performanceInstancesByIdentifier;
//	private Map<String, HashMap<String, Integer>> numberCompletelyDistinctSamples;

	/**
	 * Inner helper class for managing parameter configurations easily.
	 * 
	 * @author jmhansel
	 *
	 */
	private class ParameterConfiguration {
		private final List<Pair<Parameter, String>> values;

		public ParameterConfiguration(ComponentInstance composition) {
			ArrayList<Pair<Parameter, String>> temp = new ArrayList<Pair<Parameter, String>>();
			List<ComponentInstance> componentInstances = Util.getComponentInstancesOfComposition(composition);
			for (ComponentInstance compInst : componentInstances) {
				PartialOrderedSet<Parameter> parameters = compInst.getComponent().getParameters();
				for (Parameter parameter : parameters) {
					temp.add(Pair.of(parameter, compInst.getParameterValues().get(parameter.getName())));
				}
			}
			// Make the list immutable to avoid problems with hashCode
			values = Collections.unmodifiableList(temp);
		}

		@Override
		public int hashCode() {
			return values.hashCode();
		}

		public List<Pair<Parameter, String>> getValues() {
			return this.values;
		}
	}

	public PerformanceKnowledgeBase(final SQLAdapter sqlAdapter) {
		super();
		this.sqlAdapter = sqlAdapter;
		// this.performanceSamples = new HashMap<String, HashMap<ComponentInstance,
		// Double>>();
		// this.performanceSamplesByIdentifier = new HashMap<String, HashMap<String,
		// List<Pair<ParameterConfiguration, Double>>>>();
		this.performanceInstancesByIdentifier = new HashMap<String, HashMap<String, Instances>>();
//		this.numberCompletelyDistinctSamples = new HashMap<String, HashMap<String, Integer>>();
	}

	public PerformanceKnowledgeBase() {
		super();
		// this.performanceSamples = new HashMap<String, HashMap<ComponentInstance,
		// Double>>();
		// this.performanceSamplesByIdentifier = new HashMap<String, HashMap<String,
		// List<Pair<ParameterConfiguration, Double>>>>();
		this.performanceInstancesByIdentifier = new HashMap<String, HashMap<String, Instances>>();
//		this.numberCompletelyDistinctSamples = new HashMap<String, HashMap<String, Integer>>();
	}

	public void addPerformanceSample(String benchmarkName, ComponentInstance componentInstance, double score,
			boolean addToDB) {
		String identifier = Util.getComponentNamesOfComposition(componentInstance);

		if (performanceInstancesByIdentifier.get(benchmarkName) == null) {
			// System.out.println("Creating new HashMap");
			HashMap<String, Instances> newMap = new HashMap<String, Instances>();
			HashMap<String, Integer> newMap2 = new HashMap<String, Integer>();
			performanceInstancesByIdentifier.put(benchmarkName, newMap);
//			numberCompletelyDistinctSamples.put(benchmarkName, newMap2);

		}

		if (!performanceInstancesByIdentifier.get(benchmarkName).containsKey(identifier)) {
			// System.out.println("Creating new Instances Object");
			// Create Instances pipeline for this pipeline type
			ParameterConfiguration parameterConfig = new ParameterConfiguration(componentInstance);
			Instances instances = null;
			// Add parameter domains as attributes
			List<ComponentInstance> componentInstances = Util.getComponentInstancesOfComposition(componentInstance);
			ArrayList<Attribute> allAttributes = new ArrayList<Attribute>();
			for (ComponentInstance ci : componentInstances) {
				PartialOrderedSet<Parameter> parameters = ci.getComponent().getParameters();
				ArrayList<Attribute> attributes = new ArrayList<Attribute>(parameters.size());
				for (Parameter parameter : parameters) {
					ParameterDomain domain = parameter.getDefaultDomain();
					Attribute attr = null;
					if (domain instanceof CategoricalParameterDomain) {
						CategoricalParameterDomain catDomain = (CategoricalParameterDomain) domain;
						// TODO further namespacing of attributes!!!
						attr = new Attribute(ci.getComponent().getName() + "::" + parameter.getName(),
								Arrays.asList(catDomain.getValues()));
					} else if (domain instanceof NumericParameterDomain) {
						NumericParameterDomain numDomain = (NumericParameterDomain) domain;
						// TODO is there a better way to set the range of this attribute?
						// if(numDomain.getMin() == numDomain.getMax()) {
						// System.out.println("Domain has range of 0, skipping it!");
						// continue;
						// }
						String range = "[" + numDomain.getMin() + "," + numDomain.getMax() + "]";
						Properties prop = new Properties();
						prop.setProperty("range", range);
						ProtectedProperties metaInfo = new ProtectedProperties(prop);
						attr = new Attribute(ci.getComponent().getName() + "::" + parameter.getName(), metaInfo);
					}
					// System.out.println("Trying to add parameter: " + attr.name() + " for
					// component: "
					// + componentInstance.getComponent().getName());

					attributes.add(attr);
				}
				allAttributes.addAll(attributes);
			}
			// Add performance score as class attribute TODO make score numeric?
			Attribute scoreAttr = new Attribute("performance_score");
			allAttributes.add(scoreAttr);
			instances = new Instances("performance_samples", allAttributes, 16);
			instances.setClass(scoreAttr);
			performanceInstancesByIdentifier.get(benchmarkName).put(identifier, instances);
//			numberCompletelyDistinctSamples.get(benchmarkName).put(identifier, 0);
		}

		// Add Instance for performance samples to corresponding Instances
		// System.out.println("Adding instance");
		Instances instances = performanceInstancesByIdentifier.get(benchmarkName).get(identifier);
		DenseInstance instance = new DenseInstance(instances.numAttributes());
		ParameterConfiguration config = new ParameterConfiguration(componentInstance);
		List<Pair<Parameter, String>> values = config.getValues();
		for (int i = 0; i < instances.numAttributes() - 1; i++) {
			Attribute attr = instances.attribute(i);
			Parameter param = values.get(i).getLeft();
			// System.out.println("Adding vlaue " + values.get(i).getRight() + " for
			// Parameter " + param);
			if (param.isCategorical()) {
				// Enumeration<Object> e = attr.enumerateValues();
				// System.out.println("Values: ");
				// if (e == null) {
				// System.out.println("Enumeration is null");
				// } else {
				// while (e.hasMoreElements()) {
				// System.out.println(e.nextElement().toString());
				// }
				// }
				String value = values.get(i).getRight();
				// if (value.equals("default")) {
				// System.out.println("Value is default!");
				// value = (String) param.getDefaultValue();
				// System.out.println("Default value is: " + value);
				// }
				instance.setValue(attr, value);
			} else if (param.isNumeric()) {
				// String value = values.get(i).getRight();
				// if (value.equals("default"))
				// value = (String) param.getDefaultValue();
				double finalValue = Double.parseDouble(values.get(i).getRight());
				instance.setValue(attr, finalValue);
				// System.out.println("bounds: [" + attr.getLowerNumericBound() + "," +
				// attr.getUpperNumericBound() + "]");
			}
		}
		Attribute scoreAttr = instances.classAttribute();
		instance.setValue(scoreAttr, score);
		// System.out.println("Adding instance: " + instance.toString());
		// int count =
		// numberCompletelyDistinctSamples.get(benchmarkName).get(identifier);
		// System.out.println("Current distinct count: " + count);
		// boolean distinctFromAll = true;
		// for (Instance compare : instances) {
		// for (int i = 0; i < instances.numAttributes()-1; i++) {
		// if ((instances.attribute(i).isNominal() || instances.attribute(i).isString())
		// && (instances.attribute(i).numValues() < count)) {
		// System.out.println("Skipping nominal");
		// continue;
		// }
		// if(instances.attribute(i).getUpperNumericBound() <=
		// instances.attribute(i).getLowerNumericBound()) {
		// System.out.println("Skipping numeric");
		// continue;
		// }
		// System.out.println("Comparing " + instance.value(i) + " and " +
		// compare.value(i));
		// if (instance.value(i) == compare.value(i)) {
		// distinctFromAll = false;
		// }
		// }
		// }
		// System.out.println("min distinct value for attributes: " + min);
		// if (distinctFromAll)
		// numberCompletelyDistinctSamples.get(benchmarkName).put(identifier, count+1);
		// System.out.println("New distinct count: " +
		// numberCompletelyDistinctSamples.get(benchmarkName).get(identifier));
		performanceInstancesByIdentifier.get(benchmarkName).get(identifier).add(instance);

		if (addToDB)
			this.addPerformanceSampleToDB(benchmarkName, componentInstance, score);
	}

	public Map<String, HashMap<ComponentInstance, Double>> getPerformanceSamples() {
		return this.performanceSamples;
	}

	public Map<String, HashMap<String, List<Pair<ParameterConfiguration, Double>>>> getPerformanceSamplesByIdentifier() {
		return performanceSamplesByIdentifier;
	}

	public String getStringOfMaps() {
		return performanceSamples.toString();
	}

	public FeatureSpace createFeatureSpaceFromComponentInstance(ComponentInstance compInst) {
		FeatureSpace space = new FeatureSpace();
		for (Parameter param : compInst.getComponent().getParameters()) {
			ParameterDomain domain = param.getDefaultDomain();
		}
		return space;
	}

	public void initializeDBTables() {
		/* initialize tables if not existent */
		try {
			ResultSet rs = sqlAdapter.getResultsOfQuery("SHOW TABLES");
			boolean havePerformanceTable = false;
			while (rs.next()) {
				String tableName = rs.getString(1);
				if (tableName.equals("performance_samples")) {
					havePerformanceTable = true;
				}
			}

			if (!havePerformanceTable) {
				System.out.println("Creating table for performance samples");
				sqlAdapter.update(
						"CREATE TABLE `performance_samples` (\r\n" + " `sample_id` int(10) NOT NULL AUTO_INCREMENT,\r\n"
								+ " `benchmark` varchar(200) COLLATE utf8_bin DEFAULT NULL,\r\n"
								+ " `composition` json NOT NULL,\r\n" + " `score` double NOT NULL,\r\n"
								+ " PRIMARY KEY (`sample_id`)\r\n"
								+ ") ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COLLATE=utf8_bin",
						new ArrayList<>());
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void addPerformanceSampleToDB(String benchmarkName, ComponentInstance componentInstance, double score) {
		try {
			Map<String, String> map = new HashMap<>();
			map.put("benchmark", benchmarkName);
			ObjectMapper mapper = new ObjectMapper();
			String composition = mapper.writeValueAsString(componentInstance);
			map.put("composition", composition);
			map.put("score", "" + score);
			this.sqlAdapter.insert("performance_samples", map);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the number of samples for the given benchmark name and pipeline
	 * identifier.
	 * 
	 * @param benchmarkName
	 * @param identifier
	 * @return
	 */
	public int getNumSamples(String benchmarkName, String identifier) {
		if (!this.performanceInstancesByIdentifier.containsKey(benchmarkName))
			return 0;
		if (!this.performanceInstancesByIdentifier.get(benchmarkName).containsKey(identifier))
			return 0;

		return this.performanceInstancesByIdentifier.get(benchmarkName).get(identifier).numInstances();
	}

	/**
	 * Returns the number of samples for the given benchmark name and pipeline
	 * identifier, which are pairwise distinct in all attribute values.
	 * 
	 * @param benchmarkName
	 * @param identifier
	 * @return
	 */
//	public int getNumCompletelyDistinctSamples(String benchmarkName, String identifier) {
//		if (!this.numberCompletelyDistinctSamples.containsKey(benchmarkName))
//			return 0;
//		if (!this.numberCompletelyDistinctSamples.get(benchmarkName).containsKey(identifier))
//			return 0;
//
//		return this.numberCompletelyDistinctSamples.get(benchmarkName).get(identifier);
//	}

	/**
	 * Returns the number of significant samples for the given benchmark name and
	 * pipeline identifier. Significant means, that
	 * 
	 * @param benchmarkName
	 * @param identifier
	 * @return
	 */
	public int getNumSignificantSamples(String benchmarkName, String identifier) {
		if (!this.performanceInstancesByIdentifier.containsKey(benchmarkName))
			return 0;
		if (!this.performanceInstancesByIdentifier.get(benchmarkName).containsKey(identifier))
			return 0;
		Instances instances = this.performanceInstancesByIdentifier.get(benchmarkName).get(identifier);
		int numDistinctValues = 1;
		for (int i = 0; i < instances.numInstances(); i++) {
			for (int j = 0; j < i; j++) {
				boolean allValuesDistinct = true;
				for (int k = 0; k < instances.numAttributes(); k++) {
					if (instances.get(i).value(k) == instances.get(j).value(k)) {
						allValuesDistinct = false;
					}
				}
				if (allValuesDistinct)
					numDistinctValues++;
			}
		}
		return numDistinctValues;
	}

	// /**
	// * Checks whether there is a specific amount of samples available, that are
	// * pairwise distinct in all attributes.
	// *
	// * @param benchmarkName
	// * @param identifier
	// * @param numSamplesRequired
	// * @return
	// */
	// public boolean hasSignificantSamples(String benchmarkName, String identifier,
	// int numSamplesRequired) {
	// if (!this.performanceInstancesByIdentifier.containsKey(benchmarkName))
	// return false;
	// if
	// (!this.performanceInstancesByIdentifier.get(benchmarkName).containsKey(identifier))
	// return false;
	// Instances instances =
	// this.performanceInstancesByIdentifier.get(benchmarkName).get(identifier);
	// int numDistinctValues = 1;
	// for (int i = 0; i < instances.numInstances(); i++) {
	// for (int j = 0; j < i; j++) {
	// boolean allValuesDistinct = true;
	// for (int k = 0; k < instances.numAttributes(); k++) {
	// int temp = numSamplesRequired;
	// if(instances.attribute(k).isNominal() && instances.attribute(k).numValues() <
	// numSamplesRequired)
	// temp = instances.attribute(k).numValues();
	// if (instances.get(i).value(k) == instances.get(j).value(k)) {
	// allValuesDistinct = false;
	// }
	// }
	// if (allValuesDistinct)
	// numDistinctValues++;
	// }
	// }
	// return numDistinctValues;
	// }

	public void loadPerformanceSamplesFromDB() {
		try {
			ResultSet rs = sqlAdapter
					.getResultsOfQuery("SELECT benchmark, composition, score FROM performance_samples");
			ObjectMapper mapper = new ObjectMapper();
			while (rs.next()) {
				String benchmarkName = rs.getString(1);
				String ciString = rs.getString(2);
				System.out.println(ciString);
				ComponentInstance composition = mapper.readValue(ciString, ComponentInstance.class);
				double score = rs.getDouble(3);
				this.addPerformanceSample(benchmarkName, composition, score, false);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Checks whether k samples are available, which are
	 * 
	 * @param k
	 * @return
	 */
	public boolean kDistinctAttributeValuesAvailable(String benchmarkName, ComponentInstance composition, int minNum) {
		String identifier = Util.getComponentNamesOfComposition(composition);
		if (!this.performanceInstancesByIdentifier.containsKey(benchmarkName))
			return false;
		if (!this.performanceInstancesByIdentifier.get(benchmarkName).containsKey(identifier))
			return false;
		Instances instances = performanceInstancesByIdentifier.get(benchmarkName).get(identifier);
		if(instances.numInstances()<minNum)
			return false;
		for (int i = 0; i < instances.numAttributes()-1; i++) {
			// if the attribute is nominal or string but the number of values is smaller
			// than k, skip it
			if (instances.attribute(i).numValues() > 0 && instances.attribute(i).numValues() < minNum) {
				// System.out.println("Skipping attribute " + instances.attribute(i));
				if (instances.numDistinctValues(i) < instances.attribute(i).numValues())
					return false;
			} else if (instances.attribute(i).getUpperNumericBound() <= instances.attribute(i).getLowerNumericBound()) {
				// System.out.println("Skipping Attribute becasue of bounds: " +
				// instances.attribute(i));
				continue;
			} else if (instances.numDistinctValues(i) < minNum) {
				// System.out.println("Attribute values: " + instances.numDistinctValues(i));
				// System.out.println("Required: " + minNum);
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks whether at least k sample are available, that are pairwise distinct in
	 * each of their attribute values.
	 * 
	 * @param benchmarkName
	 * @param composition
	 * @param minNum strictly positive minimum number of samples
	 * @return
	 */
	public boolean kCompletelyDistinctSamplesAvailable(String benchmarkName, ComponentInstance composition,
			int minNum) {
		String identifier = Util.getComponentNamesOfComposition(composition);
		if (!this.performanceInstancesByIdentifier.containsKey(benchmarkName))
			return false;
		if (!this.performanceInstancesByIdentifier.get(benchmarkName).containsKey(identifier))
			return false;
		Instances instances = performanceInstancesByIdentifier.get(benchmarkName).get(identifier);
		if(instances.numInstances()==0)
			return false;
		int count = 0;
		if(minNum == 1 && instances.numInstances()>0)
			return true;
		for (int i = 0; i < instances.numInstances(); i++) {
			boolean distinctFromAll = true;
			for (int j = 0; j < i; j++) {
				Instance instance1 = instances.get(i);
				Instance instance2 = instances.get(j);
				for (int k = 0; k < instances.numAttributes()-1; k++) {
					if ((instances.attribute(k).isNominal() || instances.attribute(k).isString())
							&& (instances.attribute(k).numValues() < minNum)) {
//						System.out.println("Skipping nominal");
						continue;
					} else if (instances.attribute(k).getUpperNumericBound() <= instances.attribute(k)
							.getLowerNumericBound()) {
//						System.out.println(instances.attribute(k).getLowerNumericBound() + " "
//								+ instances.attribute(k).getUpperNumericBound());
//						System.out.println("Skipping numeric");
						continue;
					}
//					 System.out.println("Comparing " + instance.value(i) + " and " +
//					 compare.value(i));
					if (instance1.value(k) == instance2.value(k)) {
//						System.out.println(instance1.value(k) + " == " + instance2.value(k) + ": " + (instance1.value(k)==instance2.value(k)));
						distinctFromAll = false;
					}
				}
			}
			if (distinctFromAll)
				count++;
//				System.out.println("count: " + count);
//				System.out.println("min num: " + minNum);
			if (count >= minNum)
				return true;
		}
		return false;
	}

	/**
	 * Creates an Instances object containing all performance samples observed for
	 * pipelines of the type of the given ComponentInstance for the given benchmark.
	 * 
	 * @param benchmarkName
	 * @param pipeline
	 * @return Instances
	 */
	// public Instances createInstancesForPerformanceSamples(String benchmarkName,
	// ComponentInstance composition) {
	// Instances instances = null;
	// String identifier = Util.getComponentNamesOfComposition(composition);
	// // Add parameter domains as attributes
	// List<ComponentInstance> componentInstances =
	// Util.getComponentInstancesOfComposition(composition);
	// ArrayList<Attribute> allAttributes = new ArrayList<Attribute>();
	// for (ComponentInstance componentInstance : componentInstances) {
	// PartialOrderedSet<Parameter> parameters =
	// componentInstance.getComponent().getParameters();
	// ArrayList<Attribute> attributes = new
	// ArrayList<Attribute>(parameters.size());
	// for (Parameter parameter : parameters) {
	// ParameterDomain domain = parameter.getDefaultDomain();
	// Attribute attr = null;
	// if (domain instanceof CategoricalParameterDomain) {
	// CategoricalParameterDomain catDomain = (CategoricalParameterDomain) domain;
	// // TODO further namespacing of attributes!!!
	// attr = new Attribute(componentInstance.getComponent().getName() + "::" +
	// parameter.getName(),
	// Arrays.asList(catDomain.getValues()));
	// } else if (domain instanceof NumericParameterDomain) {
	// NumericParameterDomain numDomain = (NumericParameterDomain) domain;
	// // TODO is there a better way to set the range of this attribute?
	// // if(numDomain.getMin() == numDomain.getMax()) {
	// // System.out.println("Domain has range of 0, skipping it!");
	// // continue;
	// // }
	// String range = "[" + numDomain.getMin() + "," + numDomain.getMax() + "]";
	// Properties prop = new Properties();
	// prop.setProperty("range", range);
	// ProtectedProperties metaInfo = new ProtectedProperties(prop);
	// attr = new Attribute(componentInstance.getComponent().getName() + "::" +
	// parameter.getName(),
	// metaInfo);
	// }
	// // System.out.println("Trying to add parameter: " + attr.name() + " for
	// // component: "
	// // + componentInstance.getComponent().getName());
	//
	// attributes.add(attr);
	// }
	// allAttributes.addAll(attributes);
	// }
	// // Add performance score as class attribute TODO make score numeric?
	// Attribute scoreAttr = new Attribute("performance_score");
	// allAttributes.add(scoreAttr);
	// instances = new Instances("performance_samples", allAttributes,
	// this.performanceSamples.get(benchmarkName).size());
	// instances.setClass(scoreAttr);
	// List<Pair<ParameterConfiguration, Double>> samples =
	// performanceSamplesByIdentifier.get(benchmarkName)
	// .get(identifier);
	// for (Pair<ParameterConfiguration, Double> sample : samples) {
	// DenseInstance instance = new DenseInstance(instances.numAttributes());
	// ParameterConfiguration config = sample.getLeft();
	// double score = sample.getRight();
	// List<Pair<Parameter, String>> values = config.getValues();
	// for (int i = 0; i < instances.numAttributes() - 1; i++) {
	// Attribute attr = instances.attribute(i);
	// Parameter param = values.get(i).getLeft();
	// if (param.isCategorical()) {
	// Enumeration<Object> e = attr.enumerateValues();
	// System.out.println("Values: ");
	// if (e == null) {
	// System.out.println("Enumeration is null");
	// } else {
	// while (e.hasMoreElements()) {
	// System.out.println(e.nextElement().toString());
	// }
	// }
	// String value = values.get(i).getRight();
	// if (value.equals("default")) {
	// System.out.println("Value is default!");
	// value = (String) param.getDefaultValue();
	// System.out.println("Default value is: " + value);
	// }
	// System.out.println("Trying to add value: " + value);
	// instance.setValue(attr, value);
	// } else if (param.isNumeric()) {
	// String value = values.get(i).getRight();
	// if (value.equals("default"))
	// value = (String) param.getDefaultValue();
	// double finalValue = Double.parseDouble(values.get(i).getRight());
	// instance.setValue(attr, finalValue);
	// // System.out.println("bounds: [" + attr.getLowerNumericBound() + "," +
	// // attr.getUpperNumericBound() + "]");
	// }
	// }
	// instance.setValue(scoreAttr, score);
	// instances.add(instance);
	// }
	// return instances;
	// }

	public Instances getPerformanceSamples(String benchmarkName, ComponentInstance composition) {
		String identifier = Util.getComponentNamesOfComposition(composition);
		return this.performanceInstancesByIdentifier.get(benchmarkName).get(identifier);
	}

	public Instances createInstancesForPerformanceSamples(String benchmarkName, ComponentInstance composition) {
		String identifier = Util.getComponentNamesOfComposition(composition);
		return this.performanceInstancesByIdentifier.get(benchmarkName).get(identifier);
	}

}