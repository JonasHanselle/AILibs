package ai.libs.jaicore.search.gui.plugins.rolloutboxplots;

import java.util.ArrayList;
import java.util.List;

import org.api4.java.algorithm.events.serializable.IPropertyProcessedAlgorithmEvent;

import ai.libs.jaicore.graphvisualizer.events.gui.GUIEvent;
import ai.libs.jaicore.graphvisualizer.plugin.ASimpleMVCPluginController;
import ai.libs.jaicore.graphvisualizer.plugin.controlbar.ResetEvent;
import ai.libs.jaicore.graphvisualizer.plugin.graphview.NodeClickedEvent;
import ai.libs.jaicore.graphvisualizer.plugin.timeslider.GoToTimeStepEvent;
import ai.libs.jaicore.search.algorithms.standard.bestfirst.events.RolloutEvent;
import ai.libs.jaicore.search.gui.plugins.rollouthistograms.RolloutInfo;
import ai.libs.jaicore.search.gui.plugins.rollouthistograms.RolloutInfoAlgorithmEventPropertyComputer;

public class SearchRolloutBoxplotPluginController extends ASimpleMVCPluginController<SearchRolloutBoxplotPluginModel, SearchRolloutBoxplotPluginView> {

	public SearchRolloutBoxplotPluginController(final SearchRolloutBoxplotPluginModel model, final SearchRolloutBoxplotPluginView view) {
		super(model, view);
	}

	@Override
	public void handleGUIEvent(final GUIEvent guiEvent) {
		if (guiEvent instanceof ResetEvent || guiEvent instanceof GoToTimeStepEvent) {
			this.getModel().clear();
		} else if (guiEvent instanceof NodeClickedEvent) {
			synchronized (this.getView()) {
				this.getModel().setCurrentlySelectedNode(((NodeClickedEvent) guiEvent).getSearchGraphNode());
				this.getView().clear();
				this.getView().update();
			}
		}
	}

	@Override
	public void handleAlgorithmEventInternally(final IPropertyProcessedAlgorithmEvent algorithmEvent) {
		if (algorithmEvent.correspondsToEventOfClass(RolloutEvent.class)) {

			RolloutInfo rolloutInfo = (RolloutInfo) algorithmEvent.getProperty(RolloutInfoAlgorithmEventPropertyComputer.ROLLOUT_SCORE_PROPERTY_NAME);

			String lastNode = null;
			for (String n : rolloutInfo.getPath()) {
				if (lastNode != null) {
					List<String> successors = this.getModel().getListsOfKnownSuccessors().computeIfAbsent(lastNode, k -> new ArrayList<>());
					if (!successors.contains(n)) {
						successors.add(n);
					}
					this.getModel().getParents().put(n, lastNode);
				}
				this.getModel().addEntry(n, (double) rolloutInfo.getScore());
				lastNode = n;
			}
		}
	}

}
