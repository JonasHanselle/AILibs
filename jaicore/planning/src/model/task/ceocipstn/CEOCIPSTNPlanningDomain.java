package jaicore.planning.model.task.ceocipstn;

import java.io.Serializable;
import java.util.Collection;

import jaicore.planning.model.ceoc.CEOCOperation;
import jaicore.planning.model.task.ceocstn.CEOCSTNPlanningDomain;

@SuppressWarnings("serial")
public class CEOCIPSTNPlanningDomain extends CEOCSTNPlanningDomain implements Serializable {

	
	public CEOCIPSTNPlanningDomain(Collection<? extends CEOCOperation> operations, Collection<? extends OCIPMethod> methods) {
		super(operations, methods);
	}

	@SuppressWarnings("unchecked")
	public Collection<? extends OCIPMethod> getMethods() {
		return (Collection<? extends OCIPMethod>)super.getMethods();
	}
}
