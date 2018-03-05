package de.upb.crc901.mlplan.evaluablepredicates.mlplan.classifier.basic.RadiusNeighborsClassifier;

    import java.util.Arrays;
    import java.util.List;

import org.apache.tools.ant.types.Assertions.DisabledAssertion;

import de.upb.crc901.mlplan.evaluablepredicates.mlplan.DisabledOptionPredicate;
import de.upb.crc901.mlplan.evaluablepredicates.mlplan.OptionsPredicate;
    /*
        weights : str or callable
        weight function used in prediction.  Possible values:

        - 'uniform' : uniform weights.  All points in each neighborhood
          are weighted equally.
        - 'distance' : weight points by the inverse of their distance.
          in this case, closer neighbors of a query point will have a
          greater influence than neighbors which are further away.
        - [callable] : a user-defined function which accepts an
          array of distances, and returns an array of the same shape
          containing the weights.

        Uniform weights are used by default.


    */
    public class OptionsFor_RadiusNeighborsClassifier_weights extends DisabledOptionPredicate {
        
        private static List<Object> validValues = Arrays.asList(new Object[]{"distance"}); // default is uniform

        @Override
        protected List<? extends Object> getValidValues() {
            return validValues;
        }
    }
    
