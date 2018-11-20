package jaicore.ml.evaluation;

import com.google.common.eventbus.EventBus;

import jaicore.basic.IObjectEvaluator;
import weka.classifiers.Classifier;
/**
 * Can perform a (cross)-validation process on a {@link Classifier}.
 *
 */
public interface ClassifierEvaluator extends IObjectEvaluator<Classifier, Double> {
	
	void setReproducibilityEventBus(EventBus e);
}
